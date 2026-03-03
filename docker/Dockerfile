FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

# Install required packages
RUN apt-get update && apt-get install -y \
    openssh-server sudo systemd systemd-sysv dbus dbus-user-session \
 && rm -rf /var/lib/apt/lists/*

# Copy setup scripts (build context is root directory)
COPY docker/scripts/ /tmp/docker-setup/
RUN chmod +x /tmp/docker-setup/*.sh

# Run setup scripts
RUN /tmp/docker-setup/setup-user.sh
RUN /tmp/docker-setup/setup-ssh.sh
RUN /tmp/docker-setup/setup-postgres-config.sh
RUN /tmp/docker-setup/setup-demo-service.sh

# Copy systemctl wrapper (to be installed by setup script)
COPY docker/scripts/systemctl-wrapper.sh /tmp/docker-assets/
RUN /tmp/docker-setup/setup-systemctl-wrapper.sh

# Clean up temporary files
RUN rm -rf /tmp/docker-setup /tmp/docker-assets

EXPOSE 22

CMD ["/usr/sbin/sshd", "-D"]
