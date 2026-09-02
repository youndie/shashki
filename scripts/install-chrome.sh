#!/usr/bin/env bash
# Put a headless Chrome on this machine, so `wasmJsBrowserTest` has a browser to run in.
#
#   bash scripts/install-chrome.sh          # into ~/.cache/shashki/chrome
#   eval "$(bash scripts/install-chrome.sh --export)"   # and print CHROME_BIN for the shell
#
# **Chrome for Testing, pinned, unzipped — not a package.** Ubuntu 24.04 ships `chromium` as a snap
# transitional package with no apt candidate at all, and snap inside WSL is its own problem. Chrome
# for Testing is a plain archive Google publishes per version for exactly this: no root, no snap, and
# the version is a number in this file rather than whatever the machine's channel happened to be.
# A golden that renders in a browser is a golden of *that* browser.
#
# The shared libraries it needs are the only part that touches the system, and it installs them only
# when they are missing.
set -euo pipefail

VERSION=${CHROME_VERSION:-152.0.7977.75}
ROOT=${CHROME_HOME:-$HOME/.cache/shashki/chrome}
TARGET="$ROOT/$VERSION/chrome-linux64/chrome"

if [ "${1:-}" = "--export" ]; then
  [ -x "$TARGET" ] || { echo "no chrome at $TARGET; run $0 first" >&2; exit 1; }
  echo "export CHROME_BIN=$TARGET"
  exit 0
fi

if [ -x "$TARGET" ]; then
  echo "already here: $TARGET"
else
  mkdir -p "$ROOT/$VERSION"
  url="https://storage.googleapis.com/chrome-for-testing-public/$VERSION/linux64/chrome-linux64.zip"
  echo "fetching $url"
  curl -fsSL "$url" -o "$ROOT/$VERSION/chrome.zip"
  unzip -q -o "$ROOT/$VERSION/chrome.zip" -d "$ROOT/$VERSION"
  rm "$ROOT/$VERSION/chrome.zip"
fi

# Whatever the binary says it is missing, by name, rather than a list somebody wrote down once.
missing=$(ldd "$TARGET" 2>/dev/null | awk '/not found/ {print $1}' | sort -u)
if [ -n "$missing" ]; then
  echo "missing shared libraries:"
  echo "$missing" | sed 's/^/  /'
  echo "installing the packages Chrome's own dependency list names"
  sudo apt-get update -qq
  sudo apt-get install -y -qq \
    libasound2t64 libatk-bridge2.0-0t64 libatk1.0-0t64 libcups2t64 libdrm2 libgbm1 \
    libgtk-3-0t64 libnspr4 libnss3 libpango-1.0-0 libxcomposite1 libxdamage1 \
    libxfixes3 libxkbcommon0 libxrandr2 xdg-utils
fi

"$TARGET" --version
echo
echo "point the build at it:"
echo "  export CHROME_BIN=$TARGET"
