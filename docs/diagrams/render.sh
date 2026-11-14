#!/bin/sh
# Render docs/diagrams/*.mmd to *.svg and *.png next to them.
# Requires: mermaid-cli + a Chromium browser.
#
# The committed renders were produced with mermaid-cli 12.2.0, whose theme
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
# PNG is rendered at 4x: the SVG text is 12px in a coordinate space of ~860
# units, and markdown draws the image at container width, so anything below 4x
# reads as pixelated once the flowchart is scaled to fit.
SCALE="${MMDC_SCALE:-4}"
for src in *.mmd; do
  base="${src%.mmd}"
  echo "render $src -> $base.svg"
  mmdc -i "$src" -o "$base.svg" -p "$CONFIG" --backgroundColor white
  echo "render $src -> $base.png"
  mmdc -i "$src" -o "$base.png" -p "$CONFIG" --backgroundColor white --scale "$SCALE"
done
