#!/usr/bin/env python3
"""Nexmux Runtime E2E 上游桩（OpenAI 兼容）。

端口 18099：
  GET  /v1/models           → 200 {"object":"list","data":[...]}
  POST /v1/chat/completions → 非流式返回 chat.completion（echo 请求 model，固定 usage 12/6）；
                              stream=true 返回 SSE 两个内容 chunk + usage chunk + [DONE]
响应 content / 合成 model 字段携带上游模型 ID，便于断言选路命中了哪个候选。
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/v1/models":
            self._json(200, {"object": "list", "data": [{"id": "stub-a"}, {"id": "stub-b"}]})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self._json(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(length) or b"{}")
        model = body.get("model", "unknown")
        if body.get("stream"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            for chunk in (
                    f'data: {{"choices":[{{"delta":{{"content":"{model}:"}}}}]}}\n\n',
                    'data: {"choices":[{"delta":{"content":"ok"}}]}\n\n',
                    'data: {"choices":[],"usage":{"prompt_tokens":12,"completion_tokens":6}}\n\n',
                    "data: [DONE]\n\n"):
                self.wfile.write(chunk.encode())
                self.wfile.flush()
        else:
            self._json(200, {
                "id": "chatcmpl-e2e", "object": "chat.completion", "model": model,
                "choices": [{"index": 0,
                             "message": {"role": "assistant", "content": f"{model}:ok"},
                             "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 12, "completion_tokens": 6}})

    def _json(self, code, obj):
        data = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18099
    print(f"upstream stub on :{port}")
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
