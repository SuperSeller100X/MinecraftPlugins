#!/usr/bin/env python3
"""Offline consistency checks for HourGlass.

The build sandbox for this plugin has no JDK and no access to Maven Central, so
`mvn clean verify` runs in CI. That catches everything the compiler can see.
This script catches the class of bug a compiler cannot see at all: a message key,
config path, sound event, GUI action, permission or documented command that is
referenced from Java but does not exist in the shipped resource files (or the
other way round). Those are exactly the bugs that only show up when somebody
types the command on a live server.

Self-contained: no third-party modules — this file ships its own small
YAML-subset parser, because PyYAML is not guaranteed to be installed.

Run from anywhere:  python3 HourGlass/tools/check_consistency.py
Exit code 0 = clean, 1 = problems found.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java"
RES = ROOT / "src/main/resources"
README = ROOT / "README.md"

PKG = "dev/superseller/hourglass"

# Player-facing packages: they reference message keys. config/ and sound/ read
# raw YAML paths instead, so scanning them would produce false positives.
MESSAGE_PACKAGES = ("command/", "gui/", "listener/", "service/", "api/", "integration/")

failures: list[str] = []
warnings: list[str] = []
notes: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


def warn(message: str) -> None:
    warnings.append(message)


def note(message: str) -> None:
    notes.append(message)


# ---------------------------------------------------------------- mini yaml


def parse_yaml_subset(text: str) -> dict[str, str | None]:
    """Flattens a simple YAML document into ``dotted.path -> scalar``.

    Handles nested maps by indentation, quoted scalars, inline lists and
    ``- item`` entries (recorded against the parent path). That is enough for
    the four HourGlass resource files, which never use anchors or block scalars.
    List items under ``key:`` also become ``key.0``, ``key.1`` ... so entries
    such as ``commands`` inside a milestone can still be counted.
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
    counters: dict[str, int] = {}
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
                index = counters.get(prefix, 0)
                counters[prefix] = index + 1
                item = content[2:].strip()
                if ":" in item:  # a list of mappings; keep the row's keys reachable
                    key, _, value = item.partition(":")
                    row_path = f"{prefix}.{index}.{key.strip()}"
                    paths[row_path] = unquote(value) or None
                else:
                    paths[f"{prefix}.{index}"] = unquote(item)
            continue
        if ":" not in content:
            continue
        key, _, value = content.partition(":")
        key = key.strip()
        value = value.strip()
        path = f"{prefix}.{key}" if prefix else key
        paths[path] = unquote(value) if value else None
        stack.append((indent, key))
    return paths


def load_resource(name: str) -> dict[str, str | None] | None:
    path = RES / name
    if not path.is_file():
        fail(f"missing resource {name}")
        return None
    try:
        return parse_yaml_subset(path.read_text(encoding="utf-8"))
    except UnicodeDecodeError as error:
        fail(f"{name} is not valid UTF-8: {error}")
        return None


def children_of(paths: dict[str, str | None], prefix: str) -> set[str]:
    result: set[str] = set()
    for path in paths:
        if path.startswith(prefix + "."):
            result.add(path[len(prefix) + 1:].split(".")[0])
    return result


def keys_at(paths: dict[str, str | None], prefix: str) -> set[str]:
    """Leaf paths below ``prefix``, with the prefix removed."""
    return {path[len(prefix) + 1:] for path in paths if path.startswith(prefix + ".")}


# ---------------------------------------------------------------- java scan


def java_sources() -> dict[Path, str]:
    files = sorted(SRC.rglob("*.java"))
    if not files:
        fail("no Java sources found under src/main/java")
    return {path: path.read_text(encoding="utf-8") for path in files}


def relative(path: Path) -> str:
    base = SRC / PKG
    return path.relative_to(base).as_posix() if path.is_relative_to(base) else path.name


def code_only(text: str) -> str:
    """Replaces comments and literal bodies, keeping the code's own brackets.

    A regex cannot tell a quote inside a char literal from the start of a string,
    so this walks the file once — the only reliable way to count braces.
    """
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        char = text[i]
        if char == "/" and i + 1 < n and text[i + 1] == "*":
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
            continue
        if char == "/" and i + 1 < n and text[i + 1] == "/":
            end = text.find("\n", i)
            i = n if end < 0 else end
            continue
        if char == '"':
            if text[i:i + 3] == '"""':
                end = text.find('"""', i + 3)
                i = n if end < 0 else end + 3
                continue
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
            i += 1
            out.append('""')
            continue
        if char == "'":
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
            i += 1
            out.append("''")
            continue
        out.append(char)
        i += 1
    return "".join(out)


