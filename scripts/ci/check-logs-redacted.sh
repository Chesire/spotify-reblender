#!/usr/bin/env bash
# Needs the app started by smoke-test.sh. Sends secrets the app sees and checks none of them are logged.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

base=http://127.0.0.1:8080
curl -s -o /dev/null "$base/login/oauth2/code/spotify?code=SECRETCODE123&state=SECRETSTATE456"
challenge=$(curl -s -o /dev/null -w '%{redirect_url}' "$base/oauth2/authorization/spotify" \
  | sed -n 's/.*code_challenge=\([^&]*\).*/\1/p')
session=$(curl -s -D - -o /dev/null "$base/oauth2/authorization/spotify" \
  | sed -n 's/^[Ss]et-[Cc]ookie: JSESSIONID=\([^;]*\).*/\1/p')
sleep 1

docker logs reblender > app.log 2>&1
grep "reblender.http.in" app.log | tail -n 20
grep -q "IN  GET / -> 200" app.log || fail "home page request not logged"
grep -q "IN  GET /oauth2/authorization/spotify -> 302" app.log || fail "login redirect not logged"
grep -q "IN  GET /login/oauth2/code/spotify?code=\*\*\*&state=\*\*\*" app.log || fail "OAuth callback not logged masked"
for secret in SECRETCODE123 SECRETSTATE456 "$challenge" "$session"; do
  [ -n "$secret" ] || fail "couldn't capture a secret value to check for"
  if grep -qF "$secret" app.log; then fail "a secret value was found in the log"; fi
done
