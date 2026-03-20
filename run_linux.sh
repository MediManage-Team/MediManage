#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DRY_RUN="${MEDIMANAGE_LAUNCHER_DRY_RUN:-0}"

run_target() {
  local label="$1"
  shift
  echo "${label}"
  if [[ "${DRY_RUN}" == "1" ]]; then
    echo "DRY RUN: $*"
    exit 0
  fi
  exec "$@"
}

if [[ -x "${SCRIPT_DIR}/dist/linux/image/MediManage/bin/MediManage" ]]; then
  run_target "Running packaged Linux app image..." \
    "${SCRIPT_DIR}/dist/linux/image/MediManage/bin/MediManage"
fi

if [[ -x "/opt/medimanage/bin/MediManage" ]]; then
  run_target "Running installed Linux app..." /opt/medimanage/bin/MediManage
fi

if [[ -x "${SCRIPT_DIR}/mvnw" ]]; then
  run_target "Running development app via Maven..." \
    "${SCRIPT_DIR}/mvnw" javafx:run
fi

echo "ERROR: MediManage launcher not found."
echo "Build the Linux image with bash scripts/build_linux_package.sh or install the .deb first."
exit 1