def string_literals(text: str) -> list[str]:
    """Every string and char literal body in the file, comments excluded."""
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        char = text[i]
        if char == "/" and i + 1 < n and text[i + 1] in "/*":
            if text[i + 1] == "*":
                end = text.find("*/", i + 2)
                i = n if end < 0 else end + 2
            else:
                end = text.find("\n", i)
                i = n if end < 0 else end
            continue
        if char in "'" and True or char == '"':
            quote = char
            triple = text[i:i + 3] == '"""'
            if triple:
                end = text.find('"""', i + 3)
                out.append(text[i + 3:end if end > 0 else n])
                i = n if end < 0 else end + 3
                continue
            start = i + 1
            cursor = start
            while cursor < n and text[cursor] != quote:
                cursor += 2 if text[cursor] == "\\" else 1
            out.append(text[start:cursor])
            i = cursor + 1
            continue
        i += 1
    return out


def strip_comments(text: str) -> str:
    """Drops block/line comments so commented-out code is not counted as usage.

    String literals are kept: most of the checks look at literals.
    """
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def player_facing(sources: dict[Path, str]) -> dict[Path, str]:
    return {path: text for path, text in sources.items()
            if relative(path).startswith(MESSAGE_PACKAGES)}


# -------------------------------------------------------------------- checks


def check_plugin_yml(plugin: dict[str, str | None]) -> None:
    for key in ("name", "version", "main", "api-version", "folia-supported", "description", "author"):
        if key not in plugin:
            fail(f"plugin.yml is missing '{key}'")
    if plugin.get("name") != "HourGlass":
        fail(f"plugin.yml name must be HourGlass, found {plugin.get('name')!r}")
    if plugin.get("api-version") != "'26.2'" and plugin.get("api-version") != "26.2":
        fail(f"plugin.yml api-version should be '26.2', found {plugin.get('api-version')!r}")
    if plugin.get("folia-supported") != "true":
        fail("plugin.yml must set 'folia-supported: true' — every timer goes through PlatformScheduler")
    if "emoji" in (plugin.get("name") or ""):
        fail("the plugin name must stay plain text")

    main_class = plugin.get("main") or ""
    main_path = SRC / (main_class.replace(".", "/") + ".java")
    if not main_path.is_file():
        fail(f"plugin.yml main class {main_class} has no source file")
        return
    main_source = main_path.read_text(encoding="utf-8")

    commands = sorted(children_of(plugin, "commands"))
    if not commands:
        fail("plugin.yml declares no commands")
    note(f"commands declared: {len(commands)} ({', '.join(commands)})")
    for name in commands:
        if f'bind("{name}"' not in main_source and f'getCommand("{name}")' not in main_source:
            fail(f"command '{name}' is declared but never registered in {main_path.name}")
        aliases = plugin.get(f"commands.{name}.aliases") or ""
        if not aliases.startswith("["):
            fail(f"command '{name}' has no alias list — a short form is required")
        for key in ("description", "usage", "permission", "permission-message"):
            if f"commands.{name}.{key}" not in plugin:
                fail(f"command '{name}' is missing '{key}'")
        usage = plugin.get(f"commands.{name}.usage") or ""
        if not usage.startswith("/<command>"):
            fail(f"command '{name}' usage should start with /<command>, found {usage!r}")
        permission = plugin.get(f"commands.{name}.permission") or ""
        if permission and permission not in permission_nodes(plugin):
            fail(f"command '{name}' uses undeclared permission '{permission}'")

    declared = permission_nodes(plugin)
    note(f"permissions declared: {len(declared)}")
    for path in plugin:
        if not path.startswith("permissions.") or ".children." not in path:
            continue
        rest = path[len("permissions."):]
        node, _, child = rest.partition(".children.")
        if node not in declared:
            fail(f"permission node '{node}' has children but is not declared")
        if child and child not in declared:
            fail(f"'{node}' lists unknown child permission '{child}'")

    parents = {node for node in declared if node.endswith(".*")}
    for parent in sorted(parents):
        children = {path[len(f"permissions.{parent}.children."):]
                    for path in plugin if path.startswith(f"permissions.{parent}.children.")}
        orphans = sorted(
            node for node in declared
            if node.startswith(parent[:-1]) and node != parent and "." in node[len(parent) - 1:]
            and node not in children
        )
        if orphans:
            warn(f"'{parent}' does not list these children: {', '.join(orphans)}")


