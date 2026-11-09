"""Loopback-only embedding server for live tests when Ollama is unavailable.

Requires torch and transformers. Uses the published BAAI model weights.
"""

import argparse
from http.server import BaseHTTPRequestHandler, HTTPServer
import json

import torch
from transformers import AutoModel, AutoTokenizer


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=11435)
    parser.add_argument("--model", default="BAAI/bge-m3")
    args = parser.parse_args()
    torch.set_num_threads(4)
    tokenizer = AutoTokenizer.from_pretrained(args.model)
    model = AutoModel.from_pretrained(args.model).eval()

    class Handler(BaseHTTPRequestHandler):
        def do_POST(self):
            if self.path != "/api/embed":
                self.send_error(404)
                return
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if not 0 < size <= 128_000:
                    raise ValueError("Invalid request size")
                body = json.loads(self.rfile.read(size))
                inputs = body["input"]
                if not isinstance(inputs, list) or not 1 <= len(inputs) <= 16:
                    raise ValueError("Invalid batch")
                encoded = tokenizer(inputs, padding=True, return_tensors="pt")
                if encoded.input_ids.shape[1] > model.config.max_position_embeddings - 2:
                    raise ValueError("Input exceeds model limit")
                with torch.inference_mode():
                    output = model(**encoded).last_hidden_state[:, 0]
                    vectors = torch.nn.functional.normalize(output, p=2, dim=1).tolist()
                result = json.dumps({"model": args.model, "embeddings": vectors}).encode()
            except (KeyError, TypeError, ValueError):
                self.send_error(400)
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(result)))
            self.end_headers()
            self.wfile.write(result)

        def log_message(self, *_args):
            pass

    print(f"Embedding service ready on 127.0.0.1:{args.port} using {args.model}", flush=True)
    HTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
