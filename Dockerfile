FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    openssh-server sudo systemd systemd-sysv dbus dbus-user-session \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /var/run/sshd

# Create a normal user with password and sudo
RUN useradd -m -s /bin/bash demo \
 && echo 'demo:demo' | chpasswd \
 && usermod -aG sudo demo \
 && echo 'demo ALL=(ALL) ALL' > /etc/sudoers.d/demo \
 && chmod 0440 /etc/sudoers.d/demo

# Configure sshd to allow password auth (for your simulation)
RUN sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication yes/' /etc/ssh/sshd_config \
 && sed -i 's/^#\?KbdInteractiveAuthentication.*/KbdInteractiveAuthentication yes/' /etc/ssh/sshd_config \
 && sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config \
 && sed -i 's/^#\?UsePAM.*/UsePAM yes/' /etc/ssh/sshd_config

# Fake "postgres config" that requires sudo to edit
RUN mkdir -p /etc/postgresql/16/main \
 && printf "# demo config\nlisten_addresses = '*'\n" > /etc/postgresql/16/main/postgresql.conf \
 && chown root:root /etc/postgresql/16/main/postgresql.conf \
 && chmod 0644 /etc/postgresql/16/main/postgresql.conf

# Create a simple demo systemd service
RUN mkdir -p /etc/systemd/system && cat > /etc/systemd/system/demo-service.service << 'EOF'
[Unit]
Description=Demo Service for Testing
After=network.target

[Service]
Type=simple
ExecStart=/bin/bash -c "while true; do echo 'Demo service running at '$(date); sleep 60; done"
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# Create a systemctl wrapper script for container use
RUN mkdir -p /opt/local/bin && cat > /opt/local/bin/systemctl-wrapper.sh << 'EOF'
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
EOF

RUN chmod +x /opt/local/bin/systemctl-wrapper.sh

# Alias systemctl to our wrapper for demo-service when running in container
RUN cat > /usr/local/bin/systemctl << 'EOF'
#!/bin/bash
# Wrapper that delegates to the service control script for demo-service
# Usage: systemctl [action] [service|options]
if [[ "$*" == *"demo-service"* ]]; then
  # Extract action (first argument) and service (second argument)
  ACTION="$1"
  SERVICE="$2"
  exec /opt/local/bin/systemctl-wrapper.sh system "$ACTION" "$SERVICE"
else
  echo "This container only supports management of demo-service" >&2
  exit 1
fi
EOF

RUN chmod +x /usr/local/bin/systemctl

EXPOSE 22

CMD ["/usr/sbin/sshd", "-D"]
