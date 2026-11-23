"""服务内统一错误类型：携带 HTTP 状态与可直接展示给调用方的中文信息。"""


class RagError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


def bad_request(message: str) -> RagError:
    return RagError(400, message)


def unavailable(message: str) -> RagError:
    return RagError(503, message)
