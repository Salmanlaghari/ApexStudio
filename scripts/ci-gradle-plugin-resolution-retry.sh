#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "Usage: $0 <gradle arguments...>" >&2
  exit 2
fi

attempt=1
max_attempts=2
log_file="$(mktemp)"
trap 'rm -f "$log_file"' EXIT

while true; do
  if ./gradlew "$@" --no-daemon 2>&1 | tee "$log_file"; then
    exit 0
  else
    status=$?
  fi

  if [ "$attempt" -ge "$max_attempts" ] || ! grep -Eq 'Plugin \[id:.*\] was not found in any of the following sources:|Could not resolve plugin artifact' "$log_file"; then
    exit "$status"
  fi

  echo '::warning::Gradle plugin resolution failed; retrying once in 15 seconds.'
  sleep 15
  attempt=$((attempt + 1))
done
