#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

mkdir -p secrets
openssl rand -base64 32 > secrets/token_encryption_key
sudo chown 10001:10001 secrets/token_encryption_key
sudo chmod 400 secrets/token_encryption_key

touch .env
docker compose -f docker-compose.yml -f deploy/docker-compose.secret.yml config > resolved.yml
grep -q "$PWD/secrets/token_encryption_key" resolved.yml \
  || fail "the compose override doesn't resolve the secret relative to the project directory"

run_app reblender-keyfile 8081 \
  -v "$PWD/secrets/token_encryption_key:/run/secrets/token_encryption_key:ro" \
  -e TOKEN_ENCRYPTION_KEY_FILE=/run/secrets/token_encryption_key
trap 'docker logs reblender-keyfile 2>&1 | tail -n 20; docker rm -f reblender-keyfile > /dev/null' EXIT
wait_for_http http://127.0.0.1:8081/
