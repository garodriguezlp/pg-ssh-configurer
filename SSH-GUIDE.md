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

## Credentials

- **Username:** demo
- **Password:** demo
- **SSH Port:** 2223
- **Sudo Access:** Yes (password required)
