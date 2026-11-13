/**
 * Global VPN backend — Node.js / Express.
 *
 * Deploy-ready for the Render free tier:
 *   Root Directory: backend
 *   Build Command:  npm install
 *   Start Command:  node server.js
 *
 * Env vars:
 *   PORT               (Render sets this automatically)
 *   ADMIN_PASSWORD     admin panel password (REQUIRED — set in Render)
 *   ADMIN_TOKEN_SECRET secret for signing admin tokens (set in Render;
 *                      defaults to a random value per process restart)
 *   DATA_DIR           where db.json lives (default ./data)
 *   REWARD_DEV_MODE    "true" credits rewards on claim without AdMob
 *                      server-side verification — TESTING ONLY, keep
 *                      false in production.
 *   WG_SSH_PRIVATE_KEY SSH private key (PEM) for the WireGuard VMs.
 *                      REQUIRED for peer provisioning — without it
 *                      sessions are recorded but the VPN server never
 *                      learns about the client peer, so no traffic
 *                      flows. See wg-provision.js.
 *   WG_SSH_USER        SSH username for the VMs (default "ubuntu").
 *   WG_SSH_PORT        SSH port (default 22).
 *   WG_INTERFACE       WireGuard interface (default "wg0").
 *
 * Money/credits rules (all server-side, never trusted from the client):
 *   - 100 welcome credits, once per guest.
 *   - 10 credits per 30 minutes of VPN use, billed per completed
 *     30-minute block on heartbeat.
 *   - 20 credits per rewarded ad, credited ONLY after AdMob
 *     server-side verification (SSV) — see /api/reward/ssv.
 *   - All values are configurable in the admin panel.
 *
 * Honesty: /api/servers lists ONLY real servers registered in the
 * admin panel. With no servers configured it returns an empty list and
 * the app shows "No servers configured yet" — it never invents one.
 */
const express = require("express");
const crypto = require("crypto");
const path = require("path");
const fs = require("fs");
const net = require("net");
const store = require("./store");
const wgProvision = require("./wg-provision");

const app = express();
app.use(express.json({ limit: "1mb" }));
app.use(express.static(path.join(__dirname, "public")));

const PORT = process.env.PORT || 3000;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "";
const TOKEN_SECRET =
  process.env.ADMIN_TOKEN_SECRET || crypto.randomBytes(32).toString("hex");
const REWARD_DEV_MODE = process.env.REWARD_DEV_MODE === "true";

const db = store.load();

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

function publicWallet(user) {
  return {
    guestId: user.guestId,
    balance: user.balance,
    totalEarned: user.totalEarned,
    totalUsed: user.totalUsed,
    welcomeClaimed: user.welcomeClaimed,
  };
}

function getOrCreateUser(guestId) {
  let user = db.users[guestId];
  if (!user) {
    user = {
      guestId,
      balance: 0,
      totalEarned: 0,
      totalUsed: 0,
      welcomeClaimed: false,
      createdAt: new Date().toISOString(),
    };
    db.users[guestId] = user;
    store.save();
  }
  return user;
}

/** Lowest free client address in 10.8.0.0/24 (10.8.0.2 .. 10.8.0.254). */
function nextClientAddress() {
  const used = new Set(
    db.sessions.filter((s) => !s.endedAt).map((s) => s.clientAddress)
  );
  for (let i = 2; i <= 254; i++) {
    const addr = `10.8.0.${i}/32`;
    if (!used.has(addr)) return addr;
  }
  return null;
}

/** Bills completed 30-minute blocks for a session. Returns { billed, exhausted }. */
function billSession(session) {
  const settings = db.settings;
  const elapsedMs = Date.now() - new Date(session.startedAt).getTime();
  const blocks = Math.floor(elapsedMs / (30 * 60 * 1000));
  const newBlocks = blocks - (session.billedBlocks || 0);
  if (newBlocks <= 0) return { billed: 0, exhausted: false };
  const cost = newBlocks * settings.creditsPer30Min;
  const user = getOrCreateUser(session.guestId);
  if (user.balance < cost) {
    // Not enough credits: do NOT bill partially — the session is
    // exhausted and must end so the user can earn more credits.
    return { billed: 0, exhausted: true };
  }
  store.applyTransaction(session.guestId, -cost, `VPN use (${newBlocks}x 30 min)`);
  session.billedBlocks = blocks;
  session.lastHeartbeatAt = new Date().toISOString();
  store.save();
  return { billed: cost, exhausted: false };
}

