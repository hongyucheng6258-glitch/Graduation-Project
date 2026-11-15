#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

project=${CAMPUS_DEPLOY_PROJECT:-ai-campus-docker}
if [[ ! "$project" =~ ^[a-z0-9][a-z0-9_-]*$ ]]; then
    echo "无效的 Compose 项目名 $project" >&2
    exit 1
fi
if (( $# > 1 )); then
    echo "用法 bash scripts/deploy-backend.sh [已构建的后端镜像]" >&2
    exit 1
fi

compose() { docker compose -p "$project" -f docker-compose.yml -f docker-compose.agent.yml "$@"; }
backend=$(compose ps -q backend)
if [[ -z "$backend" || -z "$(compose ps -q student)" || -z "$(compose ps -q admin)" ]]; then
    echo "请先启动 $project 的后端、学生端和管理端。" >&2
    exit 1
fi

lock="$(git rev-parse --path-format=absolute --git-common-dir)/deploy-$project.lock"
if ! mkdir "$lock"; then
    echo "该项目已有发布任务，请检查 $lock。" >&2
    exit 1
fi
trap 'rm -f "$lock/candidate.yml"; rmdir "$lock"' EXIT

compose run --rm --no-deps -e CAMPUS_MIGRATION_CHECK_ONLY=1 agent-migrate

old_image=$(docker inspect -f '{{.Image}}' "$backend")
service_image=$(docker inspect -f '{{.Config.Image}}' "$backend")
if [[ "$service_image" != "$project-backend" && "$service_image" != "$project-backend:latest" ]]; then
    echo "后端容器使用自定义镜像 $service_image，请先核对部署配置。" >&2
    exit 1
fi
version=$(git rev-parse --short=12 HEAD)
candidate=${1:-"$project-backend:$version"}

if [[ $# = 0 ]]; then
    if [[ -n "$(git status --porcelain)" ]]; then
        echo "请先提交工作区改动，再从明确的 Git 版本构建。" >&2
        exit 1
    fi
    printf 'services:\n  backend:\n    image: %s\n' "$candidate" > "$lock/candidate.yml"
    BUILD_VERSION="$version" docker compose -p "$project" -f docker-compose.yml -f docker-compose.agent.yml \
        -f "$lock/candidate.yml" build backend
fi
candidate_id=$(docker image inspect -f '{{.Id}}' "$candidate")

address() {
    local endpoint
    endpoint=$(compose port "$1" "$2")
    printf 'http://127.0.0.1:%s' "${endpoint##*:}"
}
backend_url=$(address backend 8080)
student_url=$(address student 80)
admin_url=$(address admin 80)

get() { curl --fail --silent --show-error --max-time 5 "$1"; }
smoke() {
    get "$backend_url/readyz" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"READY"' &&
    get "$student_url/" | grep -Ei '<html' >/dev/null &&
    get "$admin_url/admin/" | grep -Ei '<html' >/dev/null &&
    get "$student_url/api/activity/list?pageSize=1" | grep -Eq '"code"[[:space:]]*:[[:space:]]*200' &&
    get "$admin_url/api/admin/auth/me" | grep -Eq '"code"[[:space:]]*:[[:space:]]*401'
}
rollout() { compose up -d --no-deps --no-build --wait --wait-timeout "${CAMPUS_DEPLOY_WAIT_SECONDS:-90}" backend; }
reload() { compose exec -T student nginx -s reload && compose exec -T admin nginx -s reload; }

docker tag "$old_image" "$project-backend:previous"
docker tag "$candidate_id" "$service_image"
echo "发布 $candidate 到 $project"
running_image() {
    local container
    container=$(compose ps -q backend)
    docker inspect -f '{{.Image}}' "$container"
}
if rollout && [[ "$(running_image)" = "$candidate_id" ]] && reload && smoke; then
    echo "发布检查通过 $candidate"
else
    echo "发布检查失败，恢复到 $old_image" >&2
    docker tag "$old_image" "$service_image"
    if rollout && reload && smoke; then
        echo "已恢复上一版，检查通过" >&2
    else
        echo "回退后仍未通过检查，请查看容器日志。" >&2
    fi
    exit 1
fi