def permission_nodes(plugin: dict[str, str | None]) -> set[str]:
    """Permission node names, which contain dots themselves."""
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


def check_permissions(plugin: dict[str, str | None], sources: dict[Path, str]) -> None:
    declared = permission_nodes(plugin)
    used: set[str] = set()
    patterns = (
        r'hasPermission\(\s*"[a-zA-Z0-9_.\-]*"?,\s*"(hourglass\.[a-z0-9.*\-]+)"',
        r'"(hourglass\.[a-z0-9.*\-]+)"',
    )
    for text in sources.values():
        text = strip_comments(text)
        for pattern in patterns:
            used.update(re.findall(pattern, text))
    used = {node for node in used if not node.endswith(".*")}
    missing = sorted(used - declared)
    for node in missing:
        fail(f"permission '{node}' is checked in Java but not declared in plugin.yml")
    if not missing:
        note(f"permissions checked in Java: {len(used)} (all declared)")
    for node in sorted(declared - used - {parent for parent in declared if parent.endswith(".*")}):
        if node.startswith("hourglass.") and node not in used:
            pass  # declaring more nodes than the plugin checks is fine (server-side grouping)


def check_message_keys(messages: dict[str, str | None], sources: dict[Path, str]) -> None:
    available = {path for path, value in messages.items() if value is not None}
    config = load_resource("config.yml") or {}
    gui = load_resource("gui.yml") or {}
    dotted = re.compile(r'"([a-z][a-z0-9]*(?:\.[a-z0-9\-]+)+)"')
    # The key is either the only argument or the second one, right after the
    # receiver — that keeps config paths and ph() names out of the way.
    second = re.compile(r'messages\(\)\.\w+\(\s*[A-Za-z0-9_.()]+,\s*"([a-z][a-z0-9.\-]*)"')
    first = re.compile(r'messages\(\)\.\w+\(\s*"([a-z][a-z0-9.\-]*)"')
    used: set[str] = set()
    for path, text in player_facing(sources).items():
        body = strip_comments(text)
        used.update(key for key in second.findall(body) if not key.startswith("hourglass."))
        used.update(key for key in first.findall(body) if not key.startswith("hourglass."))
        for match in dotted.finditer(body):
            key = match.group(1)
            if key.startswith("hourglass.") or key.endswith((".yml", ".java", ".version")):
                continue
            if key in config or key in gui or any(other.startswith(key + ".") for other in list(config) + list(gui)):
                if key not in available:
                    continue  # a config path, not a message key
            used.add(key)
    missing = sorted(key for key in used if key not in available)
    for key in missing:
        fail(f"message key '{key}' is used in Java but missing from messages.yml")
    if not missing:
        note(f"message keys referenced from Java: {len(used)} (all present in messages.yml)")

    # keys only reachable through a variable or a ternary still have to exist
    dynamic = [
        "prefix", "players-only", "no-permission", "usage", "internal-error", "reloaded", "reload-failed",
        "unknown-subcommand", "invalid-duration", "player-not-found",
        "admin.freeze-on", "admin.freeze-off", "admin.set-done", "admin.add-done", "admin.remove-done",
        "admin.reset-done", "milestones.reached", "milestones.broadcast", "milestones.title",
        "milestones.subtitle", "milestones.line", "milestones.line-reached", "leaderboard.line-self",
        "leaderboard.footer", "history.footer", "display.bossbar", "display.actionbar", "display.changed",
        "help.intro", "help.player", "help.admin", "gui.reset-confirm", "gui.need-player",
    ]
    for key in dynamic:
        if key not in available:
            fail(f"message key '{key}' is selected dynamically in Java but missing from messages.yml")

    never = sorted(key for key in available - used - set(dynamic)
                   if key not in ("legacy-color-codes", "escape-placeholder-values"))
    if never:
        warn(f"messages.yml keys never referenced from Java: {', '.join(never[:15])}")


