#!/bin/sh
set -eu

runtime_file="/usr/share/nginx/html/runtime-config.js"
token="${MAPBOX_ACCESS_TOKEN:-}"

printf 'window.__SENTINEL_CONFIG__ = Object.freeze({"mapboxAccessToken":"%s"});\n' \
  "$token" > "$runtime_file"
