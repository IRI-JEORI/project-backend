#!/usr/bin/env bash
set -Eeuo pipefail

DB_NAME="nunnun"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/nunnun}"
MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-}"
TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
FINAL_PATH="${BACKUP_DIR}/${DB_NAME}-${TIMESTAMP}.sql"
TEMP_PATH="${FINAL_PATH}.tmp"

cleanup() {
  rm -f -- "$TEMP_PATH"
}
trap cleanup ERR INT TERM

command -v mysqldump >/dev/null 2>&1 || {
  echo "ERROR: mysqldump is not installed." >&2
  exit 1
}

mkdir -p -- "$BACKUP_DIR"
chmod 700 -- "$BACKUP_DIR"

defaults_option=()
if [[ -n "$MYSQL_DEFAULTS_FILE" ]]; then
  if [[ ! -r "$MYSQL_DEFAULTS_FILE" ]]; then
    echo "ERROR: MYSQL_DEFAULTS_FILE is not readable." >&2
    exit 1
  fi
  # MySQL requires --defaults-extra-file before other options.
  defaults_option=("--defaults-extra-file=${MYSQL_DEFAULTS_FILE}")
fi

echo "Creating a consistent read-only logical backup of database '${DB_NAME}'..."
mysqldump "${defaults_option[@]}" \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --events \
  --default-character-set=utf8mb4 \
  --set-gtid-purged=OFF \
  "$DB_NAME" > "$TEMP_PATH"

if [[ ! -s "$TEMP_PATH" ]]; then
  echo "ERROR: mysqldump produced an empty file." >&2
  exit 1
fi

chmod 600 -- "$TEMP_PATH"
mv -- "$TEMP_PATH" "$FINAL_PATH"
trap - ERR INT TERM

echo "Backup completed: ${FINAL_PATH}"