// ------------------------------------------------------------------
// Public API
// ------------------------------------------------------------------

app.get("/api/health", (req, res) => {
  res.json({
    status: "ok",
    service: "global-vpn-backend",
    maintenanceMode: db.settings.maintenanceMode,
    serversOnline: db.servers.filter((s) => s.status === "online").length,
    // False until WG_SSH_PRIVATE_KEY is set in Render — without it,
    // sessions are recorded but peers are never added to the VPN
    // server, so no traffic flows. See wg-provision.js.
    peerProvisioningConfigured: wgProvision.isConfigured(),
    time: new Date().toISOString(),
  });
});

app.get("/api/config", (req, res) => {
  const s = db.settings;
  res.json({
    welcomeCredits: s.welcomeCredits,
    rewardCredits: s.rewardCredits,
    creditsPer30Min: s.creditsPer30Min,
    lowCreditThreshold: s.lowCreditThreshold,
    maintenanceMode: s.maintenanceMode,
    supportEmail: s.supportEmail,
    minVersion: "1.0.0",
  });
});

/** Public server list — only real, registered servers, no private keys. */
app.get("/api/servers", (req, res) => {
  const list = db.servers.map((s) => ({
    id: s.id,
    country: s.country,
    countryCode: s.countryCode,
    city: s.city,
    host: s.host,
    port: s.port,
    publicKey: s.publicKey,
    status: s.status,
    load: s.load,
  }));
  res.json(list);
});

app.get("/api/wallet/:guestId", (req, res) => {
  const user = getOrCreateUser(req.params.guestId);
  res.json(publicWallet(user));
});

app.get("/api/wallet/:guestId/transactions", (req, res) => {
  const list = db.transactions
    .filter((t) => t.guestId === req.params.guestId)
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, 100);
  res.json(list);
});

/** One-time welcome credits (default 100). Server enforces once-per-guest. */
app.post("/api/wallet/welcome", (req, res) => {
  const { guestId } = req.body || {};
  if (!guestId) return res.status(400).json({ error: "guestId required" });
  const user = getOrCreateUser(guestId);
  if (user.welcomeClaimed) return res.json(publicWallet(user));
  store.applyTransaction(guestId, db.settings.welcomeCredits, "Welcome credits");
  user.welcomeClaimed = true;
  store.save();
  res.json(publicWallet(user));
});

// ------------------------------------------------------------------
// Sessions (WireGuard)
// ------------------------------------------------------------------

app.post("/api/session/start", (req, res) => {
  const { guestId, serverId, clientPublicKey } = req.body || {};
  if (!guestId || !serverId || !clientPublicKey) {
    return res.status(400).json({ error: "guestId, serverId and clientPublicKey required" });
  }
  if (db.settings.maintenanceMode) {
    return res.status(503).json({ error: "Service is in maintenance mode" });
  }
  const server = db.servers.find((s) => s.id === serverId);
  if (!server) return res.status(404).json({ error: "Server not found" });
  if (server.status !== "online") {
    return res.status(409).json({ error: "Server is not online" });
  }
  const user = getOrCreateUser(guestId);
  if (user.balance < db.settings.creditsPer30Min) {
    return res.status(402).json({
      error: "Not enough credits",
      balance: user.balance,
      required: db.settings.creditsPer30Min,
    });
  }
  const clientAddress = nextClientAddress();
  if (!clientAddress) {
    return res.status(503).json({ error: "No free client addresses" });
  }
  const session = {
    id: store.uid("sess"),
    guestId,
    serverId,
    clientAddress,
    clientPublicKey,
    startedAt: new Date().toISOString(),
    lastHeartbeatAt: new Date().toISOString(),
    billedBlocks: 0,
    endedAt: null,
    bytesRx: 0,
    bytesTx: 0,
    peerProvisioned: false,
  };
  db.sessions.push(session);
  store.save();
  // Provision the peer on the real WireGuard server so traffic can
  // actually flow. Fire-and-forget: the tunnel config is returned
  // immediately (WireGuard retries its handshake, so traffic starts
  // flowing as soon as the peer lands, usually < 2s). The result is
  // recorded on the session for the admin panel.
  wgProvision
    .provisionPeer(server, clientPublicKey, clientAddress)
    .then((r) => {
      session.peerProvisioned = r.ok;
      if (!r.ok) session.peerProvisionError = r.reason;
      store.save();
    })
    .catch(() => {});
  res.json({
    sessionId: session.id,
    clientAddress,
    dns: ["1.1.1.1", "8.8.8.8"],
    wallet: publicWallet(user),
    peerProvisioned: wgProvision.isConfigured(),
  });
});

