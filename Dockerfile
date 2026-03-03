FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    openssh-server sudo \
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

EXPOSE 22

CMD ["/usr/sbin/sshd", "-D"]
