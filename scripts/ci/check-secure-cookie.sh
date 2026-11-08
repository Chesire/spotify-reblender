#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

run_app reblender-https 8082 \
  -e TOKEN_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
  -e APP_BASE_URL=https://reblender.example.com \
  -e DATABASE_URL=jdbc:h2:mem:ci
trap 'docker logs reblender-https 2>&1 | tail -n 20; docker rm -f reblender-https > /dev/null' EXIT
wait_for_http http://127.0.0.1:8082/

curl -s -D cookie-https.txt -o /dev/null http://127.0.0.1:8082/oauth2/authorization/spotify
cat cookie-https.txt
cookie=$(grep -i "^set-cookie: JSESSIONID" cookie-https.txt || true)
[[ ${cookie,,} == *secure* ]] || fail "session cookie isn't Secure for an https base URL"
location=$(grep -i "^location:" cookie-https.txt || true)
[[ $location == *"redirect_uri=https://reblender.example.com/login/oauth2/code/spotify"* ]] \
  || fail "redirect_uri doesn't use APP_BASE_URL"