def check_config_paths(config: dict[str, str | None], sources: dict[Path, str]) -> None:
    readers = {
        "HourGlassConfig.java": sources.get(SRC / PKG / "config" / "HourGlassConfig.java", ""),
        "SoundService.java": sources.get(SRC / PKG / "sound" / "SoundService.java", ""),
        "Messages.java": sources.get(SRC / PKG / "config" / "Messages.java", ""),
        "GuiConfig.java": sources.get(SRC / PKG / "config" / "GuiConfig.java", ""),
    }
    if not readers["HourGlassConfig.java"]:
        fail("HourGlassConfig.java not found; cannot verify config paths")
        return
    gui = load_resource("gui.yml") or {}
    pattern = re.compile(r'yaml\.get(?:String|Integer|Int|Long|Boolean|Double|StringList|List|Map)\(\s*"([a-z0-9.\-]+)"')
    contains = re.compile(r'yaml\.contains\(\s*"([a-z0-9.\-]+)"')
    read: set[str] = set()
    for text in readers.values():
        read.update(pattern.findall(text))
        read.update(contains.findall(text))
    # GuiConfig reads its screen layout from gui.yml, not from config.yml.
    gui_owned = {key for key in read if key in gui or any(other.startswith(key + ".") for other in gui)}
    for required in ("config-version", "general.primary-metric", "storage.history-size", "tracking.idle-seconds",
                     "leaderboard.per-page", "display.mode", "gui.fill", "export.line-endings",
                     "purge.default-days", "placeholders.enabled", "commands.allow-console", "sounds.enabled",
                     "format.style", "format.units.second", "milestones.list"):
        if required not in config:
            fail(f"config.yml is missing the documented option '{required}'")
    missing = sorted(key for key in read - gui_owned if key not in config and key not in {"display", "sounds"})
    for key in missing:
        fail(f"config path '{key}' is read in Java but missing from {name_for(key, config)}")
    if not missing:
        note(f"config paths read from Java: {len(read - gui_owned)} (all present in config.yml)")
    def covered(path: str) -> bool:
        parts = path.split(".")
        return any(".".join(parts[:i]) in read for i in range(1, len(parts)))

    unread = sorted(
        path for path, value in config.items()
        if value is not None and path not in read and not covered(path) and path.count(".") >= 1
        and not path.startswith(("commands.playtime", "permissions."))
        and not path.startswith("format.units.") and not path.startswith("sounds.events.")
        and not path.startswith("milestones.list.") and not path.endswith(".sound")
    )
    if unread:
        warn(f"config.yml options nobody reads: {', '.join(unread[:15])}")


def name_for(path: str, config: dict[str, str | None]) -> str:
    return "config.yml"


def check_enums(config: dict[str, str | None], sources: dict[Path, str]) -> None:
    """Every value HourGlassConfig normalises must be one it accepts.

    The accepted lists live in the ``norm(yaml.getString(...), "a", "b")`` calls,
    so the config file is checked against the code instead of a copy of it here.
    """
    reader = sources.get(SRC / PKG / "config" / "HourGlassConfig.java", "")
    if not reader:
        return
    pattern = re.compile(r'norm\(\s*yaml\.getString\(\s*"([a-z0-9.\-]+)"\s*(?:,\s*"[a-z\-]*")?\s*\)'
                         r'\s*,([^;]*?)\)\s*;', re.S)
    checked = 0
    for match in pattern.finditer(reader):
        path, rest = match.group(1), match.group(2)
        allowed = set(re.findall(r'"([a-z][a-z0-9\-]*)"', rest))
        if not allowed:
            continue
        checked += 1
        value = config.get(path)
        if value is None:
            continue  # falls back to the code default, which is fine
        if value.strip("\'\"") not in allowed:
            fail(f"config.yml {path}: {value!r} is not one of the values the code accepts "
                 f"({', '.join(sorted(allowed))})")
    if checked == 0:
        warn("no norm(yaml.getString(...)) calls found; enum checking is disabled")
    else:
        note(f"enum options validated against the code: {checked}")


