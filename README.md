# GLOBAL VPN — Your Privacy. Your Connection. Your World.

A **100% free** Android VPN app built on **WireGuard**, with a credit wallet, AdMob ads and a Node.js backend + web admin panel.

> **Free, forever.** There is no premium plan and no in-app purchases. Everyone sees ads. The app is funded by AdMob ads and by the credit system.

## Features

- **WireGuard VPN** — the fastest modern VPN protocol. Real tunnels via the `wireguard-android` library, `VpnService`, DNS + IPv6 leak protection, kill switch (Android "Always-on VPN" + "Block connections without VPN"), auto-reconnect.
- **Fastest-server auto-selection** — the app measures **real ping** to every server and auto-selects the lowest-ping one by default. The server list shows each server's real ping, backend-reported load and last measured speed, so the user can pick the fastest manually.
- **Credit wallet** — 100 welcome credits, 20 credits per verified rewarded ad, 10 credits per 30 minutes of VPN use. All values are configurable in the admin panel.
- **The credit loop** — use VPN → credits decrease → low-credit warning → at zero the VPN disconnects safely and a prominent **"Watch Ad & Earn Credits"** option appears → watch a rewarded ad → reward verified server-side → 20 credits added → connect again. The "Watch Ad & Earn" button is always visible on Home and Wallet, so the user is never stuck at zero credits.
- **Ads (AdMob, live)** — interstitial ad before Connect and before Disconnect (if no ad is loaded, the action runs immediately — ads never block the VPN); banner ads while disconnected, including on app open; rewarded ads for credits. Everyone sees ads — there is no ad-free tier.
- **Firebase Analytics + Crashlytics** — anonymous usage (user counts, DAU/MAU, retention) and crash reports in the Firebase console. No personal data is logged.
- **16 screens** — Splash, Onboarding, Home, Server Locations, Wallet, Earn Credits, Credit History, Settings, Privacy Policy, Terms & Conditions, Disclaimer, About, Contact, FAQs, Help & Support, Delete Account, Connection History, App Info. Bottom navigation only, no sidebar.
- **Backend + admin panel** — credit wallets, sessions, reward verification (AdMob SSV), server registry, error reports, maintenance mode. See `backend/README.md`.

## Honesty — read this first

- **No servers exist yet.** The app shows an honest **"No servers configured yet — add servers in the admin panel"** state and never pretends to connect. A country appears in the list only when a real, operational WireGuard server is registered in the backend.
- **No fake speeds, pings or connections.** Ping is measured live (TCP connect timing to the server). Speed comes from real tunnel statistics and past-session data. Nothing is invented.
- **Real speed depends on the server's location, bandwidth and load, and on the user's own connection.** Results will vary.
- **A VPN changes your IP address and apparent country. It does NOT change your GPS location or your SIM location**, and no VPN can promise complete anonymity.

## Project structure

```
global-vpn/
├── app/                          Android app (Kotlin + Jetpack Compose)
│   ├── src/main/java/com/globalvpn/
│   │   ├── MainActivity.kt       Single activity, VPN permission flow
│   │   ├── GlobalVpnApp.kt      Application: Firebase, AdMob, singletons
│   │   ├── VpnViewModel.kt      Credit loop + ad-first connect/disconnect
│   │   ├── ads/AdMobManager.kt  Interstitial / rewarded / banner (live IDs in code only)
│   │   ├── analytics/Analytics.kt  Firebase Analytics + Crashlytics, no PII
│   │   ├── data/                 Models, Retrofit API, repository, DataStore prefs
│   │   ├── vpn/                  WireGuardManager, GlobalVpnService, PingMeasurer, BootReceiver
│   │   └── ui/                   Theme, navigation, 16 screens
│   ├── src/main/res/             Strings, theme, launcher icons (from the app icon)
│   └── google-services.json.example   Placeholder — see Firebase setup below
├── backend/                      Node.js / Express backend + admin panel
│   ├── server.js                 API + SSV verification + admin API
│   ├── store.js                  File-based data store (db.json)
│   ├── public/                   Admin panel, privacy policy, terms, disclaimer, ads.txt
│   └── README.md                 Deploy + Oracle server setup
└── .github/workflows/android.yml GitHub Actions — builds the debug APK in the cloud
```

