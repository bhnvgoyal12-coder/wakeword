#!/usr/bin/env bash
# Download the on-device models + sherpa-onnx AAR (all from sherpa-onnx GitHub releases).
set -euo pipefail
cd "$(dirname "$0")"
REL=https://github.com/k2-fsa/sherpa-onnx/releases/download
KWS=sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01
SHERPA_VERSION=1.13.8
A=wakeword-android/src/main/assets/wakeword
mkdir -p "$A/kws" wakeword-android/libs
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

curl -fL --retry 3 "$REL/kws-models/$KWS.tar.bz2" | tar xj -C "$tmp"
# int8 models only (what the engine loads); tokens + default keywords file
cp "$tmp/$KWS"/{encoder,decoder,joiner}-epoch-12-avg-2-chunk-16-left-64.int8.onnx "$A/kws/"
cp "$tmp/$KWS"/tokens.txt "$tmp/$KWS"/keywords.txt "$A/kws/"
curl -fL --retry 3 -o "$A/silero_vad.onnx" "$REL/asr-models/silero_vad.onnx"
cp ../shared/kws_vocab.tsv "$A/kws_vocab.tsv"
[ -f "wakeword-android/libs/sherpa-onnx-$SHERPA_VERSION.aar" ] || \
  curl -fL --retry 3 -o "wakeword-android/libs/sherpa-onnx-$SHERPA_VERSION.aar" "$REL/v$SHERPA_VERSION/sherpa-onnx-$SHERPA_VERSION.aar"
du -sh "$A"
