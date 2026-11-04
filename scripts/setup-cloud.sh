#!/usr/bin/env bash
# Preparação repetível da imagem cloud Linux x86_64; não altera código nem dados.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/javac ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
else
  if [[ ! -x /workspace/tools/jdk-21.0.8/bin/javac ]]; then
    mkdir -p /workspace/tools
    archive="$(mktemp /tmp/urbaniza-jdk.XXXXXX.tar.gz)"
    trap 'rm -f "$archive"' EXIT
    curl --fail --silent --show-error --location https://download.oracle.com/java/21/archive/jdk-21.0.8_linux-x64_bin.tar.gz --output "$archive"
    echo "d87272944278713fc7a120cf024d2818d136b5debc750aa17045e3c6f045b867  $archive" | sha256sum --check --status
    tar -xzf "$archive" -C /workspace/tools
  fi
  export JAVA_HOME=/workspace/tools/jdk-21.0.8
fi
./mvnw verify
