"""CPU service implementing the subset of the embedding API used by the campus backend."""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import BoundedSemaphore

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer


MODEL = "bge-small-zh-v1.5-onnx"
TOKENIZER = Tokenizer.from_file("/models/tokenizer.json")
TOKENIZER.enable_padding(pad_id=0, pad_token="[PAD]")
OPTIONS = ort.SessionOptions()
OPTIONS.intra_op_num_threads = 2
OPTIONS.inter_op_num_threads = 1
SESSION = ort.InferenceSession("/models/model.onnx", OPTIONS, providers=["CPUExecutionProvider"])
INPUTS = {node.name for node in SESSION.get_inputs()}
REQUESTS = BoundedSemaphore(2)


def embed(texts):
    encoded = TOKENIZER.encode_batch(texts)
    if len(encoded[0].ids) > 512:
        raise ValueError("Text exceeds 512 tokens")
    arrays = {
        "input_ids": [item.ids for item in encoded],
        "attention_mask": [item.attention_mask for item in encoded],
        "token_type_ids": [item.type_ids for item in encoded],
    }
    outputs = SESSION.run(None, {name: np.array(arrays[name], dtype=np.int64) for name in INPUTS})[0]
    vectors = outputs[:, 0, :]
    norm = np.maximum(np.linalg.norm(vectors, axis=1, keepdims=True), 1e-12)
    return (vectors / norm).tolist()


class Handler(BaseHTTPRequestHandler):
    def reply(self, code, result):
        payload = json.dumps(result, allow_nan=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        self.reply(200, {"status": "ready"}) if self.path == "/healthz" else self.reply(404, {"error": "Not found"})

    def do_POST(self):
        if self.path != "/api/embed":
            self.reply(404, {"error": "Not found"})
            return
        if not REQUESTS.acquire(blocking=False):
            self.reply(503, {"error": "Embedding service busy"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 128_000:
                raise ValueError("Invalid request size")
            request = json.loads(self.rfile.read(length))
            if not isinstance(request, dict) or request.get("model") != MODEL:
                raise ValueError("Invalid embedding model")
            texts = request.get("input")
            if not isinstance(texts, list) or not 1 <= len(texts) <= 16 or any(
                not isinstance(text, str) or not text.strip() for text in texts
            ):
                raise ValueError("Invalid embedding batch")
            self.reply(200, {"model": MODEL, "embeddings": embed(texts)})
        except (ValueError, UnicodeError) as error:
            self.reply(400, {"error": str(error)})
        except RuntimeError:
            self.reply(503, {"error": "Embedding inference failed"})
        finally:
            REQUESTS.release()

    def log_message(self, *_args):
        pass


if __name__ == "__main__":
    if len(embed(["校园向导"])[0]) != 512:
        raise RuntimeError("Invalid embedding dimensions")
    print("Embedding service ready (512 dimensions)")
    ThreadingHTTPServer(("0.0.0.0", 11434), Handler).serve_forever()
