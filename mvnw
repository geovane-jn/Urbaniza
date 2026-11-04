#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -x /workspace/tools/jdk-21.0.8/bin/javac && ! -x "${JAVA_HOME:-/usr/lib/jvm/default-java}/bin/javac" ]]; then
  export JAVA_HOME=/workspace/tools/jdk-21.0.8
fi
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
  export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
if command -v mvn >/dev/null 2>&1; then
  MAVEN_CMD="$(command -v mvn)"
elif [[ -x /workspace/tools/apache-maven-3.9.9/bin/mvn ]]; then
  MAVEN_CMD=/workspace/tools/apache-maven-3.9.9/bin/mvn
else
  mkdir -p .urbaniza/tools
  if [[ ! -x .urbaniza/tools/apache-maven-3.9.9/bin/mvn ]]; then
    curl --fail --silent --show-error --location https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz --output .urbaniza/tools/maven.tar.gz
    echo "a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b  .urbaniza/tools/maven.tar.gz" | sha512sum --check --status
    tar -xzf .urbaniza/tools/maven.tar.gz -C .urbaniza/tools
    rm .urbaniza/tools/maven.tar.gz
  fi
  MAVEN_CMD="$PWD/.urbaniza/tools/apache-maven-3.9.9/bin/mvn"
fi
# O Maven não herda HTTP_PROXY sozinho. Nenhuma credencial é lida ou salva.
mkdir -p .urbaniza
python3 - <<'PYSETTINGS'
import os, pathlib, urllib.parse, xml.etree.ElementTree as ET
root=ET.Element('settings',xmlns='http://maven.apache.org/SETTINGS/1.2.0')
p=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY') or os.environ.get('HTTP_PROXY') or '')
if p.hostname:
    proxies=ET.SubElement(root,'proxies'); proxy=ET.SubElement(proxies,'proxy')
    for k,v in {'id':'environment','active':'true','protocol':'http','host':p.hostname,'port':str(p.port or 80),'nonProxyHosts':'localhost|127.0.0.1'}.items(): ET.SubElement(proxy,k).text=v
ET.ElementTree(root).write(pathlib.Path('.urbaniza/maven-settings.xml'),encoding='unicode')
PYSETTINGS
exec "$MAVEN_CMD" -B -s .urbaniza/maven-settings.xml "-Dmaven.repo.local=$PWD/.urbaniza/m2" "$@"
