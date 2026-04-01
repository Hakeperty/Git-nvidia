# NVIDIA API Proxy for Claude Code

## Quick Start

1. Install dependencies:
   ```bash
   pip install -r nvidia_proxy_requirements.txt
   ```

2. Run:
   ```bash
   ./claude.sh
   ```

That's it! The script handles everything automatically.

## What It Does

- Starts a FastAPI proxy on port 8080
- Translates Anthropic API requests to OpenAI format
- Forwards to NVIDIA's API
- Translates responses back to Anthropic format
- Runs Claude Code with your NVIDIA API key

## Available NVIDIA Models

Use any model from https://build.nvidia.com:
- `anthropic/claude-3-5-sonnet`
- `meta/llama-3.3-70b-instruct`
- `mistralai/mistral-large`
- `google/gemma-2-27b-it`
- And more...