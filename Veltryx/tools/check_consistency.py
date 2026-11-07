#!/usr/bin/env python3
"""Offline consistency checks for Veltryx.

The sandbox this runs in has no JDK and no access to Maven Central, so the real
`mvn clean verify` happens in CI. These checks cover the class of bug a compiler
cannot see: a message key, config path, command registration or permission
referenced from Java that does not exist in the shipped resource files (and
placeholders passed to messages that the template does not declare).

Self-contained: no third-party modules required (this file ships its own small
YAML-subset parser because PyYAML is not guaranteed to be installed).

Run:  python3 Veltryx/tools/check_consistency.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java"
RES = ROOT / "src/main/resources"

failures: list[str] = []
warnings: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


def warn(message: str) -> None:
    warnings.append(message)


# ---------------------------------------------------------------- mini yaml


def parse_yaml_keys(text: str) -> dict[str, str]:
    """Flattens simple ``key: value`` YAML into dotted paths -> raw value."""
    result: dict[str, str] = {}
    stack: list[tuple[int, str]] = []
    for raw_line in text.splitlines():
        line = raw_line.split("#", 1)[0].rstrip() if not raw_line.lstrip().startswith("#") else ""
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip())
        stripped = line.strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        match = re.match(r"^([A-Za-z0-9_.\-/\"']+):\s*(.*)$", stripped)
        if not match:
            continue
        key = match.group(1).strip().strip('"\'')
        value = match.group(2).strip()
        path = ".".join([frame for _, frame in stack] + [key])
        if value == "":
            stack.append((indent, key))
        else:
            result[path] = value
    return result


def placeholder_names(template: str) -> set[str]:
    return set(re.findall(r"\{([a-z0-9_\-]+)\}", template, flags=re.IGNORECASE))


# ------------------------------------------------------------- load sources

java_files = list(SRC.rglob("*.java"))
all_java = "\n".join(f.read_text() for f in java_files)

messages_text = (RES / "messages.yml").read_text()
message_keys = parse_yaml_keys(messages_text)

plugin_text = (RES / "plugin.yml").read_text()
plugin_yaml = parse_yaml_keys(plugin_text)

config_text = (RES / "config.yml").read_text()
config_keys = parse_yaml_keys(config_text)

# ------------------------------------------------------- message key usage

used_messages: dict[str, list[tuple[str, set[str]]]] = {}
for f in java_files:
    flat = re.sub(r"\s+", " ", f.read_text())
    for m in re.finditer(
        r"messages\.(?:send|prefixed|render|sendMultiline)\(\s*[^,()]+,\s*\"([a-z0-9.\-]+)\""
        r"(?:,\s*Map\.of\(([^)]*)\))?",
        flat,
    ):
        key, args = m.group(1), m.group(2) or ""
        placeholders = set(re.findall(r"\"([a-z0-9_\-]+)\"\s*,", args))
        used_messages.setdefault(key, []).append((f.name, placeholders))
    for m in re.finditer(r"messages\.(?:render|prefixed)\(\"([a-z0-9.\-]+)\"(?:,\s*Map\.of\(([^)]*)\))?", flat):
        key, args = m.group(1), m.group(2) or ""
        placeholders = set(re.findall(r"\"([a-z0-9_\-]+)\"\s*,", args))
        used_messages.setdefault(key, []).append((f.name, placeholders))

# dynamic ternary keys
for m in re.finditer(r"\"([a-z0-9.\-]+)\"\s*:\s*\"([a-z0-9.\-]+)\"", all_java):
    pass
for key in re.findall(r"\?\s*\"([a-z0-9.\-]+)\"\s*:\s*\"([a-z0-9.\-]+)\"", all_java):
    for k in key:
        if "." in k:  # message keys always contain a dot (no yes/no/7d false hits)
            used_messages.setdefault(k, [])
for m in re.finditer(r"notifyAll\(\"([a-z0-9.\-]+)\"", all_java):
    used_messages.setdefault(m.group(1), [])

for key, sites in sorted(used_messages.items()):
    if key not in message_keys:
        where = ", ".join(sorted({name for name, _ in sites})) or "?"
        fail(f"messages.yml is missing key '{key}' (used in {where})")

for key, sites in sorted(used_messages.items()):
    if key not in message_keys:
        continue
    template_ph = placeholder_names(message_keys[key])
    for name, passed in sites:
        missing = passed - template_ph
        if missing:
            warn(f"message '{key}' ({name}) passes placeholders not in the template: {sorted(missing)}")

for key in sorted(message_keys):
    if key == "prefix":
        continue
    if key not in used_messages and not key.startswith("ban-screen"):
        warn(f"messages.yml key '{key}' is never referenced from Java")

# ------------------------------------------------------- command wiring

command_blocks = re.findall(r"^  ([a-z0-9\-]+):\s*$", plugin_text, flags=re.MULTILINE)
registered_commands = set()
in_commands = False
for line in plugin_text.splitlines():
    if line.startswith("commands:"):
        in_commands = True
        continue
    if in_commands:
        if line and not line.startswith(" "):
            in_commands = False
            continue
        m = re.match(r"^  ([a-z0-9\-]+):\s*$", line)
        if m:
            registered_commands.add(m.group(1))

bound_commands = set(re.findall(r"bind\(\"([a-z0-9\-]+)\"", all_java))
for cmd in sorted(bound_commands):
    if cmd not in registered_commands:
        fail(f"command '{cmd}' is bound in Java but missing from plugin.yml commands")
for cmd in sorted(registered_commands):
    if cmd not in bound_commands:
        warn(f"command '{cmd}' is declared in plugin.yml but never bound in Java")

# ------------------------------------------------------- permissions

declared_permissions = set(re.findall(r"^  (veltryx[a-z0-9.*_\-]+):", plugin_text, flags=re.MULTILINE))
used_permissions = set(re.findall(r"hasPermission\(\"([a-z0-9.*_\-]+)\"", all_java))
for perm in sorted(used_permissions):
    if perm not in declared_permissions:
        fail(f"permission '{perm}' is checked in Java but not declared in plugin.yml")
for perm in sorted(declared_permissions):
    if perm.endswith(".*"):
        continue
    if perm not in used_permissions:
        warn(f"permission '{perm}' is declared in plugin.yml but never checked in Java")

# ------------------------------------------------------- config paths

config_getters = set(re.findall(r"config\.get\w+\(\"([a-z0-9.\-]+)\"", all_java))
for path in sorted(config_getters):
    if path.endswith(".*"):
        continue
    found = any(k == path or k.startswith(path + ".") for k in config_keys)
    if not found and not path.endswith("-vl") and path not in {
        "anticheat.checks",
    }:
        # values read with computed sub-paths are checked loosely
        if "." in path and path.split(".")[0] in {"anticheat", "economy", "storage",
                                                  "punishments", "moderation", "staff"}:
            warn(f"config path '{path}' is read in Java but not found in config.yml")

# ---------------------------------------------------------------- report

for message in warnings:
    print(f"WARN  {message}")
for message in failures:
    print(f"FAIL  {message}")
print(f"\n{len(failures)} failure(s), {len(warnings)} warning(s).")
sys.exit(1 if failures else 0)
