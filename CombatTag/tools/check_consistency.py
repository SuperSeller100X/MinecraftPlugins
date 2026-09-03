#!/usr/bin/env python3
"""Offline consistency checks for CombatTag.

Verifies, without a network connection or a JDK, that:
  * every message key used from Java exists in messages.yml
  * every permission checked in Java is declared in plugin.yml
  * every config path read in Java exists in config.yml
  * plugin.yml / pom.xml agree on the main class and Java/API versions

Run with:  python3 tools/check_consistency.py
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src" / "main" / "java"
RES = ROOT / "src" / "main" / "resources"

errors: list[str] = []


def yaml_keys(path: pathlib.Path) -> set[str]:
    """Collects dotted keys from a simple, well-indented YAML file."""
    keys: set[str] = set()
    stack: list[tuple[int, str]] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#") or raw.lstrip().startswith("- "):
            continue
        match = re.match(r"^(\s*)([A-Za-z0-9_.*\-]+):(.*)$", raw)
        if not match:
            continue
        indent, key, _ = match.group(1), match.group(2), match.group(3)
        depth = len(indent)
        while stack and stack[-1][0] >= depth:
            stack.pop()
        stack.append((depth, key))
        keys.add(".".join(part for _, part in stack))
    return keys


def java_sources() -> list[pathlib.Path]:
    return sorted(SRC.rglob("*.java"))


def main() -> int:
    message_keys = yaml_keys(RES / "messages.yml")
    config_keys = yaml_keys(RES / "config.yml")
    plugin_yml = (RES / "plugin.yml").read_text(encoding="utf-8")
    pom = (ROOT / "pom.xml").read_text(encoding="utf-8")

    used_messages: set[str] = set()
    used_permissions: set[str] = set()
    used_config: set[str] = set()

    for file in java_sources():
        text = file.read_text(encoding="utf-8")
        used_messages |= set(re.findall(r'messages\.(?:send|get|getList|getRaw)\([^,\)]*?"([a-z0-9.\-]+)"', text))
        used_permissions |= set(re.findall(r'hasPermission\("([a-z0-9.*\-]+)"\)', text))
        used_permissions |= set(re.findall(r'requirePerm\([^,]+,\s*"([a-z0-9.\-]+)"', text))
        used_config |= set(re.findall(r'c\.get\w+\("([a-z0-9.\-]+)"', text))

    for key in sorted(used_messages):
        if key.endswith("."):
            continue
        # dynamic keys of the shape "blocked." + action.id()
        if key == "blocked":
            continue
        if key not in message_keys:
            errors.append(f"messages.yml is missing key: {key}")

    for action_id in ("shop", "teleport", "easymending", "command", "pearl"):
        if f"blocked.{action_id}" not in message_keys:
            errors.append(f"messages.yml is missing key: blocked.{action_id}")

    for perm in sorted(used_permissions):
        if perm.endswith("*"):
            perm_key = perm
        else:
            perm_key = perm
        if f"  {perm_key}:" not in plugin_yml:
            errors.append(f"plugin.yml is missing permission: {perm}")

    for path in sorted(used_config):
        if path not in config_keys:
            errors.append(f"config.yml is missing path: {path}")

    if "dev.superseller.combattag.CombatTagPlugin" not in plugin_yml:
        errors.append("plugin.yml main class does not point at CombatTagPlugin")
    if "api-version: '26.2'" not in plugin_yml:
        errors.append("plugin.yml api-version must be 26.2")
    if "folia-supported: true" not in plugin_yml:
        errors.append("plugin.yml must declare folia-supported: true")
    if "<release>25</release>" not in pom:
        errors.append("pom.xml must compile with Java release 25")
    if "26.2" not in pom:
        errors.append("pom.xml must depend on the Paper 26.2 API")

    for path in (RES / "config.yml", RES / "messages.yml", RES / "plugin.yml"):
        if not path.exists():
            errors.append(f"missing resource: {path.name}")

    if errors:
        print("CombatTag consistency check FAILED:")
        for error in errors:
            print(f"  - {error}")
        return 1

    print(
        "CombatTag consistency check passed: "
        f"{len(list(java_sources()))} Java files, "
        f"{len(used_messages)} message keys, "
        f"{len(used_permissions)} permissions, "
        f"{len(used_config)} config paths."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
