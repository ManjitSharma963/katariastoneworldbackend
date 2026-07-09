# Railway deploy playbook — Kataria

Repeatable steps to deploy **Backend API** + **Inventory UI** on Railway.

**Target URLs**
- UI: `https://www.katariastoneworld.com/inventory`
- API: `https://www.katariastoneworld.com/api`

---

## Architecture

```
www.katariastoneworld.com
        │
        ▼
  Gateway (kataristoneworldinventory repo)
    /inventory  → React static files
    /api/*      → proxy to API
        │
        ▼
  API (katariastoneworldbackend repo)
        │
        ▼
  MySQL (Railway plugin)
```

| Service | Repo | Dockerfile |
|---------|------|------------|
| MySQL | Railway plugin | — |
| API | `katariastoneworldbackend` | `/Dockerfile` |
| Gateway | `kataristoneworldinventory` | `/deploy/gateway/Dockerfile` |

**Deploy order:** MySQL → API (healthy) → Gateway → verify

---

## One-time setup

1. Push both repos to GitHub.
2. Create Railway project at [railway.com/new](https://railway.com/new).
3. Save a strong `JWT_SECRET` (32+ characters).

---

## Step 1 — MySQL

1. **+ New** → **Database** → **MySQL**
2. Note variables: `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`
3. Import existing data if migrating: `mysqldump` → import via Railway CLI

---

## Step 2 — Backend API

### Create service
1. **+ New** → **GitHub Repo** → `katariastoneworldbackend`
2. Rename service to **`api`**

### Link MySQL
1. API → **Variables** → **Add Reference** → select MySQL service
2. Add all `MYSQL*` variables

### Variables (API service)
```env
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=<your-strong-secret>
CORS_ALLOWED_ORIGINS=https://www.katariastoneworld.com
CORS_ALLOW_ALL=false
SWAGGER_UI_ENABLED=false
```

Optional mail:
```env
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
```

### Networking
- Enable **Private Networking**
- Do **not** attach `www.katariastoneworld.com` to API

### Verify
```text
https://<api-service>.up.railway.app/actuator/health  →  {"status":"UP"}
```

---

## Step 3 — Gateway (Inventory UI + nginx)

### Create service
1. **+ New** → **GitHub Repo** → `kataristoneworldinventory`
2. Rename service to **`gateway`**
3. Uses `railway.toml` → builds `deploy/gateway/Dockerfile`

### Variables (Gateway service)
```env
BACKEND_URL=http://api.railway.internal:8080
```

Replace `api` with your backend service name if different.

Build arg (in `railway.toml` or Variables):
```env
REACT_APP_API_URL=https://www.katariastoneworld.com/api
```

### Custom domain
1. Gateway → **Settings** → **Networking** → **Custom Domain**
2. Add: `www.katariastoneworld.com`
3. At domain registrar, add CNAME:

| Type | Name | Value |
|------|------|--------|
| CNAME | www | (Railway CNAME target) |

### Verify
```text
https://www.katariastoneworld.com/health
https://www.katariastoneworld.com/actuator/health
https://www.katariastoneworld.com/inventory/
```

PowerShell verify script:
```powershell
cd katariastoneworldbackend
$env:KATARIA_TEST_EMAIL="your@email.com"
$env:KATARIA_TEST_PASSWORD="yourpassword"
.\scripts\go-live-verify.ps1
```

---

## Redeploy after code changes

| Changed | Action |
|---------|--------|
| Backend code | Push `katariastoneworldbackend` → wait for API healthy |
| UI code | Push `kataristoneworldinventory` → redeploy gateway |
| Env vars only | Update in Railway → redeploy that service |

---

## Go-live checklist

- [ ] MySQL running, data imported, Flyway applied on first API start
- [ ] API `/actuator/health` → UP
- [ ] `POST /api/auth/login` works
- [ ] `/api/inventory`, `/api/bills`, `/api/expenses` work with JWT
- [ ] `/inventory` loads and calls `/api`
- [ ] CORS: `https://www.katariastoneworld.com` only
- [ ] Secrets not in git (`deploy/railway.env` gitignored)
- [ ] Mobile: `EXPO_PUBLIC_API_URL=https://www.katariastoneworld.com/api`

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Access denied for user` | Link MySQL to API; check `MYSQLPASSWORD` |
| 502 on `/api` | Fix `BACKEND_URL`; ensure API is up + private networking on |
| UI blank at `/inventory` | Check gateway build logs |
| CORS errors | Set `CORS_ALLOWED_ORIGINS` on API |
| UI wrong API URL | Rebuild gateway with `REACT_APP_API_URL` |

---

## Local dev against Railway DB

```powershell
cd katariastoneworldbackend
.\scripts\run-with-railway-db.ps1
```

Credentials template: `deploy/railway.env.example` (copy to `deploy/railway.env`, gitignored).
