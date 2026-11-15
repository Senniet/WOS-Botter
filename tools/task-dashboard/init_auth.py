#!/usr/bin/env python3
"""Generate a one-time local operator password; never commit the private auth file."""
from pathlib import Path
import os
import json
import secrets
import hashlib
import base64
directory=Path.home()/".config/survivalops-qa-dashboard"
directory.mkdir(mode=0o700,parents=True,exist_ok=True)
os.chmod(directory,0o700)
target=directory/"auth.json"
if target.exists():
    print("Auth exists; refusing to overwrite")
    raise SystemExit(0)
password=secrets.token_urlsafe(15)
salt=secrets.token_bytes(16)
data={"salt":salt.hex(),"password_hash":hashlib.pbkdf2_hmac("sha256",password.encode(),salt,260000).hex(),
      "session_key":base64.urlsafe_b64encode(secrets.token_bytes(32)).decode()}
old_umask=os.umask(0o077)
try:
 target.write_text(json.dumps(data)+"\n")
 (directory/"initial-password.txt").write_text(password+"\n")
finally:os.umask(old_umask)
target.chmod(0o600)
(directory/"initial-password.txt").chmod(0o600)
print("Initialized credentials in",target,"and a private password file.")
