#!/usr/bin/env python3
"""Offline consistency checks for SwiftTPA.

The sandbox this runs in has no JDK and no access to Maven Central, so the real
`mvn clean verify` happens in CI. These checks cover the class of bug a compiler
cannot see: a message key, config path, command registration or permission
referenced from Java that does not exist in the shipped resource files.

Self-contained: no third-party modules required (this file ships its own small
YAML-subset parser because PyYAML is not guaranteed to be installed).

Run:  python3 SwiftTPA/tools/check_consistency.py
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


# --------------------------------------------------------------------- mini yaml

def parse_yaml_subset(text: str) -> dict[str, str | None]:
    """Flattens a simple ``key: value`` YAML document into dotted paths.

    Supports nested maps by indentation, quoted scalars, inline lists and
    ``- item`` lists (recorded as the parent path). That covers the three
    SwiftTPA resource files, which never use anchors, block scalars or
    multiline strings.
    """
    paths: dict[str, str | None] = {}

    def strip_comment(line: str) -> str:
        in_single = False
        in_double = False
        for i, ch in enumerate(line):
            if ch == "'" and not in_double:
                in_single = not in_single
            elif ch == '"' and not in_single:
                in_double = not in_double
            elif ch == "#" and not in_single and not in_double:
                if i == 0 or line[i - 1] in " \t":
                    return line[:i]
        return line

    def unquote(value: str) -> str:
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            return value[1:-1]
        return value

    stack: list[tuple[int, str]] = []
    for raw in text.splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        line = strip_comment(raw).rstrip()
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        content = line.strip()
        while stack and stack[-1][0] >= indent:
            stack.pop()
        prefix = ".".join(key for _, key in stack)
        if content.startswith("- "):
            if prefix:
                paths.setdefault(prefix, None)
            continue
        if ":" not in content:
            continue
        key, _, value = content.partition(":")
        key = key.strip()
        value = value.strip()
        path = f"{prefix}.{key}" if prefix else key
        if value:
            paths[path] = unquote(value)
            stack.append((indent, key))
        else:
            paths.setdefault(path, None)
            stack.append((indent, key))
    return paths


def load_resource(name: str) -> dict[str, str | None] | None:
    path = RES / name
    if not path.is_file():
        fail(f"missing resource {name}")
        return None
    return parse_yaml_subset(path.read_text(encoding="utf-8"))


def children_of(paths: dict[str, str | None], prefix: str) -> set[str]:
    """Direct children one level below ``prefix``."""
    result: set[str] = set()
    for path in paths:
        if path.startswith(prefix + "."):
            rest = path[len(prefix) + 1:]
            result.add(rest.split(".")[0])
    return result


# --------------------------------------------------------------------- java scan

def java_sources() -> dict[Path, str]:
    files = sorted(SRC.rglob("*.java"))
    if not files:
        fail("no Java sources found under src/main/java")
    return {path: path.read_text(encoding="utf-8") for path in files}


def strip_java(text: str) -> str:
    """Removes comments and string/char literal bodies for brace counting."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    text = re.sub(r'"""(?:.|\n)*?"""', '""', text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])'", "''", text)
    return text


# --------------------------------------------------------------------- checks

def check_plugin_yml(plugin: dict[str, str | None]) -> None:
    for key in ("name", "version", "main", "api-version", "folia-supported"):
        if key not in plugin:
            fail(f"plugin.yml is missing '{key}'")
    if plugin.get("api-version") != "26.2":
        fail(f"plugin.yml api-version should be '26.2', found {plugin.get('api-version')!r}")
    if plugin.get("folia-supported") != "true":
        fail("plugin.yml must set 'folia-supported: true' for Folia")
    main_class = plugin.get("main") or ""
    main_path = SRC / (main_class.replace(".", "/") + ".java")
    if not main_path.is_file():
        fail(f"plugin.yml main class {main_class} has no source at {main_path}")

    commands = sorted(children_of(plugin, "commands"))
    print(f"  commands declared: {len(commands)} ({', '.join(commands)})")
    main_source = main_path.read_text(encoding="utf-8") if main_path.is_file() else ""
    for name in commands:
        if f'bind("{name}"' not in main_source and f'getCommand("{name}")' not in main_source:
            fail(f"command '{name}' is declared in plugin.yml but never registered in the main class")
        if not plugin.get(f"commands.{name}.aliases"):
            fail(f"command '{name}' has no aliases — every command needs a short form")

    declared = permission_nodes(plugin)
    print(f"  permissions declared: {len(declared)}")
    for path in plugin:
        if ".children." not in path or not path.startswith("permissions."):
            continue
        rest = path[len("permissions."):]
        node, _, child = rest.partition(".children.")
        if node not in declared:
            fail(f"permission node '{node}' has children but is not declared")
        if child and child not in declared:
            fail(f"permission '{node}' lists unknown child '{child}'")


def permission_nodes(plugin: dict[str, str | None]) -> set[str]:
    """Collects full permission node names (they contain dots themselves)."""
    nodes: set[str] = set()
    for path in plugin:
        if not path.startswith("permissions."):
            continue
        rest = path[len("permissions."):]
        if ".children." in rest:
            nodes.add(rest.split(".children.", 1)[0])
        elif rest.endswith((".description", ".default", ".children")):
            nodes.add(rest.rsplit(".", 1)[0])
        else:
            nodes.add(rest)
    return nodes


def check_message_keys(messages: dict[str, str | None], sources: dict[Path, str]) -> None:
    available = set(messages)
    pattern = re.compile(
        r"messages\(\)\.(?:send|component|componentNoPrefix|raw|stringList|deserialize)\(\s*"
        r"(?:[A-Za-z0-9_.()]+,\s*)?\"([a-z0-9.\-]+)\""
    )
    used: set[str] = set()
    for text in sources.values():
        used.update(pattern.findall(text))
    for text in sources.values():
        for section in re.findall(r'\.sendHelp\([^,]+,\s*"([a-z]+)"', text):
            if f"help.{section}" not in available:
                fail(f"help section help.{section} is used in Java but missing from messages.yml")
    missing = sorted(key for key in used if key not in available)
    for key in missing:
        fail(f"message key '{key}' is used in Java but missing from messages.yml")
    if not missing:
        print(f"  literal message keys used: {len(used)} (all present in messages.yml)")
    dynamic = {"prefix"} | {k for k in available if k.startswith(("help.", "gui."))}
    variable_keys = (
        "request-sent-tpa", "request-sent-here", "request-received-tpa", "request-received-here",
        "list-incoming-tpa", "list-incoming-here", "list-outgoing-tpa", "list-outgoing-here",
        "stats-self", "stats-other", "warmup-cancelled-moved", "warmup-cancelled-damage",
        "warmup-cancelled", "toggle-on", "toggle-off",
        "spy-send", "spy-accept", "spy-deny", "spy-cancel", "spy-expire", "spy-teleport",
        "admin.spy-on", "admin.spy-off", "admin.cleared-all",
    )
    for key in variable_keys:
        if key not in available:
            fail(f"message key '{key}' is referenced via a variable in Java but missing from messages.yml")
    unused = sorted(k for k in available - used - dynamic if messages[k] is not None)
    unused = [k for k in unused if k not in variable_keys]
    if unused:
        warn(f"messages.yml entries never referenced: {', '.join(unused[:12])}")


def check_config_paths(config: dict[str, str | None], sources: dict[Path, str]) -> None:
    available = set(config)
    used: set[str] = set()
    pattern = re.compile(r'config\(\)\.get(?:String|Long|Int|Boolean|Double|StringList)\(\s*"([a-z0-9.\-]+)"')
    for path, text in sources.items():
        if path.name != "SwiftTpaConfig.java":
            continue
        used.update(pattern.findall(text))
    used = {key for key in used if not key.endswith(".")}
    missing = sorted(key for key in used if key not in available)
    for key in missing:
        fail(f"config path '{key}' is read in SwiftTpaConfig but missing from config.yml")
    if not missing:
        print(f"  config paths used: {len(used)} (all present in config.yml)")
    for icon in ("filling", "outgoing-cancel", "toggle-on", "toggle-off"):
        if f"gui.icons.{icon}" not in available:
            fail(f"config.yml is missing gui.icons.{icon}")


def check_sound_events(config: dict[str, str | None], sources: dict[Path, str]) -> None:
    pattern = re.compile(r'Sounds\.play\(\s*[^,]+,\s*\w+,\s*"([a-z\-]+)"')
    events: set[str] = set()
    for text in sources.values():
        events.update(pattern.findall(text))
    if not config.get("sounds.enabled"):
        fail("config.yml should ship sounds.enabled: true by default")
    for event in sorted(events):
        if f"sounds.{event}" not in config:
            fail(f"sound event '{event}' is played in Java but sounds.{event} is missing from config.yml")
    print(f"  sound events played: {len(events)} (all configured)")


def check_permissions(plugin: dict[str, str | None], sources: dict[Path, str]) -> None:
    declared = permission_nodes(plugin)
    used: set[str] = set()
    for text in sources.values():
        used.update(re.findall(r'hasPermission\(\s*"([a-z0-9.*\-]+)"', text))
        used.update(re.findall(r'requirePermission\(\s*\w+,\s*"([a-z0-9.*\-]+)"', text))
    missing = sorted(used - declared)
    for node in missing:
        fail(f"permission '{node}' is checked in Java but not declared in plugin.yml")
    if not missing:
        print(f"  permissions checked in Java: {len(used)} (all declared in plugin.yml)")


def check_packages(sources: dict[Path, str]) -> None:
    for path, text in sources.items():
        match = re.search(r"^package\s+([\w.]+);", text, re.MULTILINE)
        if not match:
            fail(f"{path.name} has no package declaration")
            continue
        expected = path.parent.relative_to(SRC).as_posix().replace("/", ".")
        if match.group(1) != expected:
            fail(f"{path.name}: package {match.group(1)} does not match folder {expected}")
        stripped = strip_java(text)
        for opener, closer in (("{", "}"), ("(", ")")):
            if stripped.count(opener) != stripped.count(closer):
                fail(f"{path.name}: unbalanced '{opener}{closer}' "
                     f"({stripped.count(opener)} vs {stripped.count(closer)})")


def check_resource_filtering(sources: dict[Path, str]) -> None:
    plugin_text = (RES / "plugin.yml").read_text(encoding="utf-8")
    if "${project.version}" not in plugin_text:
        fail("plugin.yml should use ${project.version} so Maven filtering fills it in")
    for name in ("config.yml", "messages.yml"):
        text = (RES / name).read_text(encoding="utf-8")
        for match in re.finditer(r"\$\{[^}]*\}", text):
            fail(f"{name} contains {match.group(0)}, which Maven resource filtering would replace")


def report() -> int:
    for message in warnings:
        print(f"WARN  {message}")
    for message in failures:
        print(f"FAIL  {message}")
    if failures:
        print(f"\n{len(failures)} problem(s) found")
        return 1
    print("\nAll consistency checks passed")
    return 0


def main() -> int:
    plugin_yml = load_resource("plugin.yml")
    config_yml = load_resource("config.yml")
    messages_yml = load_resource("messages.yml")
    sources = java_sources()
    if plugin_yml is None or config_yml is None or messages_yml is None or not sources:
        return report()
    print(f"checked {len(sources)} Java sources")
    check_plugin_yml(plugin_yml)
    check_message_keys(messages_yml, sources)
    check_config_paths(config_yml, sources)
    check_sound_events(config_yml, sources)
    check_permissions(plugin_yml, sources)
    check_packages(sources)
    check_resource_filtering(sources)
    return report()


if __name__ == "__main__":
    sys.exit(main())
