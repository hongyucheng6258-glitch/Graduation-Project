# ai-service（AI 检索服务）

梧桐校园的 Python 第二后端，只承接文档解析、切片、向量化与向量检索。业务、事务、
权限、提示词与 Function Calling 全部仍在 Java 侧；本服务不直连 MySQL，也不对前端
暴露端口，只接受 Java 后端的内部调用。

## 职责边界

| 由 Java 负责 | 由 ai-service 负责 |
| --- | --- |
| 文档上传、发布、撤回、权限校验、配额 | PDF 文本提取与切片 |
| 切片正文落库（MySQL 唯一写入方） | 调用 embedding 服务生成向量 |
| 提示词拼装、答案生成、引用格式 | 向量写入与检索（独立集合） |
| Function Calling 业务工具 | 向量清理 |

## 内部接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/healthz` | GET | 健康检查，无需令牌 |
| `/internal/rag/index` | POST | 解析 PDF、切片、向量化并写入，返回切片（id/page/text）供 Java 落库 |
| `/internal/rag/search` | POST | 检索并返回 point id，正文由 Java 从 MySQL 取 |
| `/internal/rag/collection` | DELETE | 按文档与版本清理向量 |

所有业务接口要求请求头 `X-Service-Token`，取值由 `CAMPUS_AI_SERVICE_TOKEN` 指定，
比对使用 `hmac.compare_digest`。

## 本地运行

```bash
cd web/ai-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export CAMPUS_AI_SERVICE_TOKEN=dev-token
export CAMPUS_AGENT_RAG_EMBEDDING_URL=http://127.0.0.1:11434
export CAMPUS_AGENT_RAG_QDRANT_URL=http://127.0.0.1:6333
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

未配置令牌时服务拒绝启动，避免以无鉴权状态运行。

## 测试

```bash
# 切片规则（不依赖第三方库）
python3 tests/test_chunker.py -v

# 与 Java PdfChunker 的逐段等价比对（需要 JDK）
python3 tests/compare_with_java.py

# 真实 PDF 的跨语言比对：PDFBox 与 PyMuPDF 同页输出是否一致（需要 JDK 与已构建的 jar）
python3 tests/compare_pdf.py <pdf 路径> 400
```

`compare_with_java.py` 用与 `PdfChunker` 相同的切分循环在随机语料上逐段比对。
当前 120 例、3 种段长的比对结果为 0 差异；对切片逻辑的任何改动都应重跑该脚本。

`compare_pdf.py` 用项目真实的 `PdfChunker`（PDFBox）与 Python 实现处理同一份 PDF，
逐段比对页码与文本。ASCII 样例（14 段）与中文样例（3 段）的结果均为完全一致；
PDFBox 对 CID 字体回退时会在 stdout 打印 WARN，脚本会过滤这类日志行。

## 检索一致率评估

`tests/RagConsistencyEval.java` + `tests/questions-50.txt` 用于比较两种实现的检索结果：
同一份 6 页业务文档分别建索引，对 50 个问题比较 top-5 的页码集合、top-1 页码与 top-1 切片文本。
需在容器网络内运行（本地实现写入独立集合 `campus_eval_local`，结束即删）。实测结果：

| 指标 | 结果 |
| --- | --- |
| top-1 页码一致率 / 切片文本一致率 | 100% / 100% |
| top-5 页码集合平均 Jaccard | 1.000 |
| 不一致条目 | 0 |
| 检索延迟 | 本地 P50 4ms/P95 13ms，Python P50 5ms/P95 17ms |

## 部署

叠加文件方式，不影响原有启动：

```bash
docker compose -f docker-compose.yml -f docker-compose.agent.yml \
  -f docker-compose.ai-service.yml up -d
```

省略最后一个 `-f` 即为原有部署方式。服务不映射宿主机端口，CPU/内存限制为 1 核 / 1G。

## 开关与降级

- `CAMPUS_AGENT_RAG_PROVIDER=local`（默认）：后端走原实现，本服务不参与请求。
- `CAMPUS_AGENT_RAG_PROVIDER=python`：后端调用本服务。
- `CAMPUS_AGENT_RAG_FALLBACK=true`：本服务异常时后端自动回退本地实现，问答不中断。
- 回滚：把 provider 改回 `local` 并重启 backend 即可，无需数据迁移。
