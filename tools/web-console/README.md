# SurvivalOps Web (parallel to the Windows MSI)

The original Java automation engine, task registry and scheduler run on the `webserver` host in a separate workspace and checkout. The web UI is an authenticated HTTPS Python gateway, while Java owns the local-only API (`127.0.0.1:18765`). Only Serve-Emu is supported.

## Addresses

- Home LAN: `https://192.168.1.20:9098/` (TrueNAS reverse TCP proxy -> SSH remote tunnel -> webserver)
- Webserver network: `https://10.0.0.100:9098/`
- Internal Java API: `127.0.0.1:18765` (never expose publicly)
- TrueNAS Serve-Emu UI: `https://192.168.1.20:8448/`

The app uses a self-signed TLS certificate covering both IP addresses (stored only in `~/.config/survivalops-web`). Never copy the TLS private key or password file into Git.

## Deployment

Webserver user services:

- `survivalops-web-engine.service`: Java bot
- `survivalops-web.service`: authenticated HTTPS frontend
- `survivalops-web-lan-tunnel.service`: SSH reverse tunnel to TrueNAS using an existing authorized deployment key, remote bound to `127.0.0.1:19098`

TrueNAS runs the separate Docker container `survivalops-web-lan-proxy` with host networking, bound only to `192.168.1.20:9098`. It forwards TCP/TLS to the SSH tunnel and is set to restart automatically. It does **not** modify the existing Serve-Emu application.

Access password is initially provisioned under `~/.config/survivalops-web/initial-password.txt`, and may be changed in the Web Settings page. Password hash, TLS key, and session signing key must never be committed. The QA dashboard remains available separately.

## Current validation and limitations

- Web API, browser assets, task registry, profile persistence, frontend login and local Serve-Emu screenshot read have been tested.
- Browser UI exposes 63 Java task enum entries and 281 engine settings; the historical QA archive tracks 62 active/triaged routines (different counts, not identical universes).
- Web Task Builder supports point tap, swipe, wait, back and simple linear flows. Advanced FXML editor and MSI-only features have **not** been ported.
- The web engine is **stopped by default**, with no tasks enabled. No claim of complete live task-by-task E2E verification; use shared ADB maintenance lock and coordinate with the Windows MSI before enabling.
- The gateway temporarily blocks web controls when it detects recent external Task Coach evidence; this is a conservative signal, not a guaranteed global lease.

Avoid running the Windows client and Web scheduler against the same emulator simultaneously.