app.post("/api/session/heartbeat", (req, res) => {
  const { sessionId } = req.body || {};
  const session = db.sessions.find((s) => s.id === sessionId);
  if (!session || session.endedAt) {
    return res.status(404).json({ error: "Session not found" });
  }
  const { billed, exhausted } = billSession(session);
  session.lastHeartbeatAt = new Date().toISOString();
  const user = getOrCreateUser(session.guestId);
  if (exhausted) {
    session.endedAt = new Date().toISOString();
    store.save();
    // Credits ran out: remove the peer so the tunnel stops passing traffic.
    const server = db.servers.find((s) => s.id === session.serverId);
    if (server) {
      wgProvision.removePeer(server, session.clientPublicKey).catch(() => {});
    }
  }
  res.json({
    balance: user.balance,
    billed,
    creditsExhausted: exhausted,
  });
});

app.post("/api/session/end", (req, res) => {
  const { sessionId, bytesRx, bytesTx } = req.body || {};
  const session = db.sessions.find((s) => s.id === sessionId);
  if (!session) return res.status(404).json({ error: "Session not found" });
  if (!session.endedAt) {
    // Bill any completed 30-minute blocks before closing.
    billSession(session);
    session.endedAt = new Date().toISOString();
    session.bytesRx = bytesRx || 0;
    session.bytesTx = bytesTx || 0;
    store.save();
    // Remove the peer from the real WireGuard server so the client
    // address is freed and the tunnel cannot keep passing traffic.
    const server = db.servers.find((s) => s.id === session.serverId);
    if (server) {
      wgProvision.removePeer(server, session.clientPublicKey).catch(() => {});
    }
  }
  res.json(publicWallet(getOrCreateUser(session.guestId)));
});

// ------------------------------------------------------------------
// Rewarded ads — server-side verification (AdMob SSV)
// ------------------------------------------------------------------
//
// Flow:
//  1. App: user watches a rewarded ad -> AdMobManager gets a reward.
//  2. App: POST /api/reward/claim { guestId, claimId } -> creates a
//     PENDING claim. The app polls the wallet while it is pending.
//  3. Google: calls GET /api/reward/ssv with a signed query string
//     (user_id = guestId, custom_data = claimId). We verify the
//     signature against Google's public keys and credit the wallet.
//  4. If the SSV callback never arrives, the claim stays PENDING and
//     the admin can review it in the admin panel.
//
// AdMob SSV setup (in the AdMob console, rewarded ad unit settings):
//   Server-side verification callback URL:
//     https://<your-render-host>/api/reward/ssv
//   Custom data: the claimId the app generates per reward.
//
// The signature is verified against the keys at
// https://www.gstatic.com/admob/reward/verifier-keys.json (cached).
// ------------------------------------------------------------------

let ssvKeysCache = { keys: null, fetchedAt: 0 };

async function fetchSsvKeys() {
  const now = Date.now();
  if (ssvKeysCache.keys && now - ssvKeysCache.fetchedAt < 60 * 60 * 1000) {
    return ssvKeysCache.keys;
  }
  const res = await fetch(
    "https://www.gstatic.com/admob/reward/verifier-keys.json"
  );
  if (!res.ok) throw new Error("Failed to fetch SSV keys: " + res.status);
  const data = await res.json();
  ssvKeysCache = { keys: data.keys || [], fetchedAt: now };
  return ssvKeysCache.keys;
}

function verifySsvSignature(query, keys) {
  // AdMob signs the query string (all params except signature and
  // key_id, sorted alphabetically, joined as key=value with &).
  const signature = query.signature;
  const keyId = query.key_id;
  if (!signature || !keyId) return false;
  const params = { ...query };
  delete params.signature;
  delete params.key_id;
  const message = Object.keys(params)
    .sort()
    .map((k) => `${k}=${params[k]}`)
    .join("&");
  const key = keys.find((k) => String(k.keyId) === String(keyId));
  if (!key || !key.pem) return false;
  try {
    const verifier = crypto.createVerify("RSA-SHA256");
    verifier.update(message, "utf8");
    return verifier.verify(key.pem, signature, "base64");
  } catch (e) {
    console.error("SSV verify error:", e.message);
    return false;
  }
}