def check_sound_events(config: dict[str, str | None], sources: dict[Path, str]) -> None:
    service = sources.get(SRC / PKG / "sound" / "SoundService.java", "")
    if not service:
        fail("SoundService.java not found")
        return
    defaults = set(re.findall(r'Map\.entry\("([a-z\-]+)",\s*"([A-Z_:a-z0-9.]+)"\)', service))
    if not defaults:
        fail("no sound defaults found in SoundService.DEFAULTS")
        return
    default_keys = {key for key, _ in defaults}
    configured = children_of(config, "sounds.events")
    if default_keys != configured:
        for key in sorted(default_keys - configured):
            fail(f"sound event '{key}' has a default but no entry under sounds.events in config.yml")
        for key in sorted(configured - default_keys):
            fail(f"config.yml defines sounds.events.{key}, which nothing plays")

    played: set[str] = set()
    for text in sources.values():
        played.update(re.findall(r'sounds\(\)\.play(?:[A-Za-z]+)?\([^;]*?"([a-z\-]+)"', strip_comments(text)))
    unknown = sorted(played - default_keys)
    for key in unknown:
        fail(f"sound key '{key}' is played in Java but is not one of the 16 configured events")
    if not unknown:
        note(f"sound keys played from Java: {len(played)} (all configured)")

    for key, sound in sorted(defaults):
        if not re.fullmatch(r"[A-Z][A-Z0-9_.]*(?::[a-z0-9_.]+)?", sound):
            fail(f"default sound for '{key}' looks wrong: {sound!r}")
        event = f"sounds.events.{key}.sound"
        if config.get(event) is None:
            fail(f"config.yml has no '{event}'")
        elif config[event] != sound:
            warn(f"config.yml overrides the default of '{key}' ({config[event]} vs {sound})")
        for suffix in ("volume", "pitch", "category", "enabled"):
            if f"sounds.events.{key}.{suffix}" not in config:
                fail(f"sounds.events.{key} is missing '{suffix}'")
            if suffix == "category" and config[f"sounds.events.{key}.category"] not in (
                    "MASTER", "MUSIC", "RECORDS", "WEATHER", "BLOCKS", "HOSTILE", "NEUTRAL", "PLAYERS",
                    "AMBIENT", "VOICE"):
                fail(f"sounds.events.{key}.category is not a SoundCategory")


def gui_screens() -> set[str]:
    gui_config = (SRC / PKG / "config" / "GuiConfig.java")
    text = gui_config.read_text(encoding="utf-8") if gui_config.is_file() else ""
    match = re.search(r'SCREENS\s*=\s*List\.of\(([^)]*)\)', text)
    return set(re.findall(r'"([a-z\-]+)"', match.group(1))) if match else set()


def check_gui(gui: dict[str, str | None], config: dict[str, str | None], sources: dict[Path, str]) -> None:
    gui_config = sources.get(SRC / PKG / "config" / "GuiConfig.java", "")
    service = sources.get(SRC / PKG / "gui" / "GuiService.java", "")
    if not gui_config or not service:
        fail("GuiConfig.java / GuiService.java not found")
        return
    declared_screens = gui_screens()
    if not declared_screens:
        fail("could not read GuiConfig.SCREENS")
        return
    configured_screens = {path for path, value in gui.items() if "." not in path and value is None}
    if configured_screens != declared_screens:
        fail(f"gui.yml screens {sorted(configured_screens)} differ from GuiConfig.SCREENS "
             f"{sorted(declared_screens)}")

    actions_block = re.search(r'ACTIONS\s*=\s*Set\.of\((.*?)\);', gui_config, re.S)
    actions = set(re.findall(r'"([a-z\-]+)"', actions_block.group(1))) if actions_block else set()
    if not actions:
        fail("could not read GuiConfig.ACTIONS")
    handled: set[str] = set()
    for block in re.findall(r'case\s+((?:"[a-z\-]+"\s*,?\s*)+)->', service):
        handled.update(re.findall(r'"([a-z\-]+)"', block))
    for action in sorted(actions - handled - {"none"}):
        fail(f"gui action '{action}' is configurable but GuiService never handles it")
    for action in sorted(handled - actions - {"none"}):
        fail(f"GuiService handles '{action}', which GuiConfig.ACTIONS does not allow")

    item_keys = {key for key in children_of(gui, "stats.items")}
    used_items: set[str] = set()
    for text in (service, sources.get(SRC / PKG / "gui" / "GuiItems.java", "")):
        used_items.update(re.findall(r'\.item\(\s*"([a-z0-9\-]+)"', text))
        used_items.update(re.findall(r'itemOrDefault\([^,]+,\s*"([a-z0-9\-]+)"', text))
    for key in sorted(used_items):
        found = [path for path in gui if re.match(r"^[a-z\-]+\.items\." + re.escape(key) + r"(\.|$)", path)]
        if not found:
            fail(f"gui.yml has no items.{key}, which GuiService looks up")

    for screen in sorted(declared_screens):
        size_raw = gui.get(f"{screen}.size")
        if size_raw is None:
            fail(f"gui.yml screen '{screen}' has no size")
            continue
        try:
            size = int(size_raw)
        except ValueError:
            fail(f"{screen}.size must be a number, found {size_raw!r}")
            continue
        if size % 9 or not 9 <= size <= 54:
            fail(f"{screen}.size must be 9..54 in rows of 9, found {size}")
        if not gui.get(f"{screen}.title"):
            fail(f"{screen}.title is empty")
        slots: dict[int, str] = {}
        for path, value in gui.items():
            match = re.match(rf"^{re.escape(screen)}\.items\.([a-z0-9\-]+)\.slot$", path)
            if match and value is not None:
                try:
                    slot = int(value)
                except ValueError:
                    fail(f"{path} must be a number, found {value!r}")
                    continue
                if not 0 <= slot < size:
                    fail(f"{path} = {slot} is outside a {size}-slot GUI")
                if slot in slots:
                    fail(f"{screen}: slots {slot} used by both '{slots[slot]}' and '{match.group(1)}'")
                slots[slot] = match.group(1)
        start = gui.get(f"{screen}.list.start-slot")
        cells = gui.get(f"{screen}.list.cells")
        if start is not None and cells is not None:
            if not 0 <= int(start) < size:
                fail(f"{screen}.list.start-slot {start} is outside the GUI")
            if int(start) + int(cells) > size:
                fail(f"{screen}.list needs {int(start)}+{int(cells)} slots but the GUI has {size}")
        for path, value in gui.items():
            if path.endswith(".material") and value:
                if not re.fullmatch(r"[A-Z][A-Z0-9_]*", value):
                    fail(f"{path} = {value!r} is not an UPPER_SNAKE Material name")
            if path.endswith(".action") and value and value not in actions:
                fail(f"{path} = {value!r} is not a known action")
        edit_buttons = 0
        for path, value in gui.items():
            if path.endswith(".action") and value in ("add-time", "remove-time"):
                edit_buttons += 1
                item = path[: -len(".action")]
                total = sum(int(gui.get(f"{item}.{unit}", "0") or "0") * scale for unit, scale in (
                    ("seconds", 1), ("minutes", 60), ("hours", 3_600), ("days", 86_400), ("weeks", 604_800)))
                if total <= 0:
                    fail(f"{item} has no duration (seconds/minutes/hours/days/weeks)")
        if screen == "admin-player" and edit_buttons < 2:
            fail("admin-player should offer both an add and a remove button")

    if config.get("gui.leaderboard-heads") is None:
        fail("config.yml must expose gui.leaderboard-heads (the GUI reads it)")


