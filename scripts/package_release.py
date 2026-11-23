#!/usr/bin/env python3

from pathlib import Path
import hashlib
import re
import shutil

root = Path(__file__).resolve().parents[1]
version = re.search(r'versionName = "([^"]+)"', (root / "app/build.gradle.kts").read_text()).group(1)
apk = root / "app/build/outputs/apk/debug/app-debug.apk"
if not apk.is_file():
    raise SystemExit("Zuerst ./gradlew assembleDebug ausführen.")
release = root / "release"
release.mkdir(exist_ok=True)
output = release / f"Gimbo-Gallery-{version.replace(' ', '-')}.apk"
shutil.copy2(apk, output)
(release / "SHA256SUMS").write_text(f"{hashlib.sha256(output.read_bytes()).hexdigest()}  {output.name}\n", newline="\n")
print(f"APK: {output.name} ({output.stat().st_size / 1024**2:.1f} MiB)")
print("Prüfsummen: SHA256SUMS")
