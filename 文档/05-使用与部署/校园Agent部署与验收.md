# 校园 Agent 部署与验收

学生端从「AI 学习 → 校园向导」进入。向导可以查实时校园数据和已发布资料，生成活动报名确认卡、闲置发布草稿及互换预约确认卡。失物招领、学习搭子与互助问答的结果卡片可跳转到原帖或发布者主页。私人 PDF 在「PDF 问答」上传，索引完成后可提问和打开页码引用。管理端的「AI 配置 → 校园向导工作台」提供资料发布、撤回、提示词版本和运行记录。

## 启动服务

完整 Compose 项目名为 `ai-campus-docker`。构建会下载并校验 BGE 向量模型，将模型放进独立的 ONNX Runtime CPU 镜像。

```powershell
$stack = @('-f', 'docker-compose.yml', '-f', 'docker-compose.agent.yml')
docker compose @stack up -d --build --wait
docker compose @stack ps -a
```

MySQL 就绪后，由 `agent-migrate` 自动执行以下脚本。迁移退出码为 0 后，后端才会启动。已有数据库也会执行这组增量脚本。

1. `web/backend/src/main/resources/db/2026-11-09-agent-knowledge.sql`
2. `web/backend/src/main/resources/db/2026-11-09-agent-prompt.sql`
3. `web/backend/src/main/resources/db/2026-11-09-agent-action.sql`
4. `web/backend/src/main/resources/db/2026-11-09-agent-idle-draft.sql`
5. `web/backend/src/main/resources/db/2026-11-09-agent-idle-appointment.sql`

本机运行后端时，分别配置 `CAMPUS_AGENT_ENABLED=true` 与 `CAMPUS_AGENT_RAG_ENABLED=true`。可通过 `CAMPUS_AGENT_RAG_EMBEDDING_URL`、`CAMPUS_AGENT_RAG_EMBEDDING_MODEL` 和 `CAMPUS_AGENT_RAG_QDRANT_URL` 指定服务。替换 Embedding 模型时使用新的 `CAMPUS_AGENT_RAG_COLLECTION`，然后在学生 PDF 问答或管理端公共知识库点击「重建索引」，避免复用不同模型生成的向量。

Docker 使用 BAAI bge-small-zh-v1.5 的量化 ONNX 版本，输出 512 维向量。Compose 已配置 400 字符的分段和查询长度。宿主机开发时可以单独使用 `tests/agent/serve_embeddings.py`。

当前本机部署从原 MySQL 复制了业务数据，并使用独立的数据卷。导入时先启动目标 MySQL，确认目标没有已有业务数据，再导入源库的备份。Redis 会话从新实例开始。切换到不同的 Embedding 版本后，已索引文档需要重建索引。

模型凭据由本机环境和 AI 配置表读取。管理端数据库中的非空配置优先于环境变量。更换凭据时更新实际生效的值。把 `CAMPUS_AGENT_ENABLED` 设为 false 后，学生端恢复使用原有校园向导。关闭 `CAMPUS_AGENT_RAG_ENABLED` 时，原有 PDF 问答仍可使用。

校园向导会将授权范围内的必要资料片段交给聊天模型。Embedding 在本地执行与聊天模型是否部署在本地是两件事。上传私人资料前按学校的数据使用要求选择聊天模型部署方式。

## 分阶段验收

开发机的 HTTP 验收需要运行真实后端、MySQL、Redis、聊天模型。RAG 阶段还需要 Embedding 服务和 Qdrant。验收脚本使用临时账号、活动和 PDF，结束后清理业务记录。

| 阶段 | 通过标准 |
| --- | --- |
| 1. 模型协议 | 启用 `CAMPUS_AGENT_LIVE=1`，运行 `AgentModelLiveTest`。模型发出工具请求，接收 Java 临时生成的随机值，并在最终答复中复述。 |
| 2. 只读工具 | `tools` 阶段验证匿名拒绝、两个账号的报名隔离、真实活动查询和详情。 |
| 3. PDF RAG | `rag` 阶段验证超过旧版截断位置的答案、第 2 页引用、PDF 文件读取、跨账号拒绝、证据不足和撤回。 |
| 4. 向导集成 | `combined` 阶段验证公共资料和实时查询混合回答、上下文追问读取更新后的数据、会话隔离、提示词发布和回退。 |
| 5. 报名确认 | `actions` 阶段验证确认前无报名、过期和下架拒绝、账号隔离、并发确认幂等。 |
| 6. 浏览器 | `browser` 阶段从页面完成确认、PDF 上传和引用打开，刷新检查卡片和引用，并检查另一账号及管理端运行记录。 |

从项目根目录运行 API 或浏览器验收。Python 需要 `pymysql`，浏览器阶段还需要 Playwright 和 Chromium。脚本默认走本机开发端口。验证独立 Docker 环境时，通过 `--base`、`CAMPUS_STUDENT_URL` 和 `CAMPUS_ADMIN_URL` 指定该环境的回环地址，并让脚本连接同一环境的 MySQL 测试库。

```powershell
python -X utf8 tests/agent/verify_live.py --stage tools --base http://127.0.0.1:8080
python -X utf8 tests/agent/verify_live.py --stage rag --base http://127.0.0.1:8080
python -X utf8 tests/agent/verify_live.py --stage combined --base http://127.0.0.1:8080
python -X utf8 tests/agent/verify_live.py --stage actions --base http://127.0.0.1:8080
python -X utf8 tests/agent/verify_live.py --stage browser --base http://127.0.0.1:8080
```

闲置和校园服务联动的四个脚本都可以加 `--browser`，从学生界面再执行一次验收。

```powershell
python -X utf8 tests/agent/verify_idle_guide.py
python -X utf8 tests/agent/verify_idle_publish.py
python -X utf8 tests/agent/verify_idle_appointment.py
python -X utf8 tests/agent/verify_campus_resources.py
```

脚本默认只允许本机后端。不要在生产数据库运行验收脚本或演示数据重置脚本。
