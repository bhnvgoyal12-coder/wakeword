#!/usr/bin/env bash
# Download the on-device models + sherpa-onnx AAR (all from sherpa-onnx GitHub releases).
set -euo pipefail
cd "$(dirname "$0")"
REL=https://github.com/k2-fsa/sherpa-onnx/releases/download
KWS=sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01
SHERPA_VERSION=1.13.8
A=wakeword-android/src/main/assets/wakeword
MVN=local-maven/com/k2fsa/sherpa/onnx/sherpa-onnx/$SHERPA_VERSION
mkdir -p "$A/kws" "$MVN"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

curl -fL --retry 3 "$REL/kws-models/$KWS.tar.bz2" | tar xj -C "$tmp"
# int8 models only (what the engine loads); tokens + default keywords file
cp "$tmp/$KWS"/{encoder,decoder,joiner}-epoch-12-avg-2-chunk-16-left-64.int8.onnx "$A/kws/"
cp "$tmp/$KWS"/tokens.txt "$tmp/$KWS"/keywords.txt "$A/kws/"
curl -fL --retry 3 -o "$A/silero_vad.onnx" "$REL/asr-models/silero_vad.onnx"
cp ../shared/kws_vocab.tsv "$A/kws_vocab.tsv"
# sherpa-onnx isn't on Maven Central; install its release AAR into a local Maven repo so both
# library and app modules can depend on it by coordinates (AGP rejects raw .aar files in libraries).
[ -f "$MVN/sherpa-onnx-$SHERPA_VERSION.aar" ] || \
  curl -fL --retry 3 -o "$MVN/sherpa-onnx-$SHERPA_VERSION.aar" "$REL/v$SHERPA_VERSION/sherpa-onnx-$SHERPA_VERSION.aar"
cat > "$MVN/sherpa-onnx-$SHERPA_VERSION.pom" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.k2fsa.sherpa.onnx</groupId>
  <artifactId>sherpa-onnx</artifactId>
  <version>$SHERPA_VERSION</version>
  <packaging>aar</packaging>
</project>
POM
du -sh "$A"
