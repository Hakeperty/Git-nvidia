import json
import httpx
from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse, JSONResponse
import uvicorn

app = FastAPI()

NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
NVIDIA_API_KEY  = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
NVIDIA_MODEL    = "qwen/qwen3.5-122b-a10b"


# ── Tool format conversion ─────────────────────────────────────────────────

def anthropic_tools_to_openai(tools):
    if not tools:
        return None
    openai_tools = []
    for tool in tools:
        openai_tools.append({
            "type": "function",
            "function": {
                "name":        tool["name"],
                "description": tool.get("description", ""),
                "parameters":  tool.get("input_schema", {})
            }
        })
    return openai_tools


def anthropic_to_openai_messages(messages, system=None):
    openai_messages = []
    if system:
        openai_messages.append({"role": "system", "content": system})

    for msg in messages:
        role    = msg["role"]
        content = msg.get("content", "")

        if isinstance(content, list):
            tool_results = [c for c in content if c.get("type") == "tool_result"]
            tool_uses    = [c for c in content if c.get("type") == "tool_use"]
            text_items   = [c for c in content if c.get("type") == "text"]

            if tool_results:
                for tr in tool_results:
                    result_content = tr.get("content", "")
                    if isinstance(result_content, list):
                        result_content = " ".join(
                            c.get("text", "") for c in result_content
                            if c.get("type") == "text"
                        )
                    openai_messages.append({
                        "role":         "tool",
                        "tool_call_id": tr.get("tool_use_id", "call_0"),
                        "content":      str(result_content)
                    })
                if text_items:
                    openai_messages.append({
                        "role":    "user",
                        "content": " ".join(t.get("text", "") for t in text_items)
                    })

            elif tool_uses:
                tool_calls = []
                for tu in tool_uses:
                    tool_calls.append({
                        "id":   tu.get("id", "call_0"),
                        "type": "function",
                        "function": {
                            "name":      tu["name"],
                            "arguments": json.dumps(tu.get("input", {}))
                        }
                    })
                openai_messages.append({
                    "role":       "assistant",
                    "content":    " ".join(t.get("text", "") for t in text_items) or None,
                    "tool_calls": tool_calls
                })

            else:
                parts = []
                for item in content:
                    if item["type"] == "text":
                        parts.append({"type": "text", "text": item["text"]})
                    elif item["type"] == "image":
                        src = item.get("source", {})
                        if src.get("type") == "base64":
                            parts.append({
                                "type": "image_url",
                                "image_url": {
                                    "url": f"data:{src['media_type']};base64,{src['data']}"
                                }
                            })
                openai_messages.append({"role": role, "content": parts})
        else:
            openai_messages.append({"role": role, "content": content})

    return openai_messages


def openai_tool_calls_to_anthropic(tool_calls):
    blocks = []
    for tc in tool_calls:
        fn = tc.get("function", {})
        try:
            arguments = json.loads(fn.get("arguments", "{}"))
        except json.JSONDecodeError:
            arguments = {}
        blocks.append({
            "type":  "tool_use",
            "id":    tc.get("id", "call_0"),
            "name":  fn.get("name", ""),
            "input": arguments
        })
    return blocks


# ── Streaming ──────────────────────────────────────────────────────────────

async def do_stream(openai_req, headers, input_tokens):
    async with httpx.AsyncClient(timeout=120.0) as client:
        async with client.stream(
            "POST",
            f"{NVIDIA_BASE_URL}/chat/completions",
            json=openai_req,
            headers=headers,
        ) as response:
            if response.status_code != 200:
                error_body = await response.aread()
                err = json.dumps({"error": {"message": error_body.decode()}})
                yield f"event: error\ndata: {err}\n\n"
                return

            yield (
                'event: message_start\n'
                'data: {"type":"message_start","message":{"id":"msg_proxy",'
                '"type":"message","role":"assistant","content":[],'
                '"model":"claude-sonnet-4-6","stop_reason":null,'
                '"stop_sequence":null,'
                f'"usage":{{"input_tokens":{input_tokens},"output_tokens":0}}}}}}\n\n'
            )

            output_tokens  = 0
            finish_reason  = None
            text_started   = False
            tool_calls_acc = {}

            async for line in response.aiter_lines():
                if not line.strip():
                    continue
                if line == "data: [DONE]":
                    break
                if not line.startswith("data: "):
                    continue
                try:
                    data = json.loads(line[6:])
                except json.JSONDecodeError:
                    continue

                choice        = data.get("choices", [{}])[0]
                delta         = choice.get("delta", {})
                finish_reason = choice.get("finish_reason") or finish_reason

                reasoning        = delta.get("reasoning_content") or ""
                text             = delta.get("content") or ""
                tool_calls_delta = delta.get("tool_calls", [])

                if reasoning:
                    if not text_started:
                        yield (
                            'event: content_block_start\n'
                            'data: {"type":"content_block_start","index":0,'
                            '"content_block":{"type":"thinking","thinking":""}}\n\n'
                        )
                        text_started = True
                    payload = json.dumps({
                        "type": "content_block_delta", "index": 0,
                        "delta": {"type": "thinking_delta", "thinking": reasoning}
                    })
                    yield f"event: content_block_delta\ndata: {payload}\n\n"

                if text:
                    if not text_started:
                        yield (
                            'event: content_block_start\n'
                            'data: {"type":"content_block_start","index":0,'
                            '"content_block":{"type":"text","text":""}}\n\n'
                        )
                        text_started = True
                    output_tokens += len(text.split())
                    payload = json.dumps({
                        "type": "content_block_delta", "index": 0,
                        "delta": {"type": "text_delta", "text": text}
                    })
                    yield f"event: content_block_delta\ndata: {payload}\n\n"

                for tc_delta in tool_calls_delta:
                    idx = tc_delta.get("index", 0)
                    fn  = tc_delta.get("function", {})
                    if idx not in tool_calls_acc:
                        tool_calls_acc[idx] = {
                            "id":        tc_delta.get("id", f"call_{idx}"),
                            "name":      fn.get("name", ""),
                            "arguments": fn.get("arguments", "")
                        }
                    else:
                        tool_calls_acc[idx]["name"]      += fn.get("name", "")
                        tool_calls_acc[idx]["arguments"] += fn.get("arguments", "")

            if text_started:
                yield 'event: content_block_stop\ndata: {"type":"content_block_stop","index":0}\n\n'

            if tool_calls_acc:
                for i, (idx, tc) in enumerate(sorted(tool_calls_acc.items())):
                    block_index = i + (1 if text_started else 0)
                    try:
                        arguments = json.loads(tc["arguments"])
                    except json.JSONDecodeError:
                        arguments = {}
                    block_start = json.dumps({
                        "type": "content_block_start",
                        "index": block_index,
                        "content_block": {
                            "type":  "tool_use",
                            "id":    tc["id"],
                            "name":  tc["name"],
                            "input": {}
                        }
                    })
                    yield f"event: content_block_start\ndata: {block_start}\n\n"
                    input_delta = json.dumps({
                        "type": "content_block_delta",
                        "index": block_index,
                        "delta": {
                            "type":         "input_json_delta",
                            "partial_json": json.dumps(arguments)
                        }
                    })
                    yield f"event: content_block_delta\ndata: {input_delta}\n\n"
                    yield f'event: content_block_stop\ndata: {{"type":"content_block_stop","index":{block_index}}}\n\n'

            stop_reason = "tool_use" if tool_calls_acc else "end_turn"
            payload = json.dumps({
                "type": "message_delta",
                "delta": {"stop_reason": stop_reason, "stop_sequence": None},
                "usage": {"output_tokens": output_tokens}
            })
            yield f"event: message_delta\ndata: {payload}\n\n"
            yield 'event: message_stop\ndata: {"type":"message_stop"}\n\n'


