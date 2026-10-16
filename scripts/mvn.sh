#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "${HEALTH_JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$HEALTH_JAVA_HOME"
elif [[ -x /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
elif [[ -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
else
  : "${JAVA_HOME:?Set HEALTH_JAVA_HOME to a JDK 17 installation}"
fi
export PATH="$JAVA_HOME/bin:$PATH"
VERSION="$(java -version 2>&1 | head -n 1)"
if [[ "$VERSION" != *'"17.'* ]]; then
  echo "本项目要求 JDK 17，当前为：$VERSION" >&2
  exit 1
fi
cd "$ROOT"
exec mvn --settings "$ROOT/.mvn/settings.xml" -Dmaven.repo.local="$ROOT/.dev/m2" "$@"
