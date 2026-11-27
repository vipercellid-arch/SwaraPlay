#!/usr/bin/env bash
set -e
TARGET_DIR="/var/www/swaraplay"
mkdir -p "$TARGET_DIR"
cp -rf "$(dirname "$0")"/* "$TARGET_DIR/"
cd "$TARGET_DIR"
if command -v pm2 >/dev/null 2>&1; then
  pm2 delete swaraplay-web 2>/dev/null || true
  pm2 start server.js --name swaraplay-web
  pm2 save
  pm2 status swaraplay-web
else
  pkill -f "/var/www/swaraplay/server.js" 2>/dev/null || true
  nohup node /var/www/swaraplay/server.js >/var/log/swaraplay-web.log 2>&1 &
  sleep 1
  echo "SwaraPlay Web v10.0.0 running on port 8088 (PID $!)"
fi
