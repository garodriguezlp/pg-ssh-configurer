Yes — totally possible. You’re basically describing “a small fake VM” built with Docker: an Ubuntu container running `sshd`, exposing port 22 to your host, with a normal user + password auth, and with root-owned config files that require `sudo` to edit. It’s a very common way to test SSH automation locally.

A few practical notes up front:

* **Docker containers aren’t VMs**, but you *can* run `sshd` inside them and treat them like a remote host.
* You’ll typically need to install and start services yourself (no full systemd by default). For `sshd`, that’s fine.
* For the “needs sudo” behavior: just make the file **owned by root** and not writable by your user.

Below is a complete working example.

---

## 1) Dockerfile (Ubuntu + SSH + password + sudo)

```dockerfile
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
```

This creates:

* user: `demo`
* password: `demo`
* ssh password login enabled
* `/etc/postgresql/16/main/postgresql.conf` writable only by root (so `demo` needs `sudo`)

---

## 2) Build and run

```bash
docker build -t ubuntu-ssh-demo .
docker run -d --name ubuntu-ssh-demo -p 2222:22 ubuntu-ssh-demo
```

Now you have an “SSH-able remote host” on your machine: `localhost:2222`.

---

## 3) SSH into it (password prompt)

```bash
ssh -p 2222 demo@localhost
```

First time, accept the host key, then enter password `demo`.

---

## 4) Verify “cannot edit without sudo”

Inside the SSH session:

```bash
echo "x" >> /etc/postgresql/16/main/postgresql.conf
# should fail with permission denied

sudo sh -c 'echo "max_connections = 200" >> /etc/postgresql/16/main/postgresql.conf'
# should work (prompts for demo's password)
```

---

## 5) Optional: make it feel even more “real”

* Give it a hostname:

  ```bash
  docker run -d --name ubuntu-ssh-demo --hostname fake-remote -p 2222:22 ubuntu-ssh-demo
  ```
* Put it on a dedicated Docker network (if you want multiple “servers” talking to each other).
* Add extra users, groups, locked-down directories, etc.

---

## Security note (since you’re enabling password auth)

This is fine for local dev/testing. Just avoid exposing it publicly (don’t map `-p 22:22` on a cloud host without tightening SSH config, keys-only, firewalling, etc.).

If you want, I can also give you a variant that:

* uses **SSH keys** by default (more realistic),
* still *optionally* supports password for specific users,
* and includes a tiny “restart service” script you can call via sudo to simulate `systemctl restart postgresql`.
