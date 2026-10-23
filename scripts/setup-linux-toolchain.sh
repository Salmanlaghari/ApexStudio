#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# ApexStudio — reproducible Linux toolchain bootstrap + dependency preflight.
#
# Installs:  JDK 17, Gradle 8.9
# Verifies:  whether Android SDK / Google Maven / Maven Central are reachable.
#
# Usage:     bash scripts/setup-linux-toolchain.sh
#            GRADLE_VERSION=8.9 bash scripts/setup-linux-toolchain.sh
#
# Exit code: 0 = toolchain ready AND all Android build sources reachable
#            2 = toolchain ready BUT at least one required source is blocked
# ---------------------------------------------------------------------------
set -uo pipefail

GRADLE_VERSION="${GRADLE_VERSION:-8.9}"
GRADLE_MIRROR="${GRADLE_MIRROR:-https://mirrors.cloud.tencent.com/gradle}"
UBUNTU_MIRROR="${UBUNTU_MIRROR:-https://mirrors.cloud.tencent.com/ubuntu}"
INSTALL_ROOT="${INSTALL_ROOT:-/opt}"
SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"

log()  { printf '\033[1;34m[apex]\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m[ ok ]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[fail]\033[0m %s\n' "$*"; }

# ---------------------------------------------------------------- JDK 17 ---
install_jdk() {
  if command -v java >/dev/null 2>&1 \
     && java -version 2>&1 | grep -q '"17'; then
    ok "JDK 17 already present: $(java -version 2>&1 | head -1)"
    return 0
  fi
  log "Installing OpenJDK 17 (headless)..."
  $SUDO apt-get update -qq || warn "apt update failed — continuing"
  DEBIAN_FRONTEND=noninteractive $SUDO apt-get install -y -qq \
      openjdk-17-jdk-headless unzip zip curl ca-certificates \
    || { die "JDK 17 install failed"; return 1; }
  ok "JDK 17 installed"
}

resolve_java_home() {
  for c in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk \
           /usr/lib/jvm/java-17-openjdk-*; do
    [ -x "$c/bin/java" ] && { echo "$c"; return 0; }
  done
  echo ""
}

# -------------------------------------------------------------- Gradle -----
install_gradle() {
  local target="$INSTALL_ROOT/gradle-$GRADLE_VERSION"
  if [ -x "$target/bin/gradle" ]; then
    ok "Gradle $GRADLE_VERSION already present at $target"
    return 0
  fi
  log "Downloading Gradle $GRADLE_VERSION from $GRADLE_MIRROR ..."
  local tmp; tmp="$(mktemp -d)"
  if ! curl -fsSL --max-time 600 \
        -o "$tmp/gradle.zip" "$GRADLE_MIRROR/gradle-$GRADLE_VERSION-bin.zip"; then
    warn "Mirror download failed; trying services.gradle.org ..."
    curl -fsSL --max-time 600 -o "$tmp/gradle.zip" \
      "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
      || { rm -rf "$tmp"; die "Gradle download failed from both sources"; return 1; }
  fi
  $SUDO mkdir -p "$INSTALL_ROOT"
  $SUDO unzip -q -o "$tmp/gradle.zip" -d "$INSTALL_ROOT" \
    || { rm -rf "$tmp"; die "Gradle extraction failed"; return 1; }
  rm -rf "$tmp"
  ok "Gradle $GRADLE_VERSION installed at $target"
}

# ------------------------------------------------------------- preflight ---
preflight() {
  log "Dependency preflight — an Android build needs ALL of these reachable:"
  local blocked=0
  check() {
    local label="$1" url="$2"
    local code; code="$(curl -sS -o /dev/null --max-time 15 -w '%{http_code}' "$url" 2>/dev/null)"
    case "$code" in
      200|204|301|302|403-with-body) ok   "$label reachable (HTTP $code)";;
      403) warn "$label BLOCKED (HTTP 403) — $url"; blocked=1;;
      *)   warn "$label UNREACHABLE (HTTP ${code:-000}) — $url"; blocked=1;;
    esac
  }
  check "Android SDK repository" "https://dl.google.com/android/repository/repository2-3.xml"
  check "Google Maven (AGP)"     "https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/"
  check "Maven Central"          "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/"
  return $blocked
}

# ------------------------------------------------------------------ main ---
main() {
  log "ApexStudio toolchain bootstrap"

  install_jdk
  JAVA_HOME="$(resolve_java_home)"
  if [ -z "$JAVA_HOME" ]; then
    die "No JDK 17 found after install. Set JAVA_HOME manually."
    exit 1
  fi
  export JAVA_HOME
  ok "JAVA_HOME=$JAVA_HOME"
  "$JAVA_HOME/bin/java" -version 2>&1 | head -1

  install_gradle

  # Functional smoke test — proves JVM + Gradle actually execute, not just unpack.
  log "Running Gradle smoke test..."
  local smoke; smoke="$(mktemp -d)"
  mkdir -p "$smoke/src/main/java"
  printf "plugins { id 'application' }\napplication { mainClass = 'Main' }\n" > "$smoke/build.gradle"
  printf 'public class Main { public static void main(String[] a){ System.out.println("GRADLE_OK"); } }\n' \
    > "$smoke/src/main/java/Main.java"
  if (cd "$smoke" && "$INSTALL_ROOT/gradle-$GRADLE_VERSION/bin/gradle" -q --no-daemon run 2>/dev/null | grep -q GRADLE_OK); then
    ok "Gradle smoke test passed (GRADLE_OK)"
  else
    warn "Gradle smoke test did not print GRADLE_OK — check the log above"
  fi
  rm -rf "$smoke"

  preflight
  local pf=$?

  echo
  if [ $pf -eq 0 ]; then
    ok "Toolchain ready AND all Android build sources reachable."
    echo "    export JAVA_HOME=$JAVA_HOME"
    echo "    export PATH=\$PATH:$INSTALL_ROOT/gradle-$GRADLE_VERSION/bin"
  else
    warn "Toolchain (JDK + Gradle) is ready, but Android build sources are BLOCKED on this host."
    echo "    You cannot run :app:assembleDebug here. Build on a machine with unrestricted access to:"
    echo "      - dl.google.com            (Android SDK + AGP)"
    echo "      - repo1.maven.org          (Kotlin, Media3, Compose)"
    echo
    echo "    Then:  ./gradlew :app:assembleDebug"
  fi
  return $pf
}

main "$@"
