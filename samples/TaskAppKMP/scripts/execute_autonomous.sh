#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAMPLE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$SAMPLE_DIR/../.." && pwd)"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' is missing" >&2
    exit 1
  fi
}

require_cmd java
require_cmd curl

check_artifact() {
  local artifact="$1"
  local url="https://repo1.maven.org/maven2/io/github/fshamim/${artifact}/0.8.0-alpha02/"
  curl -sfI "$url" >/dev/null
}

ensure_artifacts() {
  local artifacts=(
    "stateproof-core"
    "stateproof-core-jvm"
    "stateproof-compose"
    "stateproof-compose-jvm"
    "stateproof-navigation"
    "stateproof-viewer-jvm"
    "stateproof-annotations"
    "stateproof-ksp"
    "stateproof-gradle-plugin"
  )

  for artifact in "${artifacts[@]}"; do
    if ! check_artifact "$artifact"; then
      echo "Artifact $artifact missing from Maven Central, publishing local fallback"
      "$REPO_DIR/gradlew" -p "$REPO_DIR" publishToMavenLocal
      return
    fi
  done
}

read_sdk_from_local_properties() {
  local file="$1"
  if [[ -f "$file" ]]; then
    grep -E '^sdk.dir=' "$file" | tail -n1 | cut -d'=' -f2-
  fi
}

resolve_android_sdk() {
  local candidate

  candidate="$(read_sdk_from_local_properties "$SAMPLE_DIR/local.properties")"
  if [[ -n "$candidate" && -d "$candidate" ]]; then
    echo "$candidate"
    return
  fi

  candidate="$(read_sdk_from_local_properties "$REPO_DIR/local.properties")"
  if [[ -n "$candidate" && -d "$candidate" ]]; then
    echo "$candidate"
    return
  fi

  if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
    echo "$ANDROID_SDK_ROOT"
    return
  fi

  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
    echo "$ANDROID_HOME"
    return
  fi

  if [[ -d "/Applications/sdk" ]]; then
    echo "/Applications/sdk"
    return
  fi
}

run_stateproof_task() {
  local task="$1"
  if ./gradlew "$task" -PstateproofClasspathConfig=desktopTestRuntimeClasspath; then
    return
  fi

  ./gradlew "$task" -PstateproofClasspathConfig=jvmTestRuntimeClasspath
}

ensure_artifacts

sdk_dir="$(resolve_android_sdk || true)"
android_enabled="false"
if [[ -n "$sdk_dir" ]]; then
  printf 'sdk.dir=%s\n' "$sdk_dir" > "$SAMPLE_DIR/local.properties"
  android_enabled="true"
  echo "Android SDK resolved to $sdk_dir"
else
  echo "Android SDK not found, Android assemble will be skipped"
fi

cd "$SAMPLE_DIR"

# Avoid stale generated KSP/codegen artifacts when rerunning autonomously.
./gradlew :composeApp:clean
run_stateproof_task ":composeApp:stateproofScan"
run_stateproof_task ":composeApp:stateproofSyncAll"
"$SAMPLE_DIR/scripts/hydrate-generated-tests.sh"
./gradlew :composeApp:desktopTest
run_stateproof_task ":composeApp:stateproofDiagrams"
run_stateproof_task ":composeApp:stateproofViewer"
./gradlew :composeApp:compileKotlinDesktop

./gradlew :composeApp:kspCommonMainKotlinMetadata || true
./gradlew :composeApp:compileKotlinIosSimulatorArm64 || true

if [[ "$android_enabled" == "true" ]]; then
  ./gradlew :androidApp:assembleDebug
fi

echo "Autonomous execution complete"
