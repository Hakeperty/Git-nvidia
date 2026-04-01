#!/bin/bash

# ── Config ────────────────────────────────────────────────────────────────
NVAPI_KEY="${NVIDIA_API_KEY:-}"          # ← set NVIDIA_API_KEY environment variable
PROXY_PORT=8080

# ── Validate ──────────────────────────────────────────────────────────────
if [ -z "$NVAPI_KEY" ]; then
    echo "Error: Set your NVAPI_KEY in this script first."
    exit 1
fi

# ── Environment ───────────────────────────────────────────────────────────
export ANTHROPIC_BASE_URL="http://localhost:$PROXY_PORT"
export ANTHROPIC_API_KEY="$NVAPI_KEY"
unset ANTHROPIC_AUTH_TOKEN

# ── Proxy ─────────────────────────────────────────────────────────────────
PROXY_PID=""
if ! curl -s "http://localhost:$PROXY_PORT/" > /dev/null 2>&1; then
    echo "Starting NVIDIA API proxy..."
    NVAPI_KEY="$NVAPI_KEY" python nvidia_proxy.py > /tmp/proxy.log 2>&1 &
    PROXY_PID=$!
    sleep 5

    if ! curl -s "http://localhost:$PROXY_PORT/" > /dev/null 2>&1; then
        echo "Failed to start proxy."
        kill $PROXY_PID 2>/dev/null
        exit 1
    fi
    echo "Proxy started (PID: $PROXY_PID)"
else
    echo "Proxy already running."
fi

# ── Launch Claude ──────────────────────────────────────────────────────────
claude "$@"

# ── Cleanup ────────────────────────────────────────────────────────────────
if [ -n "$PROXY_PID" ]; then
    echo "Stopping proxy..."
    kill $PROXY_PID 2>/dev/null
fi
