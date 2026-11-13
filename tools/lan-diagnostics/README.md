# Private LAN diagnostics receiver

Runs as a **user systemd service** on the user's private webserver. The live service is
bound only to 10.0.0.100:18761 and uses HTTPS. No reverse proxy is configured.

Private deployment directory: `/home/michel/survivalops-diagnostics`.

Keep these files out of Git:
- `private/server-key.pem`
- `private/upload-token`
- `private/server-cert.pem` (public certificate but pinned by fingerprint in the app)
- `inbox/*`

The certificate is self-signed and pinned by its SHA-256 leaf fingerprint in
`LanDiagnosticReporter`. Rotating it requires an app update. The token is generated
separately on the server. The Windows user enters it once in the diagnostics setting.
No production secret is checked into source control.

Service: `systemctl --user status survivalops-diagnostics.service`.
Logs: `journalctl --user -u survivalops-diagnostics.service`.
Inbox: `/home/michel/survivalops-diagnostics/inbox/YYYY-MM-DD/*.json`.

The server enforces a 5 MiB request cap, 150 reports per day, 14-day age expiry
(on incoming requests), Bearer authentication and private file permissions.
Don't add the inbox to the public nginx document root.

LAN access: `https://10.0.0.100:18761/health` is also authenticated.
On Windows, check that routing/firewall permits this address and port.

## Pairing the Windows app

In SurvivalOps > Emulator Settings > SurvivalOps LAN Diagnostics:
1. Retrieve the private token on the webserver using an authenticated SSH session:
   `ssh michel@10.0.0.100 'cat ~/survivalops-diagnostics/private/upload-token'`
2. Paste it in the password field; it is saved in the local Windows app settings,
   not embedded in release assets.
3. Use the connection test. Enable diagnostic sharing and optionally screenshot
   sharing (screenshots can show player names and chat). The existing local snapshot
   capture setting is enabled automatically when LAN diagnostics is enabled.

No upload is attempted until sharing is enabled and the token is filled in.
The ZIP export remains a separate, manual option.

## Inspect incoming reports

`find ~/survivalops-diagnostics/inbox -type f -name '*.json' -printf '%T@ %p\\n' | sort -nr | head`

Each JSON stores the task identifier, failure type, recent warning/error lines and
(optional) a separate PNG path. The server is not exposed by nginx and the inbox
files are readable only to the SSH user. Remove private data before publishing
summaries on GitHub. The inbox retention runs as new uploads arrive.