def balanced_calls(text: str, opener: str) -> list[str]:
    """Returns the argument text of every ``opener(`` call, nesting respected."""
    out: list[str] = []
    index = 0
    while True:
        start = text.find(opener, index)
        if start < 0:
            return out
        depth = 0
        cursor = start + len(opener) - 1  # the '(' itself, so the depth count starts here
        while cursor < len(text):
            char = text[cursor]
            if char == '"':
                cursor += 1
                while cursor < len(text) and text[cursor] != '"':
                    cursor += 2 if text[cursor] == "\\" else 1
            elif char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    break
            cursor += 1
        out.append(text[start + len(opener):cursor])
        index = cursor
TOKEN = re.compile(r'"([a-z0-9_\-]+)"')


def check_placeholders(sources: dict[Path, str]) -> None:
    """{tokens} in shipped text must be closed, lowercase and actually filled."""
    produced: set[str] = set()
    for path, text in sources.items():
        body = strip_comments(text)
        for call in balanced_calls(body, "ph(") + balanced_calls(body, "Map.of(") \
                + balanced_calls(body, "put(") + balanced_calls(body, "placeholders.put("):
            produced.update(TOKEN.findall(call))
        if relative(path).startswith("gui/"):
            produced.update(TOKEN.findall(body))
    if not produced:
        fail("no placeholders found in the Java sources; the check is broken")
        return
    for name in ("messages.yml", "gui.yml"):
        path = RES / name
        if not path.is_file():
            continue
        unknown: set[str] = set()
        for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            if line.lstrip().startswith("#"):
                continue
            if line.count("{") != line.count("}"):
                fail(f"{name}:{line_no}: unbalanced braces: {line.strip()}")
                continue
            for match in re.finditer(r"\{", line):
                start = match.end()
                end = line.find("}", start)
                if end < 0:
                    fail(f"{name}:{line_no}: unclosed placeholder in: {line.strip()}")
                    continue
                token = line[start:end]
                if not re.fullmatch(r"[a-z0-9_\-]+", token):
                    fail(f"{name}:{line_no}: placeholder '{{{token}}}' must be lowercase [a-z0-9_-]")
                elif token not in produced:
                    unknown.add(token)
        for token in sorted(unknown):
            warn(f"{name}: placeholder '{{{token}}}' is not obviously filled by any placeholder map")
    note(f"placeholder names known to the code: {len(produced)}")


