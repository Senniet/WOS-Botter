#!/usr/bin/env bash
set -euo pipefail
cd /home/michel/projects/WOS-Botter-Web
mkdir -p /home/michel/.cache/survivalops-web/tmp /home/michel/.survivalops/workspaces/stable/web
chmod 700 /home/michel/.survivalops /home/michel/.survivalops/workspaces /home/michel/.survivalops/workspaces/stable /home/michel/.survivalops/workspaces/stable/web 2>/dev/null || true
CP="$(cat /home/michel/.cache/survivalops-web-classpath.txt)"
# Source output is prepended to use the matching tested worktree and unchanged Java tasks.
for module in update watcher tasks automation vision persistence api desktop; do
  CP="/home/michel/projects/WOS-Botter-Web/modules/$module/target/classes:$CP"
done
exec /usr/bin/java -Xms256m -Xmx2g -Djava.awt.headless=true \
  -Djava.io.tmpdir=/home/michel/.cache/survivalops-web/tmp \
  -Djava.library.path=/usr/lib/x86_64-linux-gnu \
  -Dfrostguard.channel=stable \
  -Dfrostguard.brand.name=SurvivalOps \
  -Dfrostguard.workspace=/home/michel/.survivalops/workspaces/stable/web \
  -Dfrostguard.adb.path=/home/michel/.local/share/WsScrcpyWeb/dependencies/adb/adb \
  -Dsurvivalops.web.port=18765 \
  -cp "$CP" dev.frostguard.app.bootstrap.Main --headless --web