function creditClaim(claim, transactionId) {
  if (claim.status === "CREDITED") return false;
  // One transaction_id may only ever be credited once.
  if (
    transactionId &&
    db.claims.some((c) => c.transactionId === transactionId && c.status === "CREDITED")
  ) {
    return false;
  }
  claim.status = "CREDITED";
  claim.transactionId = transactionId || claim.transactionId || null;
  claim.creditedAt = new Date().toISOString();
  store.applyTransaction(
    claim.guestId,
    db.settings.rewardCredits,
    "Rewarded ad"
  );
  store.save();
  return true;
}

app.post("/api/reward/claim", (req, res) => {
  const { guestId, claimId } = req.body || {};
  if (!guestId || !claimId) {
    return res.status(400).json({ error: "guestId and claimId required" });
  }
  let claim = db.claims.find((c) => c.claimId === claimId);
  if (!claim) {
    claim = {
      claimId,
      guestId,
      status: "PENDING",
      transactionId: null,
      createdAt: new Date().toISOString(),
      creditedAt: null,
    };
    db.claims.push(claim);
    store.save();
  }
  if (REWARD_DEV_MODE && claim.status === "PENDING") {
    // TESTING ONLY: credit immediately without SSV. Never enable in
    // production — rewards must be verified by Google's callback.
    creditClaim(claim, null);
  }
  res.json({
    status: claim.status,
    wallet: publicWallet(getOrCreateUser(guestId)),
  });
});

app.get("/api/reward/ssv", async (req, res) => {
  // AdMob expects HTTP 200; body "1" means the reward was accepted.
  try {
    const q = req.query;
    const timestamp = parseInt(q.timestamp, 10);
    if (!timestamp || Math.abs(Date.now() - timestamp) > 10 * 60 * 1000) {
      return res.status(400).send("0"); // Stale callback.
    }
    const keys = await fetchSsvKeys();
    if (!verifySsvSignature(q, keys)) {
      return res.status(403).send("0"); // Bad signature.
    }
    const guestId = q.user_id;
    const claimId = q.custom_data;
    const transactionId = q.transaction_id;
    if (!guestId || !claimId) return res.status(400).send("0");
    const claim = db.claims.find((c) => c.claimId === claimId);
    if (!claim) {
      // No pending claim for this custom_data — create one so the
      // credit is not lost, then credit it.
      const newClaim = {
        claimId,
        guestId,
        status: "PENDING",
        transactionId: null,
        createdAt: new Date().toISOString(),
        creditedAt: null,
      };
      db.claims.push(newClaim);
      creditClaim(newClaim, transactionId);
      store.save();
      return res.send("1");
    }
    creditClaim(claim, transactionId);
    store.save();
    res.send("1");
  } catch (e) {
    console.error("SSV error:", e.message);
    res.status(500).send("0");
  }
});

// ------------------------------------------------------------------
// Error reports (from the app — ad failures, etc.) and account delete
// ------------------------------------------------------------------

app.post("/api/error/report", (req, res) => {
  const { guestId, message, source } = req.body || {};
  if (!message) return res.status(400).json({ error: "message required" });
  db.errors.push({
    id: store.uid("err"),
    guestId: guestId || "anonymous",
    message: String(message).slice(0, 500),
    source: source || "app",
    createdAt: new Date().toISOString(),
  });
  // Keep the last 500 reports.
  if (db.errors.length > 500) db.errors = db.errors.slice(-500);
  store.save();
  res.json({ ok: true });
});

app.delete("/api/account/:guestId", (req, res) => {
  const guestId = req.params.guestId;
  delete db.users[guestId];
  db.transactions = db.transactions.filter((t) => t.guestId !== guestId);
  db.sessions = db.sessions.filter((s) => s.guestId !== guestId);
  db.claims = db.claims.filter((c) => c.guestId !== guestId);
  store.save();
  res.json({ ok: true });
});

// ------------------------------------------------------------------
// Public pages
// ------------------------------------------------------------------

app.get("/ads.txt", (req, res) => {
  // AdMob ads.txt declaration. This file is public by design.
  res.type("text/plain");
  res.send("google.com, pub-4510714397469784, DIRECT, f08c47fe\n");
});

