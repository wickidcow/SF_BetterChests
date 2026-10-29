#!/usr/bin/env bash
# Explicit, tag-pinned publication only. Do not replace or supplement an existing release.
set -euo pipefail

: "${GITHUB_REPOSITORY:?}"
: "${GITHUB_SHA:?}"
: "${GITHUB_REF:?}"
: "${GITHUB_EVENT_NAME:?}"
: "${VERSION:?}"
: "${OUTPUT_NAME:?}"

tag="v${VERSION}"
if [[ "$GITHUB_EVENT_NAME" != "workflow_dispatch" || "$GITHUB_REF" != "refs/tags/${tag}" ]]; then
  echo "Refusing publication: an explicit manual run on the exact new version tag is required." >&2
  exit 1
fi
if [[ "$OUTPUT_NAME" != "SF_BetterChests${VERSION}.jar" ]]; then
  echo "Refusing publication: release artifact name and version disagree." >&2
  exit 1
fi

jar="dist/${OUTPUT_NAME}"
if [[ ! -s "$jar" ]]; then
  echo "Refusing publication: verified raw plugin JAR is missing." >&2
  exit 1
fi
unzip -tqq "$jar"

# Existing releases are immutable even when their current asset digest matches.
# A failed read is not permission to upload to an existing release: the only
# permitted write is a create, which GitHub rejects for existing releases.
if gh release view "$tag" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  echo "Refusing publication: release $tag already exists; bump the version." >&2
  exit 1
fi

digest="$(sha256sum "$jar" | awk '{print $1}')"
echo "Creating distinct $tag from $GITHUB_SHA with raw JAR sha256:$digest"
gh release create "$tag" "$jar" --repo "$GITHUB_REPOSITORY" --verify-tag \
  --target "$GITHUB_SHA" --title "SF_BetterChests $tag" \
  --notes "BetterChests ${VERSION}: Java 21 JAR with drawer compatibility and runtime checks; consult exact-head CI for limits."
