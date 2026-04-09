from __future__ import annotations

import hashlib
import hmac
import json
import os
import shutil
import subprocess
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parent
REPO_ROOT = ROOT.parents[2]
BACKEND_JAR = REPO_ROOT / "backend" / "target" / "saas-backend-0.1.0.jar"
SECRET = os.getenv("PLUGIN_SIGNATURE_SECRET", "saas-plugin-signature-secret-dev-only").encode("utf-8")


def main() -> None:
    build_platform_backend()
    dist_dir = ROOT / "dist"
    build_dir = dist_dir / "build"
    if build_dir.exists():
        shutil.rmtree(build_dir)
    build_dir.mkdir(parents=True, exist_ok=True)
    jar_path = build_backend_jar(build_dir)
    package_dir = build_dir / "package"
    (package_dir / "backend").mkdir(parents=True, exist_ok=True)
    (package_dir / "frontend" / "assets").mkdir(parents=True, exist_ok=True)
    (package_dir / "migrations").mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / "plugin.json", package_dir / "plugin.json")
    shutil.copy2(ROOT / "frontend" / "manifest.json", package_dir / "frontend" / "manifest.json")
    shutil.copy2(ROOT / "frontend" / "assets" / "2fa-plugin.js", package_dir / "frontend" / "assets" / "2fa-plugin.js")
    shutil.copy2(ROOT / "migrations" / "V1__twofactor_init.sql", package_dir / "migrations" / "V1__twofactor_init.sql")
    shutil.copy2(jar_path, package_dir / "backend" / "plugin.jar")
    checksums = create_checksums(package_dir)
    checksums_content = json.dumps(checksums, ensure_ascii=False, indent=2)
    (package_dir / "checksums.json").write_text(checksums_content, encoding="utf-8")
    signature = hmac.new(SECRET, checksums_content.encode("utf-8"), hashlib.sha256).hexdigest()
    (package_dir / "signature.sig").write_text(signature, encoding="utf-8")
    zip_path = dist_dir / "2fa-plugin-1.0.0.zip"
    if zip_path.exists():
        zip_path.unlink()
    with ZipFile(zip_path, "w", ZIP_DEFLATED) as zip_file:
        for file_path in sorted(package_dir.rglob("*")):
            if file_path.is_file():
                zip_file.write(file_path, file_path.relative_to(package_dir))
    print(zip_path)


def build_backend_jar(build_dir: Path) -> Path:
    subprocess.run(
        ["mvn", "-q", "-DskipTests", "package"],
        check=True,
        cwd=ROOT / "backend",
    )
    jar_path = ROOT / "backend" / "target" / "twofactor-plugin-backend-1.0.0.jar"
    target_path = build_dir / "plugin.jar"
    shutil.copy2(jar_path, target_path)
    return target_path


def build_platform_backend() -> None:
    subprocess.run(
        ["mvn", "-q", "-DskipTests", "package"],
        check=True,
        cwd=REPO_ROOT / "backend",
    )
    if not BACKEND_JAR.exists():
        raise SystemExit("平台后端构建失败，未生成 saas-backend-0.1.0.jar")


def create_checksums(package_dir: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for relative_path in [
        "plugin.json",
        "backend/plugin.jar",
        "frontend/manifest.json",
        "frontend/assets/2fa-plugin.js",
        "migrations/V1__twofactor_init.sql",
    ]:
        file_path = package_dir / relative_path
        result[relative_path] = hashlib.sha256(file_path.read_bytes()).hexdigest()
    return result


if __name__ == "__main__":
    main()
