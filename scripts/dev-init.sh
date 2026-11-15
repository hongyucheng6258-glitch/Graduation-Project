#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077

dev_env=.env.dev
idea_env=web/backend/.env.idea

# Adopt credentials from the existing development containers on first use.
container_value() {
    docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "campus-idea-$1-1" 2>/dev/null |
        sed -n "s/^$2=//p"
}

idea_value() {
    if [[ -f "$idea_env" ]]; then
        sed -n "s/^$1=//p" "$idea_env" | tr -d '\r'
    fi
}

if [[ ! -f "$dev_env" ]]; then
    mysql_password=$(container_value mysql MYSQL_ROOT_PASSWORD || true)
    redis_password=$(container_value redis REDIS_PASSWORD || true)
    database=$(container_value mysql MYSQL_DATABASE || true)
    mysql_password=${mysql_password:-$(idea_value MYSQL_PASSWORD)}
    redis_password=${redis_password:-$(idea_value REDIS_PASSWORD)}
    database=${database:-$(idea_value MYSQL_DB)}
    if [[ -z "$mysql_password" ]] && docker volume inspect campus-idea_mysql_data >/dev/null 2>&1; then
        echo "检测到已有 MySQL 数据卷。请在 $idea_env 中填写现有的 MYSQL_PASSWORD 后重试。" >&2
        exit 1
    fi
    mysql_password=${mysql_password:-$(openssl rand -hex 24)}
    redis_password=${redis_password:-$(openssl rand -hex 24)}
    database=${database:-ai_campus_platform}
    # Compose single-quoted dotenv values preserve literal dollar signs.
    quote_env() { printf "'%s'" "${1//\'/\\\'}"; }
    (
        set -o noclobber
        {
            printf 'MYSQL_ROOT_PASSWORD=%s\n' "$(quote_env "$mysql_password")"
            printf 'MYSQL_DB=%s\n' "$(quote_env "$database")"
            printf 'REDIS_PASSWORD=%s\n' "$(quote_env "$redis_password")"
            printf 'JWT_SECRET=%s\n' "$(openssl rand -hex 32)"
            printf 'SIGNIN_SECRET=%s\n' "$(openssl rand -hex 24)"
        } > "$dev_env"
    )
fi

# A developer's shell can contain credentials for another Compose project.
compose() {
    env -u MYSQL_ROOT_PASSWORD -u MYSQL_DB -u REDIS_PASSWORD -u JWT_SECRET -u SIGNIN_SECRET \
        docker compose --env-file "$dev_env" -p campus-idea "$@"
}

compose -f docker-compose.yml -f docker-compose.idea.yml up -d --wait mysql redis
compose -f docker-compose.yml -f docker-compose.agent.yml -f docker-compose.idea.yml \
    run --rm --no-deps agent-migrate

if [[ ! -f "$idea_env" ]]; then
    # Java properties treats backslashes as escapes.
    property() { printf '%s=%s\n' "$1" "${2//\\/\\\\}"; }
    (
        set -o noclobber
        {
            property MYSQL_PASSWORD "$(container_value mysql MYSQL_ROOT_PASSWORD)"
            property MYSQL_DB "$(container_value mysql MYSQL_DATABASE)"
            property REDIS_PASSWORD "$(container_value redis REDIS_PASSWORD)"
            property JWT_SECRET "$(openssl rand -hex 32)"
            property SIGNIN_SECRET "$(openssl rand -hex 24)"
        } > "$idea_env"
    )
fi

printf '开发依赖已就绪。用 IDEA 打开 web/backend，运行 Campus Backend Local。\n'
printf '健康检查 http://127.0.0.1:18081/readyz\n'