def check_commands_and_readme(plugin: dict[str, str | None], sources: dict[Path, str]) -> None:
    """The README has to document what the plugin actually offers."""
    command_sources = {name: text for path, text in sources.items()
                       if (name := path.name) in ("PlayTimeCommand.java", "AdminCommand.java")}
    if len(command_sources) != 2:
        fail("expected PlayTimeCommand.java and AdminCommand.java")
        return
    all_subcommands: dict[str, set[str]] = {}
    for name, text in command_sources.items():
        subs = set(re.findall(r'SubCommand\.of\(\s*"([a-z\-]+)"', text))
        aliases: set[str] = set()
        for block in re.findall(r'SubCommand\.of\([^{]*?\.(aliases\("[^;]*?\))', text, re.S):
            aliases.update(re.findall(r'"([a-z?0-9\-]+)"', block))
        all_subcommands[name] = subs | aliases
        if "help" not in subs:
            fail(f"{name} has no help sub-command")
        for sub in sorted(subs):
            permission = None
            match = re.search(r'SubCommand\.of\("' + re.escape(sub) + r'"\)(.*?)\.build\(\)', text, re.S)
            if match:
                found = re.search(r'permission\("([a-z0-9.*\-]+)"\)', match.group(1))
                permission = found.group(1) if found else None
            if permission and permission not in permission_nodes(plugin):
                fail(f"{name} sub-command '{sub}' needs undeclared permission '{permission}'")
            if permission is None and sub not in ("info", "help"):
                warn(f"{name} sub-command '{sub}' has no permission guard")

    if not README.is_file():
        fail("HourGlass/README.md is missing")
        return
    readme = README.read_text(encoding="utf-8")
    if len(readme) < 4_000:
        fail(f"HourGlass/README.md is only {len(readme)} bytes; full documentation is expected")
    for name, subs in all_subcommands.items():
        for sub in sorted(subs):
            if len(sub) <= 2:
                continue  # single letters are shown next to the long form
            if sub not in readme:
                fail(f"{name} sub-command '{sub}' is not documented in README.md")
    for path in plugin:
        if path.startswith("commands.") and path.endswith(".aliases"):
            for alias in re.findall(r"[a-z0-9]+", (plugin[path] or "").strip("[]")):
                if len(alias) > 2 and alias not in readme:
                    warn(f"command alias '{alias}' ({path}) is not mentioned in README.md")
    for section in ("Install", "Commands", "Permissions", "Configuration", "Storage"):
        if section.lower() not in readme.lower():
            fail(f"README.md has no '{section}' section")


def check_packages(sources: dict[Path, str]) -> None:
    for path, text in sources.items():
        match = re.search(r"^package\s+([\w.]+);", text, re.MULTILINE)
        if not match:
            fail(f"{path.name} has no package declaration")
            continue
        for literal in string_literals(text):
            if any(ord(char) > 127 for char in literal):
                fail(f"{relative(path)} has a non-ASCII literal {literal!r}: write it as \\uXXXX escapes so a "
                     f"wrong -encoding cannot change what players see (comments may stay UTF-8)")
        expected = path.parent.relative_to(SRC).as_posix().replace("/", ".")
        if match.group(1) != expected:
            fail(f"{path.name}: package {match.group(1)} does not match folder {expected}")
        stripped = code_only(text)
        for opener, closer in (("{", "}"), ("(", ")"), ("[", "]")):
            if stripped.count(opener) != stripped.count(closer):
                fail(f"{path.name}: unbalanced '{opener}{closer}' "
                     f"({stripped.count(opener)} vs {stripped.count(closer)})")


