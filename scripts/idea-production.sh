#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077

project=${CAMPUS_IDEA_PROJECT:-ai-campus-docker}
if [[ ! "$project" =~ ^[a-z0-9][a-z0-9_-]*$ ]]; then
    echo "无效的 Compose 项目名 $project" >&2
    exit 1
fi
backend="$project-backend-1"
student="$project-student-1"
admin="$project-admin-1"
bridge="$project-idea-bridge"
settings=web/backend/.env.idea-production
lock="$(git rev-parse --path-format=absolute --git-common-dir)/deploy-$project.lock"

# 回环端口只在这里维护一份：bridge 的端口映射与 IDEA 后端配置都引用它。
mysql_port=23306
redis_port=26379
qdrant_port=26333
embedding_port=21434

fail() { echo "$*" >&2; exit 1; }
running() { [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = true ]]; }
exists() { docker inspect "$1" >/dev/null 2>&1; }
require_container() {
    exists "$1" ||
        fail "找不到容器 $1。请先用 docker compose -f docker-compose.yml -f docker-compose.agent.yml 启动（项目名 ${CAMPUS_IDEA_PROJECT:-ai-campus-docker}）。"
}
host_port() {
    docker inspect -f "{{(index (index .HostConfig.PortBindings \"$2\") 0).HostPort}}" "$1"
}
reload_frontends() {
    running "$student" || fail '学生端容器未运行，无法 reload Nginx。'
    running "$admin" || fail '管理端容器未运行，无法 reload Nginx。'
    docker exec "$student" nginx -s reload
    docker exec "$admin" nginx -s reload
}
wait_ready() {
    local port=$1
    local body attempt
    for ((attempt=0; attempt<40; attempt++)); do
        # 先取回完整响应再匹配：curl | grep -q 会在 pipefail 下触发 SIGPIPE 误判。
        body=$(curl -fsS --max-time 2 "http://127.0.0.1:$port/readyz" 2>/dev/null || true)
        if [[ "$body" =~ \"status\"[[:space:]]*:[[:space:]]*\"READY\" ]]; then
            return 0
        fi
        sleep 1
    done
    return 1
}

wait_bridge() {
    local attempt
    for ((attempt=0; attempt<10; attempt++)); do
        if docker exec "$bridge" nginx -t -c /tmp/relay.conf >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    return 1
}

if [[ "$#" != 1 ]] || [[ "$1" != idea && "$1" != verify && "$1" != docker ]]; then
    fail '用法 bash scripts/idea-production.sh {idea|verify|docker}'
fi
mkdir "$lock" || fail "已有发布或切换任务，请检查 $lock。"
trap 'rmdir "$lock"' EXIT

require_container "$backend"
require_container "$student"
require_container "$admin"
backend_port=$(host_port "$backend" 8080/tcp)
student_port=$(host_port "$student" 80/tcp)
admin_port=$(host_port "$admin" 80/tcp)

property() {
    local value=${2//\\/\\\\}
    # Java Properties 会把行首的 # / ! 当成注释，密码或密钥以它们开头时需转义。
    case "$value" in
        [\#\!]*) value="\\$value" ;;
    esac
    # 转义分隔符后的前导空格，保证 Java Properties 读到原值。
    printf '%s=%s\n' "$1" "${value/# /\\ }"
}

write_settings() {
    local key value
    while IFS='=' read -r key value; do
        case "$key" in
            MYSQL_USER|MYSQL_PASSWORD|MYSQL_DB|REDIS_PASSWORD|SIGNIN_SECRET|TRUSTED_ORIGINS|AI_API_KEY|AI_BASE_URL|AI_MODEL|CAMPUS_ADMIN_ASSISTANT_ENABLED|MINIO_*)
                property "$key" "$value" ;;
            PLATFORM_CAPTCHA_ENABLED) property platform.captcha.enabled "$value" ;;
            PLATFORM_SMS_CONSOLE_ENABLED) property platform.sms.console-enabled "$value" ;;
            CAMPUS_AGENT_ENABLED) property campus.agent.enabled "$value" ;;
            CAMPUS_AGENT_RAG_ENABLED) property campus.agent.rag.enabled "$value" ;;
            CAMPUS_AGENT_RAG_EMBEDDING_MODEL) property campus.agent.rag.embedding-model "$value" ;;
            CAMPUS_AGENT_RAG_COLLECTION) property campus.agent.rag.collection "$value" ;;
            CAMPUS_AGENT_RAG_QUERY_CHARS) property campus.agent.rag.query-chars "$value" ;;
            CAMPUS_AGENT_RAG_CHUNK_CHARS) property campus.agent.rag.chunk-chars "$value" ;;
        esac
    done < <(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$backend")
    cat <<PROPERTIES
server.address=127.0.0.1
server.port=$backend_port
MYSQL_HOST=127.0.0.1
MYSQL_PORT=$mysql_port
REDIS_HOST=127.0.0.1
REDIS_PORT=$redis_port
campus.agent.rag.qdrant-url=http://127.0.0.1:$qdrant_port
campus.agent.rag.embedding-url=http://127.0.0.1:$embedding_port
PROPERTIES
}

