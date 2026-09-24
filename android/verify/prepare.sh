#!/usr/bin/env bash
# android.jar from a GitHub mirror of the SDK platform jars (dl.google.com is often blocked in CI sandboxes).
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p libs/aar
[ -f libs/android.jar ] || curl -fL --retry 3 -o libs/android.jar https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar
[ -f libs/sherpa-onnx.aar ] || curl -fL --retry 3 -o libs/sherpa-onnx.aar https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
unzip -o -q libs/sherpa-onnx.aar classes.jar -d libs/aar
