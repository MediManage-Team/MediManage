#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

WRAPPER_SCRIPT=""
cleanup() {
  if [[ -n "$WRAPPER_SCRIPT" ]]; then
    rm -f "$WRAPPER_SCRIPT"
  fi
}
trap cleanup EXIT

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "ERROR: scripts/build_linux_package.sh must be run on Linux."
  exit 1
fi

if [[ ! -f base_medimanage.db ]]; then
  echo "ERROR: base_medimanage.db is missing."
  exit 1
fi

if command -v mvn >/dev/null 2>&1; then
  MVN=("mvn")
elif [[ -f "./mvnw" ]]; then
  WRAPPER_SCRIPT=".mvnw-linux.sh"
  tr -d '\r' < "./mvnw" > "$WRAPPER_SCRIPT"
  chmod +x "$WRAPPER_SCRIPT"
  MVN=(bash "$WRAPPER_SCRIPT")
else
  MVN=("mvn")
fi

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/jpackage" ]]; then
  JPACKAGE="${JAVA_HOME}/bin/jpackage"
else
  JPACKAGE="$(command -v jpackage || true)"
fi

if [[ -z "$JPACKAGE" ]]; then
  echo "ERROR: jpackage was not found. Install JDK 21+ and set JAVA_HOME."
  exit 1
fi

ARTIFACT_ID="$(sed -n 's:.*<artifactId>\(.*\)</artifactId>.*:\1:p' pom.xml | head -n 1)"
PROJECT_VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -n 1)"

if [[ -z "$ARTIFACT_ID" || -z "$PROJECT_VERSION" ]]; then
  echo "ERROR: Failed to read project metadata from pom.xml."
  exit 1
fi

DIST_PATH="dist/linux/MediManage-${PROJECT_VERSION}"
IMAGE_OUTPUT="dist/linux/image"
PACKAGE_OUTPUT="dist/linux/package"
APP_IMAGE_DIR="${IMAGE_OUTPUT}/MediManage"

echo "Using jpackage: ${JPACKAGE}"
echo "Artifact ID: ${ARTIFACT_ID}"
echo "Project Version: ${PROJECT_VERSION}"

echo "=========================================="
echo "1. Building application with Maven"
echo "=========================================="
"${MVN[@]}" clean package dependency:copy-dependencies

rm -rf "$DIST_PATH" "$IMAGE_OUTPUT" "$PACKAGE_OUTPUT"
mkdir -p "$DIST_PATH/lib" "$PACKAGE_OUTPUT"

JAR_PATH="target/${ARTIFACT_ID}-${PROJECT_VERSION}.jar"
if [[ ! -f "$JAR_PATH" ]]; then
  echo "ERROR: Expected application JAR not found at ${JAR_PATH}"
  exit 1
fi

cp "$JAR_PATH" "$DIST_PATH/MediManage.jar"
cp target/dependency/*.jar "$DIST_PATH/lib/"

echo "=========================================="
echo "2. Creating Linux app image"
echo "=========================================="
"$JPACKAGE" \
  --type app-image \
  --name "MediManage" \
  --input "$DIST_PATH" \
  --main-jar "MediManage.jar" \
  --main-class "org.example.MediManage.Launcher" \
  --dest "$IMAGE_OUTPUT" \
  --app-version "$PROJECT_VERSION" \
  --icon "src/main/resources/app_icon.png" \
  --java-options "--add-modules=jdk.crypto.ec,jdk.naming.dns,java.naming" \
  --vendor "MediManage Team" \
  --copyright "Copyright 2024"

echo "=========================================="
echo "3. Bundling Linux sidecar assets"
echo "=========================================="
mkdir -p "$APP_IMAGE_DIR/ai_engine" "$APP_IMAGE_DIR/whatsapp-server" "$APP_IMAGE_DIR/runtime/db"

cp -R ai_engine/app "$APP_IMAGE_DIR/ai_engine/"
cp -R ai_engine/server "$APP_IMAGE_DIR/ai_engine/"
cp -R ai_engine/requirements "$APP_IMAGE_DIR/ai_engine/"
cp ai_engine/mcp_config.json "$APP_IMAGE_DIR/ai_engine/"
cp base_medimanage.db "$APP_IMAGE_DIR/runtime/db/medimanage.db"

cp whatsapp-server/index.js "$APP_IMAGE_DIR/whatsapp-server/"
cp whatsapp-server/security.js "$APP_IMAGE_DIR/whatsapp-server/"
cp whatsapp-server/package.json "$APP_IMAGE_DIR/whatsapp-server/"
cp whatsapp-server/package-lock.json "$APP_IMAGE_DIR/whatsapp-server/"

if [[ -f whatsapp-server/start_protected.js ]]; then
  cp whatsapp-server/start_protected.js "$APP_IMAGE_DIR/whatsapp-server/"
fi

if [[ -f whatsapp-server/.env ]]; then
  cp whatsapp-server/.env "$APP_IMAGE_DIR/whatsapp-server/"
fi

if [[ -d whatsapp-server/node_modules ]]; then
  cp -R whatsapp-server/node_modules "$APP_IMAGE_DIR/whatsapp-server/"
fi

echo "=========================================="
echo "4. Building .deb package"
echo "=========================================="
"$JPACKAGE" \
  --type deb \
  --name "MediManage" \
  --app-image "$APP_IMAGE_DIR" \
  --dest "$PACKAGE_OUTPUT" \
  --app-version "$PROJECT_VERSION" \
  --vendor "MediManage Team" \
  --license-file "LICENSE.txt" \
  --description "Single-store pharmacy management desktop application" \
  --linux-package-name "medimanage" \
  --linux-shortcut

echo "=========================================="
echo "BUILD SUCCESSFUL"
echo "App image: ${APP_IMAGE_DIR}"
echo "Deb package(s):"
find "$PACKAGE_OUTPUT" -maxdepth 1 -name '*.deb' -print
echo "=========================================="
