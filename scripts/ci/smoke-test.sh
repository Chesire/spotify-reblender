#!/usr/bin/env bash
# Starts the app (left running for the later checks) and checks login, security headers, proxy headers,
# cookies and access control.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

base=http://127.0.0.1:8080
run_app reblender 8080 \
  --security-opt no-new-privileges:true \
  -v reblender-ci:/data \
  -e TOKEN_ENCRYPTION_KEY="$(openssl rand -base64 32)"
wait_for_http "$base/" home.html
docker logs reblender | tail -n 40
grep -q "Log in with Spotify" home.html || fail "home page has no login button"

echo "== Login redirect: Spotify, expected scopes, PKCE"
location=$(curl -s -o /dev/null -w '%{redirect_url}' "$base/oauth2/authorization/spotify")
echo "$location"
[[ $location == https://accounts.spotify.com/authorize* ]] || fail "login doesn't redirect to Spotify"
[[ $location == *"scope=user-top-read%20playlist-modify-private%20playlist-modify-public&"* ]] || fail "unexpected scopes"
[[ $location == *"code_challenge="* ]] || fail "no PKCE code_challenge"
[[ $location == *"code_challenge_method=S256"* ]] || fail "PKCE method isn't S256"

echo "== Security headers"
curl -s -D headers.txt -o /dev/null "$base/"
cat headers.txt
grep -qi "^content-security-policy:.*script-src 'self';" headers.txt || fail "CSP script-src"
grep -qi "^content-security-policy:.*frame-ancestors 'none'" headers.txt || fail "CSP frame-ancestors"
grep -qi "^referrer-policy: strict-origin-when-cross-origin" headers.txt || fail "Referrer-Policy"
grep -qi "^permissions-policy:.*camera=()" headers.txt || fail "Permissions-Policy"
grep -qi "^x-content-type-options: nosniff" headers.txt || fail "X-Content-Type-Options"

echo "== Forwarded headers from clients are ignored"
curl -s -D fwd.txt -o /dev/null -H "X-Forwarded-Host: evil.example" -H "X-Forwarded-Proto: https" \
  "$base/playlists/0123456789abcdefghijkl"
cat fwd.txt
if grep -qi "evil.example" fwd.txt; then fail "a client-sent forwarded host was trusted"; fi

echo "== Session cookie isn't Secure over plain http"
cookie=$(curl -s -D - -o /dev/null "$base/oauth2/authorization/spotify" | grep -i "^set-cookie: JSESSIONID" || true)
echo "$cookie"
[ -n "$cookie" ] || fail "no session cookie"
if [[ ${cookie,,} == *secure* ]]; then fail "cookie unexpectedly Secure over http"; fi

echo "== Protected routes need a login"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$base/playlists")
echo "Anonymous POST /playlists -> $code"
[ "$code" != "200" ] || fail "anonymous POST /playlists was accepted"

echo "== The JSON API answers 401, not a login redirect"
code=$(curl -s -o /dev/null -w '%{http_code}' "$base/api/v1/playlists")
echo "Anonymous GET /api/v1/playlists -> $code"
[ "$code" = "401" ] || fail "anonymous API request didn't get 401"
