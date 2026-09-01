#!/usr/bin/env bash
# Pre-talk step: build and tag the Docker images for all three demo
# branches (crud, json-hypermedia, main) so `docker compose up` never
# triggers a build on stage after a branch switch -- see "Before
# presenting" in AGENTS.md.
#
# Each branch's docker-compose.yml defaults IMAGE_TAG to its own branch
# name, so building on each branch in turn tags that branch's images
# correctly without this script needing to pass IMAGE_TAG itself.

set -euo pipefail

branches=(crud json-hypermedia main)

if [ -n "$(git status --porcelain)" ]; then
  echo "error: working tree has uncommitted changes." >&2
  echo "Commit or stash them before switching branches to build." >&2
  exit 1
fi

original_branch="$(git rev-parse --abbrev-ref HEAD)"

cleanup() {
  git checkout "$original_branch"
}
trap cleanup EXIT

for branch in "${branches[@]}"; do
  echo "==> Building $branch"
  git checkout "$branch"
  docker compose build
done
