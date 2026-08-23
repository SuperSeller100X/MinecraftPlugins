#!/usr/bin/env python3
"""Package compiled classes + filtered resources into TeleportSigns-1.0.0.jar."""
from __future__ import annotations

import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent
CLASSES = pathlib.Path("/tmp/ts-build/classes")
RESOURCES = ROOT / "src" / "main" / "resources"
OUT = ROOT / "target" / "TeleportSigns-1.0.0.jar"
VERSION = "1.0.0"


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(CLASSES.rglob("*.class")):
            zf.write(path, path.relative_to(CLASSES).as_posix())
        for path in sorted(RESOURCES.rglob("*")):
            if not path.is_file():
                continue
            rel = path.relative_to(RESOURCES).as_posix()
            data = path.read_bytes()
            if path.suffix in {".yml", ".yaml", ".properties"}:
                text = data.decode("utf-8").replace("${project.version}", VERSION)
                data = text.encode("utf-8")
            zf.writestr(rel, data)
        manifest = (
            "Manifest-Version: 1.0\n"
            "Implementation-Title: TeleportSigns\n"
            "Implementation-Version: 1.0.0\n"
            "Implementation-Vendor: SuperSeller100X\n"
        )
        zf.writestr("META-INF/MANIFEST.MF", manifest)
    print("wrote", OUT, "(%d bytes)" % OUT.stat().st_size)


if __name__ == "__main__":
    main()
