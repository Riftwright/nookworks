#!/usr/bin/env bash
# WSL or Git Bash entry for tools/gradle.ps1. Do not execute gradlew.bat from bash.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
if command -v wslpath >/dev/null 2>&1; then
  win_root="$(wslpath -w "$root")"
elif command -v cygpath >/dev/null 2>&1; then
  win_root="$(cygpath -w "$root")"
else
  win_root="$(printf '%s' "$root" | sed -e 's|^/mnt/\([a-zA-Z]\)/|\1:/|' -e 's|/|\\|g')"
fi
if [ "$#" -eq 0 ]; then
  set -- compileJava
fi
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "${win_root}\\tools\\gradle.ps1" "$@"
