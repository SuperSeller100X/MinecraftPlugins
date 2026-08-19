#!/usr/bin/env python3
"""Packages compiled classes and resources into a plugin jar (no external deps)."""
import os
import sys
import zipfile


def main():
    if len(sys.argv) != 4:
        print("usage: pack.py <classes-dir> <resources-dir> <output-jar>")
        sys.exit(1)
    classes_dir, resources_dir, out_jar = sys.argv[1], sys.argv[2], sys.argv[3]

    with zipfile.ZipFile(out_jar, "w", zipfile.ZIP_DEFLATED) as zf:
        # manifest
        zf.writestr(
            "META-INF/MANIFEST.MF",
            "Manifest-Version: 1.0\r\nCreated-By: Gifty build\r\n\r\n",
        )
        # classes
        for root, _dirs, files in os.walk(classes_dir):
            for name in sorted(files):
                if not name.endswith(".class"):
                    continue
                full = os.path.join(root, name)
                rel = os.path.relpath(full, classes_dir).replace(os.sep, "/")
                zf.write(full, rel)
        # resources (plugin.yml, config.yml, messages.yml)
        for root, _dirs, files in os.walk(resources_dir):
            for name in sorted(files):
                full = os.path.join(root, name)
                rel = os.path.relpath(full, resources_dir).replace(os.sep, "/")
                zf.write(full, rel)
    print("packaged:", out_jar)


if __name__ == "__main__":
    main()
