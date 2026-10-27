#!/bin/bash

# Script de Backup - Larla Launcher
# Uso: ./backup.sh

BACKUP_DIR="/home/lordk/apks/backups"
PROJECT_DIR="/home/lordk/apks/Larla Launcher"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_NAME="larla_launcher_backup_${TIMESTAMP}.tar.gz"
LOG_FILE="${BACKUP_DIR}/backup.log"

# Criar diretório de backups se não existir
mkdir -p "$BACKUP_DIR"

# Backup
echo "[$(date)] Iniciando backup..." >> "$LOG_FILE"
tar -czf "${BACKUP_DIR}/${BACKUP_NAME}" \
    --exclude='.gradle' \
    --exclude='build' \
    --exclude='*.apk' \
    -C "$PROJECT_DIR" . 2>/dev/null

if [ $? -eq 0 ]; then
    SIZE=$(du -h "${BACKUP_DIR}/${BACKUP_NAME}" | cut -f1)
    echo "[$(date)] Backup criado: ${BACKUP_NAME} (${SIZE})" >> "$LOG_FILE"
    echo "Backup criado: ${BACKUP_NAME} (${SIZE})"
else
    echo "[$(date)] ERRO no backup!" >> "$LOG_FILE"
    echo "Erro ao criar backup!"
    exit 1
fi

# Manter apenas os últimos 10 backups
cd "$BACKUP_DIR" || exit
ls -t larla_launcher_backup_*.tar.gz 2>/dev/null | tail -n +11 | xargs -r rm -f
echo "[$(date)] Limpeza automática concluída" >> "$LOG_FILE"