#!/bin/bash
# Simple systemctl wrapper for demo-service
SERVICE_NAME="${3:-demo-service}"
ACTION="$2"

case "$ACTION" in
  start)
    echo "Starting $SERVICE_NAME..."
    nohup /bin/bash -c 'while true; do echo "Demo service running at $(date)"; sleep 60; done' > /var/log/demo-service.log 2>&1 &
    echo $! > /var/run/demo-service.pid
    ;;
  stop)
    echo "Stopping $SERVICE_NAME..."
    if [ -f /var/run/demo-service.pid ]; then
      kill $(cat /var/run/demo-service.pid) 2>/dev/null || true
      rm -f /var/run/demo-service.pid
    fi
    ;;
  restart)
    echo "Restarting $SERVICE_NAME..."
    if [ -f /var/run/demo-service.pid ]; then
      kill $(cat /var/run/demo-service.pid) 2>/dev/null || true
      rm -f /var/run/demo-service.pid
    fi
    sleep 1
    nohup /bin/bash -c 'while true; do echo "Demo service running at $(date)"; sleep 60; done' > /var/log/demo-service.log 2>&1 &
    echo $! > /var/run/demo-service.pid
    echo "$SERVICE_NAME restarted successfully"
    ;;
  status)
    echo "$SERVICE_NAME status: running"
    ;;
  *)
    echo "Usage: $0 <command> <action> [service]"
    exit 1
    ;;
esac
