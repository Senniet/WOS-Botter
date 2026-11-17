# SurvivalOps Task Coach evidence on webserver

Task Coach can now upload a bounded, password-protected evidence bundle to the
**existing** private LAN diagnostics receiver. No OpenAI API or background AI
service is involved. Each uploaded session is stored on the webserver in:

```
/home/michel/survivalops-diagnostics/taskcoach/YYYY-MM-DD/<task-coach-session-id>/
  summary.txt
  recognition-events.csv
  run.txt                 (when present)
  upload-manifest.json
  captures/               (full-resolution JPEG copies if screenshot sharing enabled)
  proposals/              (if screenshot sharing enabled)
  receipt.json
```

## Client settings and operation

The existing SurvivalOps LAN Diagnostics enabled setting and upload token are
required, using the already pinned TLS certificate. **Screenshots and proposed
crops are only transmitted when the existing Share screenshots setting is
on**. Without it, `summary.txt` and `recognition-events.csv` are transmitted
as text, but the images remain local. No API key or account database is sent.

At the end of a Task Coach run, evidence upload starts asynchronously. The
status appears in Debugging > Task Coach. Click **Upload evidence** to retry or
to send the most recently completed run after restarting the app. The UI
shows `UPLOADED`, `NOT_SHARED` or `UPLOAD_FAILED` without disrupting the game.

The archive prioritizes candidate templates. Candidate PNGs remain lossless;
full-resolution captures are encoded as JPEG (quality 90) to fit the whole
run, including the final claim screenshots, without cropping. Local original
PNGs are never changed. The upload is bounded at 90 images and 25 MiB of image
data and includes an explicit count of images that did not fit. Reports and recognition CSV remain complete. Local originals
are never deleted.

## HTTPS server deployment

The repository copies of `server.py` and `taskcoach_receiver.py` must be
installed together under `/home/michel/survivalops-diagnostics/`, using the
existing `survivalops-diagnostics.service` service. Never copy private token,
certificate or database files to the Git repository. The receiver listens at:

```
POST https://10.0.0.100:18761/api/taskcoach/evidence
Authorization: Bearer <existing LAN diagnostics token>
Content-Type: application/zip
X-SurvivalOps-Session: YYYYMMDD-HHMMSS-<id>
```

The endpoint checks the existing token, rejects unexpected ZIP entries,
file-size violations and path traversal, accepts identical repeat uploads, and
never executes uploaded contents. The private evidence directory is not a
public HTTP file server. Server retention is 30 days. Examine runs over SSH;
for example:

```sh
find ~/survivalops-diagnostics/taskcoach -name summary.txt -print | sort
```

## Tests

```sh
python3 -m unittest discover -s tools/taskcoach-evidence -p 'test_*.py' -v
./mvnw -pl modules/automation -am -Dtest=TaskCoachEvidenceBundleTest -Dsurefire.failIfNoSpecifiedTests=false test
```
