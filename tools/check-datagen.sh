#!/usr/bin/env bash
# Regenerates every datagen output into a clean src/generated/resources and fails on any added,
# changed or deleted file compared with the committed tree (quality gates, "Datagen").
set -euo pipefail
cd "$(dirname "$0")/.."

rm -rf src/generated/resources
./gradlew --console=plain runClientData

changes=$(git status --porcelain --untracked-files=all -- src/generated/resources)
if [ -n "$changes" ]; then
    echo "Datagen output differs from the committed src/generated/resources:"
    echo "$changes"
    exit 1
fi
echo "Datagen output matches the committed resources."
