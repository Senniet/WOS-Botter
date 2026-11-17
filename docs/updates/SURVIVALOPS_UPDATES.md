# SurvivalOps signed updates

SurvivalOps uses a dedicated Ed25519 trust root and the `survivalops-stable` GitHub Release as its update feed. It does not read the Frostguard update key or feed. The signed manifest points to an immutable, version-tagged MSI release (`survivalops-vX.Y.Z`). The installer is checked against the signed manifest's SHA-256 and size before Windows handoff.

## One-time release signing setup

1. On a trusted admin machine, build the `modules/update` module and run `java -cp modules/update/target/classes dev.frostguard.update.ProjectManifestSigner generate survivalops-private-key-base64.txt survivalops-public-key-base64.txt`. This creates an Ed25519 key pair in the format expected by the release signer. Store the private file securely and never commit it.
2. Set `keyId=survivalops-update-2026-01` and copy the contents of `survivalops-public-key-base64.txt` into `publicKey=` in `modules/update/src/main/resources/dev/frostguard/update/survivalops-update-key.properties`.
3. Add the Base64 private key as the GitHub Actions repository secret `SURVIVALOPS_UPDATE_SIGNING_PRIVATE_KEY_BASE64`.
4. Protect the signing secret and release workflow with repository/environment permissions. Only trusted maintainers may publish `survivalops-v*` tags or run the release workflow.
5. Run the `SurvivalOps — Signed Windows Release` workflow with a version (or push a `survivalops-vX.Y.Z` tag). It builds the bundled-runtime MSI, creates the immutable version release, signs the manifest, and updates the `survivalops-stable` feed release.

The public key is intentionally left blank in source until the dedicated key pair is provisioned. In that state, the updater fails closed and displays that updates are not configured; it will not fall back to Frostguard's key.

## Runtime behaviour

- The update page checks after startup and every six hours.
- A newer signed release is offered with **Nu installeren** and **Later**.
- Download and SHA-256 verification happen only after the user confirms.
- Before handoff, SurvivalOps pauses queue dispatch, lets any currently executing routine finish, and only then shuts down and installs. If the task state cannot be determined, installation is held.
- The MSI uses the stable SurvivalOps upgrade UUID and app identity, and the user's `.survivalops` workspace is not removed.
