#!/usr/bin/env bash
set -euo pipefail

version="v0.42.0"
archive="libmpv-${version}-x86_64-w64-mingw32-lgpl.zip"
sha256="8e5f77430efc53ef175804ba6618527e3af0448a0dcabf04d3cc72659d1a6fce"
dest="$(cd "$(dirname "$0")/.." && pwd)/desktop/resources/windows-x64"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

curl -fsSL --retry 3 -o "$tmp/$archive" \
  "https://github.com/mpv-player/mpv/releases/download/${version}/${archive}"
echo "${sha256}  ${tmp}/${archive}" | sha256sum -c -

mkdir -p "$dest"
if command -v unzip >/dev/null 2>&1; then
  unzip -o -j "$tmp/$archive" libmpv-2.dll LICENSE.LGPL -d "$dest"
elif command -v 7z >/dev/null 2>&1; then
  7z e -y "-o$dest" "$tmp/$archive" libmpv-2.dll LICENSE.LGPL
else
  python3 -c 'import sys, zipfile; z = zipfile.ZipFile(sys.argv[1]); [z.extract(n, sys.argv[2]) for n in ("libmpv-2.dll", "LICENSE.LGPL")]' "$tmp/$archive" "$dest"
fi
mv "$dest/LICENSE.LGPL" "$dest/libmpv-LICENSE.LGPL"
ls -l "$dest"
