#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

user=$(docker image inspect spotify-reblender:ci --format '{{.Config.User}}')
echo "Image user: $user"
[ -n "$user" ] && [ "$user" != "root" ] || fail "image runs as root"