app.get("/privacy-policy", (req, res) => {
  res.sendFile(path.join(__dirname, "public", "privacy-policy.html"));
});

app.get("/terms", (req, res) => {
  res.sendFile(path.join(__dirname, "public", "terms.html"));
});

app.get("/disclaimer", (req, res) => {
  res.sendFile(path.join(__dirname, "public", "disclaimer.html"));
});

app.get("/admin", (req, res) => {
  res.sendFile(path.join(__dirname, "public", "admin.html"));
});

// ------------------------------------------------------------------
// Admin API (token auth — token from POST /api/admin/login)
// ------------------------------------------------------------------

function adminToken() {
  const payload = JSON.stringify({
    admin: true,
    exp: Date.now() + 24 * 60 * 60 * 1000,
  });
  const sig = crypto.createHmac("sha256", TOKEN_SECRET).update(payload).digest("hex");
  return Buffer.from(payload).toString("base64") + "." + sig;
}

function verifyAdminToken(token) {
  try {
    const [payloadB64, sig] = String(token).split(".");
    const payload = Buffer.from(payloadB64, "base64").toString("utf8");
    const expected = crypto.createHmac("sha256", TOKEN_SECRET).update(payload).digest("hex");
    if (sig !== expected) return false;
    const data = JSON.parse(payload);
    return data.admin === true && data.exp > Date.now();
  } catch (e) {
    return false;
  }
}

function requireAdmin(req, res, next) {
  const token = req.headers["x-admin-token"];
  if (!token || !verifyAdminToken(token)) {
    return res.status(401).json({ error: "Admin authentication required" });
  }
  next();
}

app.post("/api/admin/login", (req, res) => {
  const { password } = req.body || {};
  if (!ADMIN_PASSWORD) {
    return res.status(500).json({
      error: "ADMIN_PASSWORD is not set on the server. Set it in the Render dashboard.",
    });
  }
  if (password !== ADMIN_PASSWORD) {
    return res.status(401).json({ error: "Wrong password" });
  }
  res.json({ token: adminToken() });
});

app.get("/api/admin/stats", requireAdmin, (req, res) => {
  const activeSessions = db.sessions.filter((s) => !s.endedAt).length;
  const totalCreditsIssued = db.transactions
    .filter((t) => t.amount > 0)
    .reduce((sum, t) => sum + t.amount, 0);
  const totalCreditsUsed = db.transactions
    .filter((t) => t.amount < 0)
    .reduce((sum, t) => sum + -t.amount, 0);
  res.json({
    users: Object.keys(db.users).length,
    servers: db.servers.length,
    serversOnline: db.servers.filter((s) => s.status === "online").length,
    activeSessions,
    totalCreditsIssued,
    totalCreditsUsed,
    pendingRewards: db.claims.filter((c) => c.status === "PENDING").length,
    errorReports: db.errors.length,
    peerProvisioningConfigured: wgProvision.isConfigured(),
  });
});

app.get("/api/admin/servers", requireAdmin, (req, res) => {
  res.json(db.servers);
});

app.post("/api/admin/servers", requireAdmin, (req, res) => {
  const { country, countryCode, city, host, port, publicKey, status, sshHost, sshUser, sshPort } = req.body || {};
  if (!country || !countryCode || !host || !port || !publicKey) {
    return res.status(400).json({
      error: "country, countryCode, host, port and publicKey are required",
    });
  }
  const server = {
    id: store.uid("srv"),
    country,
    countryCode: String(countryCode).toUpperCase().slice(0, 2),
    city: city || country,
    host,
    port: parseInt(port, 10),
    publicKey,
    status: status || "online",
    load: 0,
    // Optional SSH overrides for peer provisioning (wg-provision.js).
    // When omitted, provisioning SSHes to `host` as WG_SSH_USER.
    sshHost: sshHost || null,
    sshUser: sshUser || null,
    sshPort: sshPort ? parseInt(sshPort, 10) : null,
    createdAt: new Date().toISOString(),
  };
  db.servers.push(server);
  store.save();
  res.json(server);
});

app.put("/api/admin/servers/:id", requireAdmin, (req, res) => {
  const server = db.servers.find((s) => s.id === req.params.id);
  if (!server) return res.status(404).json({ error: "Server not found" });
  const fields = ["country", "countryCode", "city", "host", "port", "publicKey", "status", "load", "sshHost", "sshUser", "sshPort"];
  for (const f of fields) {
    if (req.body[f] !== undefined) server[f] = req.body[f];
  }
  store.save();
  res.json(server);
});

