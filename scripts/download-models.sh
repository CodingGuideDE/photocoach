#!/bin/sh
# Laedt die ML-Modelle nach models/ und prueft sie per MD5 gegen die Angabe von Google.
# Die Modelle sind nicht eingecheckt (zu gross); ohne sie baut die App zwar, die
# Tipp-Segmentierung meldet dann aber "Modell fehlt".
set -e
cd "$(dirname "$0")/.."
mkdir -p models

fetch() {  # fetch <Zieldatei> <URL> <MD5 base64>
  target="models/$1"
  if [ -f "$target" ] && [ "$(openssl md5 -binary "$target" | base64)" = "$3" ]; then
    echo "ok (schon da): $target"; return
  fi
  echo "lade $target ..."
  curl -fL -o "$target.part" "$2"
  actual=$(openssl md5 -binary "$target.part" | base64)
  if [ "$actual" != "$3" ]; then
    rm -f "$target.part"; echo "FEHLER: Pruefsumme passt nicht ($actual statt $3)"; exit 1
  fi
  mv "$target.part" "$target"; echo "ok: $target"
}

# MediaPipe Interactive Segmenter, MagicTouch v2 (int8), feste Version 1 statt "latest",
# damit sich das Verhalten nicht unbemerkt aendert. Lizenz: Apache 2.0 (Model Card).
fetch interactive_segmentation.task \
  "https://storage.googleapis.com/mediapipe-models/interactive_segmenter_v2/magic_touch/int8/1/interactive_segmentation.task" \
  "7/lZM2KQakcRKIYxHG00xg=="