# ── Main handler ───────────────────────────────────────────────────────────

async def handle_messages(request: Request):
    body = await request.json()

    messages   = body.get("messages", [])
    system     = body.get("system", None)
    tools      = body.get("tools", None)
    max_tokens = body.get("max_tokens", 4096)
    stream     = body.get("stream", False)

    if isinstance(system, list):
        system = " ".join(
            b.get("text", "") for b in system if b.get("type") == "text"
        )

    openai_tools = anthropic_tools_to_openai(tools)

    openai_req = {
        "model":             NVIDIA_MODEL,
        "messages":          anthropic_to_openai_messages(messages, system),
        "max_tokens":        max_tokens,
        "temperature":       0.60,
        "top_p":             0.95,
        "frequency_penalty": 0,
        "presence_penalty":  0,
        "stream":            stream,
        "chat_template_kwargs": {"enable_thinking": True},
    }
    if openai_tools:
        openai_req["tools"]       = openai_tools
        openai_req["tool_choice"] = "auto"

    req_headers = {
        "Authorization": f"Bearer {NVIDIA_API_KEY}",
        "Content-Type":  "application/json",
    }

    proxy_headers = {
        "x-request-id":                          "req_proxy",
        "anthropic-ratelimit-requests-limit":     "50",
        "anthropic-ratelimit-requests-remaining": "49",
        "anthropic-ratelimit-tokens-limit":       "200000",
        "anthropic-ratelimit-tokens-remaining":   "199999",
    }

    input_tokens = sum(
        len(str(m.get("content", ""))) // 4
        for m in openai_req["messages"]
    )

    if stream:
        return StreamingResponse(
            do_stream(openai_req, req_headers, input_tokens),
            media_type="text/event-stream",
            headers=proxy_headers,
        )
    else:
        async with httpx.AsyncClient(timeout=120.0) as client:
            response = await client.post(
                f"{NVIDIA_BASE_URL}/chat/completions",
                json=openai_req,
                headers=req_headers,
            )
            if response.status_code != 200:
                return JSONResponse(
                    {"error": {"message": response.text}},
                    status_code=response.status_code
                )
            data    = response.json()
            choices = data.get("choices", [])
            message = choices[0].get("message", {}) if choices else {}
            usage   = data.get("usage", {})

            content_blocks = []
            text = message.get("content")
            if text:
                content_blocks.append({"type": "text", "text": text})

            tool_calls = message.get("tool_calls", [])
            if tool_calls:
                content_blocks.extend(openai_tool_calls_to_anthropic(tool_calls))

            stop_reason = "tool_use" if tool_calls else "end_turn"

            return JSONResponse({
                "id":            "msg_proxy",
                "type":          "message",
                "role":          "assistant",
                "content":       content_blocks,
                "model":         "claude-sonnet-4-6",
                "stop_reason":   stop_reason,
                "stop_sequence": None,
                "usage": {
                    "input_tokens":  usage.get("prompt_tokens", 0),
                    "output_tokens": usage.get("completion_tokens", 0),
                }
            }, headers=proxy_headers)


@app.post("/v1/messages")
async def messages(request: Request):
    return await handle_messages(request)


@app.api_route("/", methods=["GET", "HEAD"])
async def root():
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8080)