## Tech stack

- Kotlin + Jetpack Compose (Material 3), AGP 8.5.2, Kotlin 1.9.24, minSdk 26 / targetSdk 34
- WireGuard `com.wireguard.android:tunnel:1.0.20260102`, GoBackend
- Google Mobile Ads SDK 23.0.0 + UMP 2.2.0
- Firebase BoM 34.8.0 (Analytics + Crashlytics)
- Retrofit 2.11.0, DataStore 1.1.1, Gson
- Backend: Node.js 18+ / Express 4, file-based JSON store (no external DB needed)

## Setup — Android app

1. Clone the repo and open it in Android Studio.
2. **Firebase (required before building):** create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com), add an Android app with package name `com.globalvpn`, download its `google-services.json` and place it in `app/`. A placeholder file `app/google-services.json.example` shows the expected structure — **do not invent project IDs or API keys; use the real file from your Firebase project.** `google-services.json` is git-ignored and is never committed.
3. Build and run. The app talks to the backend URL in `app/build.gradle.kts` (`BACKEND_URL`, default `http://10.0.2.2:3000` for the local emulator — set it to your deployed Render URL for a real device).

## Setup — backend

See `backend/README.md`. Short version: deploy the `backend/` folder on Render (free tier) with `ADMIN_PASSWORD` set, then open `/admin` and add real WireGuard servers.

## AdMob

- The AdMob App ID is configured in `app/src/main/AndroidManifest.xml` (Option 1). **The user must confirm which App ID is registered to Global VPN — a second candidate exists and must never be configured at the same time.**
- Interstitial + rewarded ad unit IDs live only in `app/src/main/java/com/globalvpn/ads/AdMobManager.kt`.
- **Banner Ad Unit ID is pending** (the developer will provide it). The placeholder constant `BANNER_AD_UNIT_ID = ""` in `AdMobManager.kt` has a TODO; the banner UI activates automatically once the ID is set.
- `ads.txt` is served by the backend at `/ads.txt`.
- **AdMob IDs are never written in docs, README files, memory notes or any other file — only in app code and in the backend-served `ads.txt`.**

## Firebase

- Firebase Analytics + Crashlytics are initialized in `GlobalVpnApp` (with a graceful fallback if `google-services.json` is missing, so the app still runs).
- Events logged (no personal data): `app_open`, `vpn_connect`, `vpn_disconnect`, `server_country_selected`, `rewarded_ad_earned`, `credits_earned`. User counts (DAU/MAU), retention and crashes appear in the Firebase console.
- The Privacy Policy discloses Firebase Analytics + Crashlytics honestly (anonymous usage + crash reports, no personal data).

## Speed — how fastest-server selection works

1. On load, the app measures real ping to every online server (TCP connect timing, 2 attempts, fastest kept).
2. The lowest-ping online server is auto-selected (lowest latency naturally prefers geographically closer servers; backend-reported load breaks ties).
3. The server list shows measured ping, load and last measured speed per server.
4. Tunnel efficiency: MTU 1280 (avoids fragmentation on mobile networks), `PersistentKeepalive = 25` (minimal keepalive traffic), heartbeats every 60 s only while connected, stats polling is local only.

## What is still needed (not done by this build)

1. **Real WireGuard servers** — the owner plans 2 free servers on Oracle Cloud (Always Free tier). Steps are in `backend/README.md`. The backend assigns client addresses and keys; the Oracle server must be configured to accept those peers.
2. **AdMob App ID confirmation** — confirm which of the two candidate App IDs is registered to Global VPN.
3. **Banner Ad Unit ID** — pending from the developer; set `BANNER_AD_UNIT_ID` in `AdMobManager.kt`.
4. **Firebase project + `google-services.json`** — create the project, place the real file in `app/` before building.
5. **Backend deployment** — deploy `backend/` to Render (free tier) with `ADMIN_PASSWORD` set; point `BACKEND_URL` in `app/build.gradle.kts` at the live URL.
6. **AdMob SSV callback URL** — in the AdMob console, set the rewarded ad unit's server-side verification callback to `https://<your-render-host>/api/reward/ssv`.
