#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/var/www/swaraplay"
SERVICE_NAME="swaraplay-web"
PORT="${PORT:-8088}"

mkdir -p "$APP_DIR"
cp -a ./* "$APP_DIR/"

if command -v systemctl >/dev/null 2>&1; then
  cat >/etc/systemd/system/${SERVICE_NAME}.service <<EOF
[Unit]
Description=SwaraPlay Web v11.0.0 (EncDecEndpoints Instant Streaming)
After=network.target

[Service]
Type=simple
WorkingDirectory=${APP_DIR}
Environment=PORT=${PORT}
ExecStart=$(command -v node) ${APP_DIR}/server.js
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable ${SERVICE_NAME}
  systemctl restart ${SERVICE_NAME}
  systemctl status ${SERVICE_NAME} --no-pager -l || true
else
  pkill -f "${APP_DIR}/server.js" || true
  nohup node "${APP_DIR}/server.js" >/var/log/swaraplay-web.log 2>&1 &
fi

echo "SwaraPlay Web v11.0.0 aktif di port ${PORT}"
