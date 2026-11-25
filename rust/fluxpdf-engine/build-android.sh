#!/usr/bin/env bash
set -euo pipefail

# Cross-compile the FluxPDF Rust engine into Android jniLibs using cargo-ndk.
# Requires: rustup + Android targets (aarch64-linux-android, armv7-linux-androideabi, x86_64-linux-android)
#           cargo install cargo-ndk, and ANDROID_NDK_HOME configured.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNI_OUT="${SCRIPT_DIR}/../../app/src/main/jniLibs"

cd "${SCRIPT_DIR}"
cargo ndk \
  -p 24 \
  -t armeabi-v7a \
  -t arm64-v8a \
  -t x86_64 \
  -o "${JNI_OUT}" \
  build --release

echo "Built libfluxpdf_engine.so into ${JNI_OUT}"
