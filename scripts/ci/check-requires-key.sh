#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

set +e
timeout 120 docker run --rm \
  -e SPOTIFY_CLIENT_ID=ci -e SPOTIFY_CLIENT_SECRET=ci \
  spotify-reblender:ci > no-key.log 2>&1
status=$?
set -e

tail -n 20 no-key.log
if [ "$status" -eq 0 ] || [ "$status" -eq 124 ]; then
  fail "expected the app to exit with an error when TOKEN_ENCRYPTION_KEY is missing (status $status)"
fi
grep -q "TOKEN_ENCRYPTION_KEY" no-key.log || fail "startup error doesn't mention TOKEN_ENCRYPTION_KEY"
