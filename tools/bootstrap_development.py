"""Install checksum-verified development archives inside this project only."""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import time
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".tools"
ARCHIVES = TOOLS / "archives"
HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
PACKAGES = [
    {"kind": "java", "name": "OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip",
     "url": "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip",
     "sha256": "00c847d804f4a78e9f04f2683faf14fed898535b177b7fc704486cb0284e9283"},
    {"kind": "gradle", "name": "gradle-9.5.1-bin.zip",
     "url": "https://downloads.gradle.org/distributions/gradle-9.5.1-bin.zip",
     "sha256": "bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f"},
]


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def install(package: dict) -> tuple[str, str]:
    archive = ARCHIVES / package["name"]
    if not archive.exists() or digest(archive) != package["sha256"]:
        req = urllib.request.Request(package["url"], headers=HEADERS)
        with urllib.request.urlopen(req, timeout=60) as source, archive.open("wb") as dest:
            received = 0
            last_message = time.monotonic()
            while block := source.read(1024 * 1024):
                dest.write(block)
                received += len(block)
                if time.monotonic() - last_message >= 5:
                    print(f"{package['kind']}: downloaded {received // (1024 * 1024)} MiB", flush=True)
                    last_message = time.monotonic()
        if digest(archive) != package["sha256"]:
            raise RuntimeError(f"Checksum mismatch: {archive.name}")
    with zipfile.ZipFile(archive) as z:
        roots = {name.split("/")[0] for name in z.namelist() if "/" in name}
        if len(roots) != 1:
            raise RuntimeError("Unexpected SDK archive roots")
        sdk = TOOLS / next(iter(roots))
        executable = sdk / "bin" / ("java.exe" if package["kind"] == "java" else "gradle.bat")
        if not executable.exists():
            for member in z.infolist():
                target = (TOOLS / member.filename).resolve()
                if not target.is_relative_to(TOOLS.resolve()):
                    raise RuntimeError("Archive entry escapes project tool directory")
            z.extractall(TOOLS)
    print(f"{package['kind']}: checksum verified, extracted to {sdk.name}", flush=True)
    return package["kind"], str(sdk)


def main() -> None:
    ARCHIVES.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        installed = dict(pool.map(install, PACKAGES))
    metadata = {"javaHome": installed["java"], "gradleHome": installed["gradle"],
                "archives": PACKAGES}
    (TOOLS / "development.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in metadata.items() if key != "archives"}), flush=True)


if __name__ == "__main__":
    main()
