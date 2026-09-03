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
LAUNCHER="$ROOT/$VERSION/chrome-for-karma"

if [ "${1:-}" = "--export" ]; then
  [ -x "$LAUNCHER" ] || { echo "no launcher at $LAUNCHER; run $0 first" >&2; exit 1; }
  echo "export CHROME_BIN=$LAUNCHER"
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

# **The build never launches the binary directly; it launches this.** Chrome's own sandbox needs
# unprivileged user namespaces, and the GitHub runner image has them disabled — the browser dies
# before it opens a page (`FATAL … No usable sandbox!`), karma gives up after two attempts, and the
# wasm suites then fail as "the test task did not discover any tests", which names neither the
# browser nor the reason.
#
# The flag lives here and not in the workflow so that every machine launches the browser the same
# way. A launcher that differs between a laptop and CI is what makes "it passes locally" stop
# meaning anything, and this is the one process whose entire job is to be identical everywhere.
#
# What is given up is real and narrow: the renderer sandbox of a browser that only ever loads this
# build's own test bundle over localhost, on a machine that already runs the build.
printf '%s\n' \
  '#!/usr/bin/env bash' \
  '# Written by scripts/install-chrome.sh - edit that, not this.' \
  "exec \"$TARGET\" --no-sandbox --disable-dev-shm-usage \"\$@\"" > "$LAUNCHER"
chmod +x "$LAUNCHER"

"$TARGET" --version
echo
echo "point the build at it:"
echo "  export CHROME_BIN=$LAUNCHER"