def check_build(plugin: dict[str, str | None], config: dict[str, str | None]) -> None:
    pom = ROOT / "pom.xml"
    if not pom.is_file():
        fail("pom.xml is missing")
        return
    text = pom.read_text(encoding="utf-8")
    if "<release>25</release>" not in text and "<maven.compiler.release>25</maven.compiler.release>" not in text:
        fail("pom.xml must target Java 25")
    for dependency in ("paper-api", "junit-jupiter"):
        if dependency not in text:
            fail(f"pom.xml does not declare {dependency}")
    if "shade" in text.lower():
        fail("pom.xml must not shade anything: playtime lives in YAML files only")
    if "sqlite" in text.lower() or "mysql" in text.lower() or "hikari" in text.lower():
        fail("a database dependency crept into pom.xml")
    match = re.search(r"<paper\.api\.version>([^<]+)</paper\.api\.version>", text)
    if not match:
        fail("pom.xml should pin the paper-api version in a property")
    else:
        api = plugin.get("api-version", "").strip("'")
        if api and not match.group(1).startswith(api):
            fail(f"pom.xml paper-api {match.group(1)} does not match plugin.yml api-version {api}")
        if "26.2" not in match.group(1):
            fail(f"pom.xml paper-api {match.group(1)} is not a 26.2 build")
    for forbidden in ("${project.version}",):
        if forbidden in (config or {}).__str__():
            fail("config.yml must not contain Maven placeholders")

    workflow = ROOT / ".github/workflows/build.yml"
    if not workflow.is_file():
        fail("HourGlass/.github/workflows/build.yml is missing")
    elif "mvn -B -ntp clean verify" not in workflow.read_text(encoding="utf-8"):
        warn("the CI workflow should run 'mvn -B -ntp clean verify' like its siblings")
    drop_in = ROOT / "ci/root-workflow.yml"
    if not drop_in.is_file():
        fail("HourGlass/ci/root-workflow.yml is missing — this is the copy that turns CI on "
             "(GitHub only reads .github/workflows/ at the repository root)")
    else:
        text = drop_in.read_text(encoding="utf-8")
        if "mvn -B -ntp clean verify" not in text or "check_consistency.py" not in text:
            warn("ci/root-workflow.yml should run the consistency check and 'mvn -B -ntp clean verify'")


def check_resources(sources: dict[Path, str]) -> None:
    """Every shipped resource must be loaded, and paths must stay OS-neutral."""
    joined = "\n".join(sources.values())
    for name in ("config.yml", "messages.yml", "gui.yml"):
        if f'"{name}"' not in joined:
            fail(f'{name} is never referenced by a loader (expected YamlIO.loadDefaults(plugin, "{name}"))')
    for path, body in sources.items():
        for match in re.finditer(r'(?:new File|Path\.of|Paths\.get)\(\s*"([^"]*)"', body):
            literal = match.group(1)
            if literal.startswith("/") or re.match(r"^[A-Za-z]:[\\/]", literal):
                fail(f"{relative(path)} hardcodes an absolute path: {literal!r}")
            if "/" in literal or "\\" in literal:
                fail(f"{relative(path)} builds a path out of a string literal ({literal!r}); use Path.resolve")
        if "File.separator" in body:
            warn(f"{relative(path)} uses File.separator; Path.resolve is clearer and shorter")
        if re.search(r'getDataFolder\(\)\s*\.\s*getAbsolutePath\(\)\s*\+', body):
            fail(f"{relative(path)} concatenates the data folder onto a string; use Path.resolve")


def check_time_format_units(config: dict[str, str | None]) -> None:
    units = children_of(config, "format.units")
    expected = {"year", "month", "week", "day", "hour", "minute", "second"}
    if units != expected:
        fail(f"format.units must define exactly {sorted(expected)}, found {sorted(units)}")
    for unit in sorted(units):
        value = config.get(f"format.units.{unit}") or ""
        parts = [part for part in value.split(",") if part.strip()]
        if len(parts) < 2:
            fail(f"format.units.{unit} needs at least 'short,plural', found {value!r}")


def report() -> int:
    for message in notes:
        print(f"ok    {message}")
    for message in warnings:
        print(f"WARN  {message}")
    for message in failures:
        print(f"FAIL  {message}")
    if failures:
        print(f"\n{len(failures)} problem(s) found ({len(warnings)} warning(s))")
        return 1
    print(f"\nAll consistency checks passed ({len(warnings)} warning(s))")
    return 0


def main() -> int:
    plugin = load_resource("plugin.yml")
    config = load_resource("config.yml")
    messages = load_resource("messages.yml")
    gui = load_resource("gui.yml")
    sources = java_sources()
    if None in (plugin, config, messages, gui) or not sources:
        return report()
    print(f"HourGlass consistency check — {len(sources)} Java sources, 4 resource files")
    check_plugin_yml(plugin)
    check_permissions(plugin, sources)
    check_message_keys(messages, sources)
    check_config_paths(config, sources)
    check_sound_events(config, sources)
    check_gui(gui, config, sources)
    check_placeholders(sources)
    check_commands_and_readme(plugin, sources)
    check_time_format_units(config)
    check_packages(sources)
    check_resources(sources)
    check_build(plugin, config)
    return report()


if __name__ == "__main__":
    sys.exit(main())