app.delete("/api/admin/servers/:id", requireAdmin, (req, res) => {
  db.servers = db.servers.filter((s) => s.id !== req.params.id);
  store.save();
  res.json({ ok: true });
});

/** Manual health check: measures real TCP latency to a server (honest ping). */
app.post("/api/admin/servers/:id/ping", requireAdmin, async (req, res) => {
  const server = db.servers.find((s) => s.id === req.params.id);
  if (!server) return res.status(404).json({ error: "Server not found" });
  const start = Date.now();
  const ok = await new Promise((resolve) => {
    const socket = net.createConnection(
      { host: server.host, port: server.port, timeout: 3000 },
      () => {
        socket.destroy();
        resolve(true);
      }
    );
    socket.on("timeout", () => {
      socket.destroy();
      resolve(false);
    });
    // Only ECONNREFUSED ("connection refused") means the host
    // answered: a TCP SYN to a UDP WireGuard port gets an RST, and
    // the time until that RST is the real latency. Any other error
    // (DNS failure, network unreachable, timeout) means the server
    // did NOT answer — it must not be reported as reachable.
    socket.on("error", (err) => resolve(err && err.code === "ECONNREFUSED"));
  });
  const ms = Date.now() - start;
  res.json({ reachable: ok, pingMs: ok ? ms : null });
});

app.get("/api/admin/users", requireAdmin, (req, res) => {
  const users = Object.values(db.users)
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, 200)
    .map((u) => publicWallet(u));
  res.json(users);
});

app.get("/api/admin/rewards", requireAdmin, (req, res) => {
  const list = [...db.claims]
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, 200);
  res.json(list);
});

app.get("/api/admin/errors", requireAdmin, (req, res) => {
  const list = [...db.errors]
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, 200);
  res.json(list);
});

app.get("/api/admin/settings", requireAdmin, (req, res) => {
  res.json(db.settings);
});

app.put("/api/admin/settings", requireAdmin, (req, res) => {
  const allowed = [
    "welcomeCredits",
    "rewardCredits",
    "creditsPer30Min",
    "lowCreditThreshold",
    "maintenanceMode",
    "supportEmail",
  ];
  for (const key of allowed) {
    if (req.body[key] !== undefined) db.settings[key] = req.body[key];
  }
  store.save();
  res.json(db.settings);
});

// ------------------------------------------------------------------
// Stale-session sweeper
// ------------------------------------------------------------------
// Sessions whose app died without an /end call (or lost network)
// stop heartbeating. Every 5 minutes, end any session with no
// heartbeat for 10 minutes, bill its completed blocks, and remove
// its peer from the WireGuard server so the address is freed.
setInterval(() => {
  const now = Date.now();
  let changed = false;
  for (const s of db.sessions.filter((x) => !x.endedAt)) {
    const last = new Date(s.lastHeartbeatAt || s.startedAt).getTime();
    if (now - last > 10 * 60 * 1000) {
      billSession(s);
      s.endedAt = new Date().toISOString();
      changed = true;
      const server = db.servers.find((x) => x.id === s.serverId);
      if (server) {
        wgProvision.removePeer(server, s.clientPublicKey).catch(() => {});
      }
      console.log(`[sweeper] ended stale session ${s.id}`);
    }
  }
  if (changed) store.save();
}, 5 * 60 * 1000);

// ------------------------------------------------------------------
// Start
// ------------------------------------------------------------------

app.listen(PORT, () => {
  console.log(`Global VPN backend listening on port ${PORT}`);
  console.log(`Servers registered: ${db.servers.length}`);
  console.log(`Users: ${Object.keys(db.users).length}`);
  if (!ADMIN_PASSWORD) {
    console.log("WARNING: ADMIN_PASSWORD is not set — admin login is disabled.");
  }
  if (!wgProvision.isConfigured()) {
    console.log(
      "WARNING: WG_SSH_PRIVATE_KEY is not set — WireGuard peers are NOT provisioned, so VPN traffic cannot flow. See wg-provision.js."
    );
  }
  if (REWARD_DEV_MODE) {
    console.log("WARNING: REWARD_DEV_MODE is ON — rewards are credited without SSV. Disable in production.");
  }
});
