#!/usr/bin/env bash
# Runs every automated check using only Docker. You do not need a JDK, Maven, Node, or a browser on the host.
#   - Java tests run in a Maven container. Testcontainers starts PostgreSQL on the host's Docker daemon.
#   - Dashboard checks and the browser test run in the official Playwright image, which includes Chromium.
# Requires Docker running. Files written by the containers (target/, node_modules/) are owned by root.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v1.56.0-noble"
MAVEN_IMAGE="maven:3.9-eclipse-temurin-21"

step() { printf '\n==> %s\n' "$1"; }

step "Java tests (27) in $MAVEN_IMAGE"
# certs/*.crt (corporate proxies) are imported into the container's Java trust store first.
docker run --rm --network host \
  -v "$ROOT:/work" -w /work \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=localhost \
  "$MAVEN_IMAGE" bash -c '
    for f in /work/certs/*.crt; do
      [ -f "$f" ] && keytool -importcert -noprompt -cacerts -storepass changeit \
        -alias "$(basename "$f" .crt)" -file "$f" > /dev/null 2>&1 || true
    done
    mvn -q -B test'


step "Dashboard: type check, unit tests, build, browser test in $PLAYWRIGHT_IMAGE"
docker run --rm --ipc=host \
  -v "$ROOT:/work" -w /work/frontend \
  "$PLAYWRIGHT_IMAGE" \
  bash -c 'touch /tmp/extra.pem; cat /work/certs/*.crt >> /tmp/extra.pem 2>/dev/null || true;
           export NODE_EXTRA_CA_CERTS=/tmp/extra.pem;
           npm ci --no-audit --no-fund && npm run typecheck && npm test && npm run build && npm run e2e' 

printf '\nAll checks passed (Docker only).\n'
