#!/bin/sh
set -eu

mysql_db() {
    mysql -h mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names "$MYSQL_DATABASE" "$@"
}

check_only=${CAMPUS_MIGRATION_CHECK_ONLY:-0}
if [ "$check_only" = 1 ]; then
    if ! mysql_db -e 'SELECT 1 FROM campus_schema_migration LIMIT 0'; then
        echo '迁移记录不可用，请先核对数据库并运行迁移任务。' >&2
        exit 1
    fi
else
    mysql_db <<'SQL'
CREATE TABLE IF NOT EXISTS campus_schema_migration (
    version VARCHAR(8) PRIMARY KEY,
    filename VARCHAR(128) NOT NULL,
    checksum CHAR(64) NOT NULL,
    status ENUM('running', 'applied', 'failed') NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL DEFAULT NULL
);
SQL
fi

migration_record() {
    mysql_db -e "
        SELECT CONCAT(filename, ':', checksum, ':', status)
        FROM campus_schema_migration WHERE version='$1';"
}

for migration in /migrations/*.sql; do
    filename=${migration##*/}
    version=${filename%%-*}
    case "$filename" in
        *[!a-zA-Z0-9._-]*) echo "非法迁移文件名 $filename" >&2; exit 1 ;;
    esac
    case "$version" in
        ''|*[!0-9]*) echo "迁移文件需要数字前缀 $filename" >&2; exit 1 ;;
    esac
    checksum=$(sha256sum "$migration")
    checksum=${checksum%% *}

    if [ "$check_only" = 1 ]; then
        recorded=$(migration_record "$version")
        if [ "$recorded" != "$filename:$checksum:applied" ]; then
            echo "发布所需迁移未通过检查 $filename（记录为 ${recorded:-无}）" >&2
            exit 1
        fi
        continue
    fi

    # 先登记执行权。另一个进程看到 running 时必须停止。
    claimed=$(mysql_db -e "
        INSERT IGNORE INTO campus_schema_migration (version, filename, checksum, status)
        VALUES ('$version', '$filename', '$checksum', 'running');
        SELECT ROW_COUNT();")
    if [ "$claimed" = 0 ]; then
        recorded=$(migration_record "$version")
        if [ "$recorded" = "$filename:$checksum:applied" ]; then
            echo "已应用 $filename"
            continue
        fi
        echo "迁移记录冲突或上次未完成 $filename（记录为 $recorded）" >&2
        exit 1
    fi

    echo "开始迁移 $filename"
    if mysql_db < "$migration"; then
        mysql_db -e "UPDATE campus_schema_migration SET status='applied', finished_at=CURRENT_TIMESTAMP WHERE version='$version';"
    else
        mysql_db -e "UPDATE campus_schema_migration SET status='failed', finished_at=CURRENT_TIMESTAMP WHERE version='$version';"
        echo "迁移失败 $filename" >&2
        exit 1
    fi
done
