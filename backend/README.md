# Global VPN — Backend

Node.js / Express backend for the Global VPN Android app. Deploy-ready for the **Render free tier**:

| Setting | Value |
|---|---|
| Root Directory | `backend` |
| Build Command | `npm install` |
| Start Command | `node server.js` |
| Plan | Free ($0/month) |

## Environment variables (Render → Environment)

| Variable | Required | What it is |
|---|---|---|
| `PORT` | No (Render sets it) | Port the server listens on |
| `ADMIN_PASSWORD` | **Yes** | Password for the admin panel (`/admin`) |
| `ADMIN_TOKEN_SECRET` | Recommended | Secret used to sign admin tokens |
| `DATA_DIR` | No (default `./data`) | Where `db.json` is stored |
| `REWARD_DEV_MODE` | No (default `false`) | `true` credits rewarded-ad claims immediately without AdMob SSV — **testing only, never enable in production** |

## What it does

- **Credit wallets** — guest IDs, balances, transactions (server-side truth; the app never trusts a client-computed balance).
- **Welcome credits** — 100 once per guest.
- **Sessions** — WireGuard session start / heartbeat / end. VPN use bills **10 credits per completed 30-minute block** on heartbeat. When a wallet cannot pay, the heartbeat returns `creditsExhausted: true` and the session ends so the user can earn more credits.
- **Reward verification (AdMob SSV)** — the app creates a PENDING claim at `POST /api/reward/claim`; Google calls `GET /api/reward/ssv` with a signed query string, the signature is verified against Google's public keys, and only then are **20 credits** added. One `transaction_id` is credited at most once.
- **Server registry** — WireGuard servers (country, city, host, port, public key, status, load). `/api/servers` returns only real registered servers; with none registered it returns `[]` and the app shows "No servers configured yet".
- **Admin panel** — `/admin` (password login): add/remove servers, ping-test a server, monitor active connections, list users, review reward claims, configure credit rates, view error reports, maintenance mode.
- **Public pages** — `/privacy-policy`, `/terms`, `/disclaimer`, `/ads.txt`.

## API (summary)

```
GET  /api/health
GET  /api/config
GET  /api/servers
GET  /api/wallet/:guestId
GET  /api/wallet/:guestId/transactions
POST /api/wallet/welcome            { guestId }
POST /api/session/start             { guestId, serverId, clientPublicKey }
POST /api/session/heartbeat         { sessionId }
POST /api/session/end               { sessionId, bytesRx, bytesTx }
POST /api/reward/claim              { guestId, claimId }
GET  /api/reward/ssv                (called by Google/AdMob)
POST /api/error/report              { guestId, message, source }
DELETE /api/account/:guestId
POST /api/admin/login               { password }
GET  /api/admin/stats               (x-admin-token header)
GET  /api/admin/servers  POST /api/admin/servers
PUT  /api/admin/servers/:id  DELETE /api/admin/servers/:id
POST /api/admin/servers/:id/ping
GET  /api/admin/users  GET /api/admin/rewards  GET /api/admin/errors
GET  /api/admin/settings  PUT /api/admin/settings
GET  /ads.txt  GET /privacy-policy  GET /terms  GET /disclaimer
```

## Data storage

A single JSON file (`data/db.json`, atomic writes) holds users, servers, sessions, claims, errors and settings. This keeps the Render free tier zero-config. For larger scale, replace `store.js` with Postgres — `server.js` does not change its API.

`data/` is git-ignored: user wallets and server data are never committed.

## AdMob server-side verification (SSV) setup

In the AdMob console, open the rewarded ad unit → **Server-side verification** and set:

- **Callback URL:** `https://<your-render-host>/api/reward/ssv`
- **Custom data:** the app sends the `claimId` it generates per reward (the app's `AdMobManager` uses the claimId as custom data).

Google signs the callback; the backend verifies the signature against the keys at `https://www.gstatic.com/admob/reward/verifier-keys.json` and credits the wallet only on a valid signature.

## Adding real WireGuard servers (Oracle Cloud free tier)

The owner plans to run **2 free servers on Oracle Cloud** (Always Free tier, 2 AMD VMs or ARM instances). For each server:

1. Create the Oracle Cloud VM (Ubuntu) and open UDP port **51820** in the security list / firewall.
2. Install WireGuard: `sudo apt install wireguard`.
3. Generate keys on the server: `wg genkey | tee privatekey | wg pubkey > publickey`.
4. Create `/etc/wireguard/wg0.conf` on the server:
   ```
   [Interface]
   PrivateKey = <server private key — NEVER share this>
   Address = 10.8.0.1/24
   ListenPort = 51820
   # Allow forwarding + NAT so clients reach the internet:
   PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
   PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE
   ```
   (Adjust `eth0` to the VM's real network interface.)
5. Enable IP forwarding: `echo "net.ipv4.ip_forward=1" | sudo tee -a /etc/sysctl.conf && sudo sysctl -p`.
6. Start WireGuard: `sudo wg-quick up wg0` and `sudo systemctl enable wg-quick@wg0`.
7. The backend assigns each client an address in `10.8.0.0/24` and sends the client's public key in the session. The server must accept new peers — either add a `[Peer]` per client (the admin panel does not do this automatically yet), or use a WireGuard management tool. **This is the main remaining server-side step: the backend hands out client addresses and keys, but the Oracle server must be configured to accept those peers.**
8. In the admin panel (`/admin` → Add a VPN server), enter the country, city, host (the VM's public IP), port `51820`, and the server's **public key**. The server appears in the app only when marked `online`.

### Example server entry (PLACEHOLDER — not a real server)

```json
{
  "country": "United States",
  "countryCode": "US",
  "city": "New York",
  "host": "203.0.113.10",
  "port": 51820,
  "publicKey": "EXAMPLE_PUBLIC_KEY_REPLACE_WITH_REAL_KEY",
  "status": "online"
}
```

Values above are examples only (`203.0.113.x` is the TEST-NET range). Replace every field with the real server's values.

## Speed

The app measures real ping to each server (TCP connect timing) and auto-selects the lowest-ping server. Real speed depends on the server's location, bandwidth and current load, and on the user's own connection — the backend and the app never invent speed numbers.
