#!/usr/bin/env python3
"""Offline consistency checks for PlayerVault.

The sandbox this runs in has no JDK and no access to Maven Central, so the real
`mvn clean verify` happens in CI. These checks cover the class of bug a compiler
cannot see: a message key, config path or permission referenced from Java that does
not exist in the shipped resource files.

Run:  python3 PlayerVault/tools/check_consistency.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java"
RES = ROOT / "src/main/resources"

failures: list[str] = []
warnings: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


def warn(message: str) -> None:
    warnings.append(message)


def flatten(node, prefix: str = "") -> set[str]:
    """Collects every dotted leaf path of a YAML document."""
    paths: set[str] = set()
    if isinstance(node, dict):
        for key, value in node.items():
            path = f"{prefix}{key}"
            paths.add(path)
            paths |= flatten(value, f"{path}.")
    elif isinstance(node, list):
        paths.add(prefix.rstrip("."))
    return paths


def java_files() -> list[Path]:
    return sorted(SRC.rglob("*.java"))


def load_yaml(name: str):
    path = RES / name
    if not path.is_file():
        fail(f"missing resource {name}")
        return None
    try:
        return yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        fail(f"{name} is not valid YAML: {exc}")
        return None


def main() -> int:
    plugin_yml = load_yaml("plugin.yml")
    config_yml = load_yaml("config.yml")
    messages_yml = load_yaml("messages.yml")
    if plugin_yml is None or config_yml is None or messages_yml is None:
        return report()

    sources = {path: path.read_text(encoding="utf-8") for path in java_files()}
    print(f"checked {len(sources)} Java sources")

    check_plugin_yml(plugin_yml)
    check_message_keys(messages_yml, sources)
    check_config_paths(config_yml, sources)
    check_permissions(plugin_yml, sources)
    check_packages(sources)
    check_resource_filtering(sources)
    return report()


def check_plugin_yml(plugin_yml: dict) -> None:
    required = ("name", "version", "main", "api-version", "folia-supported")
    for key in required:
        if key not in plugin_yml:
            fail(f"plugin.yml is missing '{key}'")
    if plugin_yml.get("api-version") != "26.2":
        fail(f"plugin.yml api-version should be '26.2', found {plugin_yml.get('api-version')!r}")
    if plugin_yml.get("folia-supported") is not True:
        fail("plugin.yml must set 'folia-supported: true' for Folia")
    main_class = plugin_yml.get("main", "")
    main_path = SRC / (main_class.replace(".", "/") + ".java")
    if not main_path.is_file():
        fail(f"plugin.yml main class {main_class} has no source at {main_path}")
    for command in plugin_yml.get("commands", {}):
        print(f"  command: /{command}")
    declared = set(plugin_yml.get("permissions", {}))
    print(f"  permissions declared: {len(declared)}")
    if "playervault.use" not in declared:
        fail("plugin.yml does not declare playervault.use")


def check_message_keys(messages_yml: dict, sources: dict[Path, str]) -> None:
    available = flatten(messages_yml)
    pattern = re.compile(
        r"(?:messages\.(?:send|sendPlain|prefixed|component|raw|rawList)|messages\.raw)\(\s*"
        r"(?:[A-Za-z0-9_.]+,\s*)?\"([a-z0-9._\-]+)\""
    )
    used: set[str] = set()
    for path, text in sources.items():
        for match in pattern.finditer(text):
            used.add(match.group(1))
    # Message keys that are only ever reached through a variable prefix.
    dynamic = {line for line in available if line.startswith(("help.", "admin.help."))}
    missing = sorted(key for key in used if key not in available)
    if missing:
        for key in missing:
            fail(f"message key '{key}' is used in Java but missing from messages.yml")
    else:
        print(f"  message keys used: {len(used)} (all present in messages.yml)")
    unused = sorted(available - used - dynamic - {"prefix"})
    unused = [key for key in unused if not key.endswith((".", ""))]
    if unused:
        warn(f"messages.yml entries never referenced: {', '.join(unused[:12])}")


def check_config_paths(config_yml: dict, sources: dict[Path, str]) -> None:
    available = flatten(config_yml)
    # Only Settings.java reads config.yml. The store classes also have a local
    # variable called 'config', but that is a player's vault document.
    pattern = re.compile(r"config\.(?:get[A-Za-z]+|is[A-Za-z]+)\(\"([a-z0-9.\-]+)\"")
    used: set[str] = set()
    for path, text in sources.items():
        if path.name != "Settings.java":
            continue
        for match in pattern.finditer(text):
            used.add(match.group(1))
    # Settings builds button paths dynamically.
    used = {key for key in used if not key.endswith(".material") and not key.endswith(".slot")}
    missing = sorted(key for key in used if key not in available)
    if missing:
        for key in missing:
            fail(f"config path '{key}' is read in Java but missing from config.yml")
    else:
        print(f"  config paths used: {len(used)} (all present in config.yml)")
    for spec in ("upgrade", "sort", "deposit-all", "withdraw-all", "info",
                 "page-indicator", "previous-page", "next-page", "filler"):
        for suffix in ("material", "slot"):
            path = f"gui.buttons.{spec}.{suffix}"
            if path not in available:
                fail(f"config.yml is missing {path}")


def check_permissions(plugin_yml: dict, sources: dict[Path, str]) -> None:
    declared = set(plugin_yml.get("permissions", {}))
    pattern = re.compile(r"hasPermission\(\"([a-z0-9.*\-]+)\"\)")
    used: set[str] = set()
    for path, text in sources.items():
        for match in pattern.finditer(text):
            used.add(match.group(1))
    missing = sorted(used - declared)
    if missing:
        for node in missing:
            fail(f"permission '{node}' is checked in Java but not declared in plugin.yml")
    else:
        print(f"  permissions checked in Java: {len(used)} (all declared in plugin.yml)")
    # Wildcard children must resolve to something real.
    for node, body in plugin_yml.get("permissions", {}).items():
        if not isinstance(body, dict):
            continue
        for child in body.get("children", {}) or {}:
            if child not in declared:
                fail(f"permission '{node}' lists unknown child '{child}'")


def check_packages(sources: dict[Path, str]) -> None:
    for path, text in sources.items():
        match = re.search(r"^package\s+([\w.]+);", text, re.MULTILINE)
        if not match:
            fail(f"{path.name} has no package declaration")
            continue
        expected = path.parent.relative_to(SRC).as_posix().replace("/", ".")
        if match.group(1) != expected:
            fail(f"{path.name}: package {match.group(1)} does not match folder {expected}")
        for opener, closer in (("{", "}"), ("(", ")")):
            if text.count(opener) != text.count(closer):
                fail(f"{path.name}: unbalanced '{opener}{closer}' "
                     f"({text.count(opener)} vs {text.count(closer)})")


def check_resource_filtering(sources: dict[Path, str]) -> None:
    plugin_text = (RES / "plugin.yml").read_text(encoding="utf-8")
    if "${project.version}" not in plugin_text:
        fail("plugin.yml should use ${project.version} so Maven filtering fills it in")
    for name in ("config.yml", "messages.yml"):
        text = (RES / name).read_text(encoding="utf-8")
        for match in re.finditer(r"\$\{[^}]*\}", text):
            fail(f"{name} contains {match.group(0)}, which Maven resource filtering would replace")


def report() -> int:
    for warning in warnings:
        print(f"WARN  {warning}")
    for failure in failures:
        print(f"FAIL  {failure}")
    if failures:
        print(f"\n{len(failures)} problem(s) found")
        return 1
    print("\nAll consistency checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
