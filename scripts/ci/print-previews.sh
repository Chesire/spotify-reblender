#!/usr/bin/env bash
# Prints the rendered page previews into the log in 4000-character chunks (very long log lines are dropped).
set -euo pipefail

ls -la build/previews || true
for f in build/previews/*.b64; do
  [ -f "$f" ] || continue
  name=$(basename "$f" .b64)
  fold -w 4000 "$f" | awk -v n="$name" '{ print "PREVIEW " n " " NR " " $0 }'
done
