#!/bin/sh
# Render docs/diagrams/*.mmd to *.svg next to them.
# Requires: mermaid-cli + a Chromium browser.
#
# The committed SVGs were produced with mermaid-cli 12.2.0, whose theme
# (Recursive Variable font, embedded OFL block) differs from 10.x. Re-render
# ALL diagrams together after upgrading mmdc, or the set stops matching:
#
#   npm i -g @mermaid-js/mermaid-cli@12.2.0
#
# Default config points at Brave (macOS); override with:
#   PUPPETEER_CONFIG=/path/to/puppeteer.json bash docs/diagrams/render.sh
set -eu
cd "$(dirname "$0")"
CONFIG="${PUPPETEER_CONFIG:-puppeteer.json}"
for src in *.mmd; do
  out="${src%.mmd}.svg"
  echo "render $src -> $out"
  mmdc -i "$src" -o "$out" -p "$CONFIG" --backgroundColor white
done
