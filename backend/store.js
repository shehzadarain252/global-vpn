/**
 * File-based data store for the Global VPN backend.
 *
 * A single JSON file (data/db.json) holds: users (wallets + transactions),
 * servers (WireGuard registry), sessions, reward claims, error reports and
 * app settings. Writes are atomic (temp file + rename) so a crash cannot
 * corrupt the database, and all wallet mutations go through this module
 * so balances are never lost to a race.
 *
 * This is deliberately zero-dependency so the backend deploys on the
 * Render free tier with no external database. For larger scale, swap
 * this module for Postgres (see README.md) — the server.js API does not
 * change.
 */
const fs = require("fs");
const path = require("path");

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "data");
const DB_FILE = path.join(DATA_DIR, "db.json");

const DEFAULT_SETTINGS = {
  welcomeCredits: 100,
  rewardCredits: 20,
  creditsPer30Min: 10,
  lowCreditThreshold: 20,
  maintenanceMode: false,
  supportEmail: "support@globalvpn.app",
};

function defaultDb() {
  return {
    users: {}, // guestId -> { guestId, balance, totalEarned, totalUsed, welcomeClaimed, createdAt }
    transactions: [], // { id, guestId, amount, reason, createdAt }
    servers: [], // { id, country, countryCode, city, host, port, publicKey, status, load, createdAt }
    sessions: [], // { id, guestId, serverId, clientAddress, clientPublicKey, startedAt, lastHeartbeatAt, billedBlocks, endedAt, bytesRx, bytesTx }
    claims: [], // { claimId, guestId, status, transactionId, createdAt, creditedAt }
    errors: [], // { id, guestId, message, source, createdAt }
    settings: { ...DEFAULT_SETTINGS },
  };
}

let db = null;

function load() {
  try {
    fs.mkdirSync(DATA_DIR, { recursive: true });
    if (fs.existsSync(DB_FILE)) {
      db = JSON.parse(fs.readFileSync(DB_FILE, "utf8"));
    } else {
      db = defaultDb();
      save();
    }
  } catch (e) {
    console.error("store: failed to load db, starting fresh:", e.message);
    db = defaultDb();
  }
  // Fill in any settings added in newer versions.
  db.settings = { ...DEFAULT_SETTINGS, ...(db.settings || {}) };
  return db;
}

function save() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  const tmp = DB_FILE + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(db, null, 2));
  fs.renameSync(tmp, DB_FILE);
}

function getDb() {
  if (!db) load();
  return db;
}

function uid(prefix) {
  return prefix + "_" + Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

/** Adds a credit transaction and updates the wallet balance atomically. */
function applyTransaction(guestId, amount, reason) {
  const d = getDb();
  let user = d.users[guestId];
  if (!user) {
    user = {
      guestId,
      balance: 0,
      totalEarned: 0,
      totalUsed: 0,
      welcomeClaimed: false,
      createdAt: new Date().toISOString(),
    };
    d.users[guestId] = user;
  }
  user.balance += amount;
  if (amount > 0) user.totalEarned += amount;
  if (amount < 0) user.totalUsed += -amount;
  const type = reason.startsWith("Welcome")
    ? "WELCOME"
    : reason.startsWith("Rewarded")
      ? "REWARD"
      : reason.startsWith("VPN use")
        ? "USAGE"
        : "ADMIN";
  const tx = {
    id: uid("tx"),
    guestId,
    type,
    amount,
    reason,
    createdAt: new Date().toISOString(),
  };
  d.transactions.push(tx);
  save();
  return { user, tx };
}

module.exports = { load, save, getDb, uid, applyTransaction, DEFAULT_SETTINGS };
