#!/usr/bin/env bash
# Sao lưu cơ sở dữ liệu ra S3: pg_dump -> gzip -> s3://$BACKUP_BUCKET/db/. Chạy bằng cron/systemd timer trên máy EC2.
# Cần: Docker (dùng image postgres để có pg_dump, khỏi cài client), AWS CLI, IAM role cho phép ghi vào bucket sao lưu.
# Biến môi trường: DB_HOST, DB_NAME, DB_USERNAME, DB_PASSWORD, BACKUP_BUCKET (đọc từ /opt/soudan/.env nếu có).
set -euo pipefail

ENV_FILE="${ENV_FILE:-/opt/soudan/.env}"
if [ -f "$ENV_FILE" ]; then
  set -a; . "$ENV_FILE"; set +a
fi
: "${DB_HOST:?thiếu DB_HOST}" "${DB_NAME:?thiếu DB_NAME}" "${DB_USERNAME:?thiếu DB_USERNAME}" "${DB_PASSWORD:?thiếu DB_PASSWORD}" "${BACKUP_BUCKET:?thiếu BACKUP_BUCKET}"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="/tmp/soudan-${STAMP}.sql.gz"
trap 'rm -f "$FILE"' EXIT

docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:16-alpine \
  pg_dump -h "$DB_HOST" -U "$DB_USERNAME" -d "$DB_NAME" --no-owner --no-privileges | gzip > "$FILE"

# Một bản trống/quá nhỏ gần như chắc chắn là lỗi: không đẩy lên để khỏi tưởng đã có bản sao lưu
if [ "$(stat -c %s "$FILE")" -lt 1024 ]; then
  echo "Bản sao lưu quá nhỏ, có thể pg_dump đã lỗi" >&2
  exit 1
fi

aws s3 cp "$FILE" "s3://${BACKUP_BUCKET}/db/soudan-${STAMP}.sql.gz" --only-show-errors
echo "Đã sao lưu: s3://${BACKUP_BUCKET}/db/soudan-${STAMP}.sql.gz ($(stat -c %s "$FILE") byte)"
