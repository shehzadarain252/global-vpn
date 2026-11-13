/**
 * WireGuard peer provisioning over SSH.
 *
 * THE PROBLEM IT SOLVES: /api/session/start used to record a session
 * and hand the app a client address + the client's public key — but
 * nothing ever told the real WireGuard server about that peer. A
 * WireGuard server drops all packets from unknown peers, so the
 * tunnel would come "UP" in the app while no traffic could flow.
 *
 * HOW IT WORKS: when a session starts, the backend SSHes into the
 * WireGuard VM and runs `wg set wg0 peer <pubkey> allowed-ips
 * <addr>/32`. When the session ends (or goes stale) the peer is
 * removed the same way. No agent software is needed on the VM —
 * only WireGuard + SSH + passwordless sudo (standard on Ubuntu
 * cloud images).
 *
 * SETUP (Render env vars):
 *   WG_SSH_PRIVATE_KEY  the VM user's SSH private key (PEM text).
 *                       REQUIRED for provisioning; without it the
 *                       backend still runs but peers are never added
 *                       (see isConfigured() + /api/health flag).
 *   WG_SSH_USER         SSH username (default "ubuntu").
 *   WG_SSH_PORT         SSH port (default 22).
 *   WG_INTERFACE        WireGuard interface (default "wg0").
 * Per-server overrides: a server record may carry sshHost / sshUser /
 * sshPort (admin panel) — useful when VMs live in different places.
 * If sshHost is not set, the server's VPN host is used for SSH.
 *
 * SAFETY: every failure is logged and returned, never thrown — a
 * provisioning failure must never crash a session request. The
 * session records peerProvisioned so the admin panel can see it.
 */
const { Client } = require("ssh2");

const WG_SSH_PRIVATE_KEY = process.env.WG_SSH_PRIVATE_KEY || "";
const WG_SSH_USER = process.env.WG_SSH_USER || "ubuntu";
const WG_SSH_PORT = parseInt(process.env.WG_SSH_PORT || "22", 10);
const WG_INTERFACE = process.env.WG_INTERFACE || "wg0";
const SSH_TIMEOUT_MS = 15_000;

function isConfigured() {
  return WG_SSH_PRIVATE_KEY.length > 0;
}

function sshTarget(server) {
  return {
    host: server.sshHost || server.host,
    port: server.sshPort || WG_SSH_PORT,
    username: server.sshUser || WG_SSH_USER,
    privateKey: WG_SSH_PRIVATE_KEY,
  };
}

function runSshCommand(server, command) {
  return new Promise((resolve) => {
    if (!isConfigured()) {
      return resolve({ ok: false, reason: "ssh-not-configured" });
    }
    const conn = new Client();
    let settled = false;
    const done = (result) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try {
        conn.end();
      } catch (e) {
        /* ignore */
      }
      resolve(result);
    };
    const timer = setTimeout(
      () => done({ ok: false, reason: "ssh-timeout" }),
      SSH_TIMEOUT_MS
    );
    conn
      .on("ready", () => {
        conn.exec(command, (err, stream) => {
          if (err) return done({ ok: false, reason: String(err.message || err) });
          let stderr = "";
          stream.on("data", () => {});
          stream.stderr.on("data", (d) => {
            stderr += d.toString();
          });
          stream.on("close", (code) => {
            if (code === 0) done({ ok: true });
            else done({ ok: false, reason: stderr.trim() || `exit ${code}` });
          });
        });
      })
      .on("error", (err) => done({ ok: false, reason: String(err.message || err) }))
      .connect({ ...sshTarget(server), readyTimeout: 10_000 });
  });
}

/**
 * Adds a client peer to the server's WireGuard interface.
 * clientAddress is "10.8.0.N/32" — only the /32 host part is used.
 */
async function provisionPeer(server, clientPublicKey, clientAddress) {
  const addr = String(clientAddress).split("/")[0];
  const cmd = `sudo wg set ${WG_INTERFACE} peer ${clientPublicKey} allowed-ips ${addr}/32`;
  const result = await runSshCommand(server, cmd);
  if (!result.ok) {
    console.warn(
      `[wg-provision] add peer failed (${server.host}): ${result.reason}`
    );
  }
  return result;
}

/** Removes a client peer from the server's WireGuard interface. */
async function removePeer(server, clientPublicKey) {
  const cmd = `sudo wg set ${WG_INTERFACE} peer ${clientPublicKey} remove`;
  const result = await runSshCommand(server, cmd);
  if (!result.ok) {
    console.warn(
      `[wg-provision] remove peer failed (${server.host}): ${result.reason}`
    );
  }
  return result;
}

module.exports = { isConfigured, provisionPeer, removePeer };