remove_bridge() {
    if docker inspect "$bridge" >/dev/null 2>&1; then
        docker stop "$bridge" >/dev/null
        docker container rm "$bridge" >/dev/null
    fi
}

case "$1" in
    idea)
        [[ ! -e "$settings" ]] || fail 'IDEA 切换已开始。请运行 verify 完成切换，或运行 docker 恢复容器。'
        ! docker inspect "$bridge" >/dev/null 2>&1 || fail '已存在 IDEA 转发容器，请检查当前模式。'
        running "$student" && running "$admin" || fail '学生端和管理端容器必须已启动。'
        active_rooms=$(docker exec -i "$project-mysql-1" sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -N' <<'SQL'
SELECT COUNT(*) FROM draw_game_room WHERE status IN ('PLAYING', 'WAITING');
SQL
)
        [[ "$active_rooms" = 0 ]] || fail "还有 $active_rooms 个画板房间等待或进行中，请等房间关闭后再切换。"

        prepared=$(mktemp "${settings}.XXXXXX")
        if ! write_settings > "$prepared" || ! grep -q '^MYSQL_PASSWORD=.' "$prepared" || ! grep -q '^REDIS_PASSWORD=.' "$prepared"; then
            unlink "$prepared"
            fail '读取容器配置失败。'
        fi
        was_running=false
        if running "$backend"; then
            was_running=true
            if ! docker stop "$backend" >/dev/null; then
                unlink "$prepared"
                fail '停止 Docker 后端失败。'
            fi
        fi
        image=$(docker inspect -f '{{.Image}}' "$student")
        if docker run -d --name "$bridge" --network "${project}_default" --network-alias backend \
            -p "127.0.0.1:$mysql_port:3306" -p "127.0.0.1:$redis_port:6379" \
            -p "127.0.0.1:$qdrant_port:6333" -p "127.0.0.1:$embedding_port:11434" \
            -e "IDEA_BACKEND_PORT=$backend_port" \
            --mount "type=bind,source=$PWD/docker/nginx/idea-bridge.conf,target=/relay.conf,readonly" \
            --entrypoint /bin/sh "$image" -ec 'envsubst < /relay.conf > /tmp/relay.conf; exec nginx -c /tmp/relay.conf -g "daemon off;"' >/dev/null &&
            wait_bridge; then
            mv "$prepared" "$settings"
        else
            unlink "$prepared"
            remove_bridge
            if [[ "$was_running" = true ]]; then
                docker start "$backend" >/dev/null
                if wait_ready "$backend_port"; then
                    reload_frontends
                else
                    fail 'Docker 后端恢复后未就绪，请检查容器日志（前端未 reload）。'
                fi
            fi
            fail 'IDEA 转发启动失败，请检查回环端口和 Docker 网络。'
        fi
        echo '请在 IDEA 中运行 Campus Backend Production Docker，启动后执行 bash scripts/idea-production.sh verify。'
        ;;
    verify)
        running "$bridge" && ! running "$backend" || fail '请先切换到 IDEA 模式。'
        running "$student" || fail '学生端容器未运行，请先启动 Compose 前端容器。'
        running "$admin" || fail '管理端容器未运行，请先启动 Compose 前端容器。'
        wait_ready "$backend_port" || fail 'IDEA 后端尚未就绪。请检查运行日志。'
        frontend_probe=$(docker exec "$student" wget -q -O - http://backend:8080/readyz 2>/dev/null || true)
        [[ "$frontend_probe" =~ \"status\"[[:space:]]*:[[:space:]]*\"READY\" ]] ||
            fail 'Docker 前端访问不到 IDEA 后端。'
        reload_frontends
        student_body=$(curl -sS --max-time 5 "http://127.0.0.1:$student_port/api/activity/list?pageSize=1" || true)
        [[ "$student_body" =~ \"code\"[[:space:]]*:[[:space:]]*\"200\" ]] ||
            fail '学生端公开接口未按预期返回 200。'
        admin_body=$(curl -sS --max-time 5 "http://127.0.0.1:$admin_port/api/admin/auth/me" || true)
        [[ "$admin_body" =~ \"code\"[[:space:]]*:[[:space:]]*\"401\" ]] ||
            fail '管理端未按预期返回未登录状态（code 401）。'
        echo '学生端和管理端已通过 IDEA 后端联调检查。'
        ;;
    docker)
        if ! running "$backend" && (exec 3<>"/dev/tcp/127.0.0.1/$backend_port") 2>/dev/null; then
            fail '请先在 IDEA 中停止后端，再恢复 Docker。'
        fi
        if [[ -f "$settings" ]]; then unlink "$settings"; fi
        remove_bridge
        if ! running "$backend"; then docker start "$backend" >/dev/null; fi
        wait_ready "$backend_port" || fail 'Docker 后端尚未就绪，请检查容器日志。'
        reload_frontends
        echo 'Docker 后端已恢复。'
        ;;
esac
