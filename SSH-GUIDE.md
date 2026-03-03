# SSH Demo Container - Connection Guide

## Quick Start

### 1. Start the Container

```bash
docker-compose up -d
```

Verify it's running:
```bash
docker ps | grep ubuntu-ssh-demo
```

---

## 2. Connect via SSH

Connect to the container using standard SSH:

```bash
ssh -p 2223 demo@localhost
```

**Password:** `demo`

On first connection, you'll be prompted to accept the host key. Type `yes` and press Enter.

---

## 3. Once Inside the Container

### View the Protected PostgreSQL Config File

```bash
cat /etc/postgresql/16/main/postgresql.conf
```

This should display:
```
# demo config
listen_addresses = '*'
```

### Try to Edit Without Sudo (Will Fail)

```bash
echo "test_line" >> /etc/postgresql/16/main/postgresql.conf
```

Expected output:
```
bash: /etc/postgresql/16/main/postgresql.conf: Permission denied
```

### Edit the File Using Sudo

```bash
sudo sh -c 'echo "max_connections = 200" >> /etc/postgresql/16/main/postgresql.conf'
```

When prompted for password, enter: `demo`

### Verify the Change

```bash
cat /etc/postgresql/16/main/postgresql.conf
```

Should now show:
```
# demo config
listen_addresses = '*'
max_connections = 200
```

### Exit the Container

```bash
exit
```

---

## Service Management with Systemctl Wrapper

The container includes a custom **systemctl wrapper** that allows you to manage the demo service. This wrapper simulates systemctl functionality and is restricted to work only with sudo.

### Verify the Wrapper Works Only with Sudo

**1. Try to start the service WITHOUT sudo (will fail):**

```bash
systemctl start demo-service
```

Expected output:
```
This container only supports management of demo-service
exit status 1
```

**2. Start the service WITH sudo (successful):**

```bash
sudo systemctl start demo-service
```

You'll be prompted for the password: `demo`

Expected output:
```
Starting demo-service...
```

**3. Check service status:**

```bash
sudo systemctl status demo-service
```

**4. View service logs:**

```bash
sudo systemctl restart demo-service
sudo tail -f /var/log/demo-service.log
```

**5. Stop the service:**

```bash
sudo systemctl stop demo-service
```

### Why Sudo-Only Access?

The systemctl wrapper is configured with restricted permissions to ensure that only authenticated users with elevated privileges can manage services. This prevents unauthorized service modifications and aligns with production Postgres security practices where service restarts require administrative approval.

---

## Container Management

### Stop the container
```bash
docker-compose down
```

### Restart the container
```bash
docker-compose restart
```

### View container logs
```bash
docker-compose logs -f
```

### Reset the container (fresh start)
```bash
docker-compose down
docker-compose up -d --build
```

---

## Troubleshooting

### SSH Host Key Mismatch

If you encounter an error like:

```
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
@    WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!     @
@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
```

This occurs when the container host key has changed (e.g., after rebuilding the container). To fix this, remove the old host key entry:

```bash
ssh-keygen -f '~/.ssh/known_hosts' -R '[localhost]:2223'
```

Then try connecting again:

```bash
ssh -p 2223 demo@localhost
```

You'll be prompted to verify and accept the new host key fingerprint. Type `yes` and press Enter to add it to your `known_hosts` file.

---

## Credentials

- **Username:** demo
- **Password:** demo
- **SSH Port:** 2223
- **Sudo Access:** Yes (password required)
