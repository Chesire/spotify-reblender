#!/usr/bin/env bash
# Shared helpers for the CI scripts. Source it; don't run it.

fail() {
  echo "::error::$*"
  exit 1
}

# wait_for_http URL [OUTPUT_FILE]: polls until URL answers 2xx, for up to 2 minutes.
wait_for_http() {
  local url=$1 out=${2:-/dev/null}
  for _ in $(seq 1 60); do
    if curl -fsS "$url" -o "$out"; then return 0; fi
    sleep 2
  done
  fail "$url did not respond"
}

# run_app NAME PORT [docker run options...]: starts the CI image in the background with dummy Spotify credentials.
run_app() {
  local name=$1 port=$2
  shift 2
  docker run -d --name "$name" \
    --read-only --tmpfs /tmp \
    -p "127.0.0.1:$port:8080" \
    -e SPOTIFY_CLIENT_ID=ci -e SPOTIFY_CLIENT_SECRET=ci \
    "$@" \
    spotify-reblender:ci > /dev/null
}
