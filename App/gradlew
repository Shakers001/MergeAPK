#!/bin/sh
# Minimal wrapper launcher; the official Gradle wrapper JAR is intentionally not bundled.
# GitHub Actions uses gradle/actions/setup-gradle to install Gradle before invoking this project.
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed. Use GitHub Actions workflow or install Gradle 8.9+." >&2
exit 1
