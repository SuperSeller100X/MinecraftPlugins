#!/usr/bin/env python3
"""Offline consistency checks for PlayerBank.

The sandbox this runs in has no JDK and no access to Maven Central, so the real
`mvn clean verify` happens in CI. These checks cover the class of bug a compiler
cannot see: a message key, config path or permission referenced from Java that
does not exist in the shipped resource files.

Run:  python3 PlayerBank/tools/check_consistency.py
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
    check_dialog_keys(sources)
    check_imports(sources)
    check_external_imports(sources)
    check_folia_safety(sources)
    return report()


def check_plugin_yml(plugin_yml: dict) -> None:
    required = ("name", "version", "main", "api-version", "folia-supported")
    for key in required:
        if key not in plugin_yml:
            fail(f"plugin.yml is missing '{key}'")
    if plugin_yml.get("api-version") != "26.2":
        fail(f"plugin.yml api-version should be '26.2', found {plugin_yml.get('api-version')!r}")
    if plugin_yml.get("folia-supported") is not True:
        fail("plugin.yml must set 'folia-supported: true' — the plugin is Folia-safe")
    main_class = plugin_yml.get("main", "")
    main_path = SRC / (main_class.replace(".", "/") + ".java")
    if not main_path.is_file():
        fail(f"plugin.yml main class {main_class} has no source at {main_path}")
    for command in plugin_yml.get("commands", {}):
        print(f"  command: /{command}")
    declared = set(plugin_yml.get("permissions", {}))
    print(f"  permissions declared: {len(declared)}")
    if "playerbank.use" not in declared:
        fail("plugin.yml does not declare playerbank.use")
    if "playerbank.gui" not in declared:
        fail("plugin.yml does not declare playerbank.gui")


def check_message_keys(messages_yml: dict, sources: dict[Path, str]) -> None:
    available = flatten(messages_yml)
    pattern = re.compile(
        r"messages(?:\(\))?\.(?:send|gui|guiList)\(\s*"
        r"(?:[A-Za-z0-9_.]+,\s*)?\"([a-z0-9._\-]+)\""
    )
    used: set[str] = set()
    for path, text in sources.items():
        for match in pattern.finditer(text):
            used.add(match.group(1))
    # Transactor produces these keys through TransferResult literals.
    transactor = sources.get(SRC / "dev/superseller/playerbank/bank/Transactor.java", "")
    for match in re.finditer(r"(?:success|failure)\(\s*\"([a-z\-]+)\"", transactor):
        used.add(match.group(1))
    dynamic = {line for line in available if line.startswith("help")}
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
    pattern = re.compile(r"\bc\.(?:get[A-Za-z]+|is[A-Za-z]+)\(\"([a-z0-9.\-]+)\"")
    used: set[str] = set()
    for path, text in sources.items():
        if path.name != "BankConfig.java":
            continue
        for match in pattern.finditer(text):
            used.add(match.group(1))
    # BankConfig builds icon paths dynamically.
    used = {key for key in used if not key.startswith("gui.chest.icons.") and not key.endswith(".")}
    missing = sorted(key for key in used if key not in available)
    if missing:
        for key in missing:
            fail(f"config path '{key}' is read in Java but missing from config.yml")
    else:
        print(f"  config paths used: {len(used)} (all present in config.yml)")
    for icon in ("info", "deposit", "deposit-all", "withdraw", "withdraw-all", "interest",
                 "logs", "log-entry", "close", "style", "previous-page", "next-page"):
        path = f"gui.chest.icons.{icon}"
        if path not in available:
            fail(f"config.yml is missing {path}")
    for path in ("gui.type", "gui.player-choice", "gui.quick-amounts",
                 "gui.chest.rows", "gui.chest.title", "gui.chest.filler-material",
                 "gui.sounds.enabled", "gui.sounds.open", "gui.sounds.click",
                 "gui.sounds.deposit", "gui.sounds.withdraw", "gui.sounds.error"):
        if path not in available:
            fail(f"config.yml is missing {path}")
    quick = config_yml.get("gui", {}).get("quick-amounts")
    if not isinstance(quick, list) or not quick:
        fail("config.yml gui.quick-amounts must be a non-empty list")
    elif len(quick) > 7:
        fail("config.yml gui.quick-amounts holds more than 7 entries")


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


def check_dialog_keys(sources: dict[Path, str]) -> None:
    """Every custom-click key the menus emit must be handled by the listener."""
    emitted: set[str] = set()
    handled: set[str] = set()
    emit_pattern = re.compile(r"Key\.key\(\"playerbank\",\s*\"([a-z_]+)\"")
    for text in sources.values():
        for match in emit_pattern.finditer(text):
            emitted.add(match.group(1))
    listener = sources.get(
        SRC / "dev/superseller/playerbank/listener/DialogMenuListener.java", "")
    for match in re.finditer(r'path(?:\.startsWith\(|\.equals\()"([a-z_]+)', listener):
        handled.add(match.group(1))
    for match in re.finditer(r'path\.startsWith\("([a-z_]+)/', listener):
        handled.add(match.group(1))
    unhandled = sorted(emitted - handled)
    if unhandled:
        for key in unhandled:
            fail(f"dialog action 'playerbank:{key}' is emitted but never handled")


def check_imports(sources: dict[Path, str]) -> None:
    """Every project class referenced from another package needs an import."""
    simple_names: dict[str, str] = {}  # SimpleName -> package
    for path in sources:
        match = re.search(r"^package\s+([\w.]+);", path.read_text(encoding="utf-8"), re.MULTILINE)
        if match:
            simple_names.setdefault(path.stem, match.group(1))

    missing = []
    for path, text in sources.items():
        match = re.search(r"^package\s+([\w.]+);", text, re.MULTILINE)
        own_package = match.group(1) if match else ""
        body = strip_pattern.sub("", text)
        imported = set(re.findall(r"^import\s+(?:static\s+)?([\w.]+);", body, re.MULTILINE))
        wildcard = {m.group(1) for m in re.finditer(r"^import\s+([\w.]+)\.\*;", body, re.MULTILINE)}
        for name, package in simple_names.items():
            if package == own_package:
                continue
            if not re.search(rf"\b{name}\b", body):
                continue
            fqcn = f"{package}.{name}"
            if fqcn not in imported and package not in wildcard:
                missing.append(f"{path.name} uses {package}.{name} without importing it")
    for message in sorted(set(missing)):
        fail(message)
    if not missing:
        print(f"  cross-package references: all {len(simple_names)} project classes imported")


# Strips comments and string literals so name scans only see real code.
strip_pattern = re.compile(
    r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"', re.DOTALL)


# Classes from java.lang never need an import.
JAVA_LANG = {
    "String", "System", "Math", "Object", "Runnable", "Thread", "Class", "Package",
    "Enum", "Record", "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface",
    "SafeVarargs", "Iterable", "Comparable", "Cloneable", "Void", "Integer", "Long",
    "Double", "Float", "Boolean", "Character", "Byte", "Short", "Number", "StringBuilder",
    "StringBuffer", "CharSequence", "Exception", "RuntimeException", "Error", "Throwable",
    "IllegalArgumentException", "IllegalStateException", "NullPointerException",
    "UnsupportedOperationException", "NumberFormatException", "ArithmeticException",
    "IndexOutOfBoundsException", "ClassCastException", "AutoCloseable", "Process",
    "Runtime", "ThreadLocal",
}

# Commonly used external classes. The vocabulary is seeded with these so a
# class nobody imports anywhere (a missing import in *every* using file) is
# still caught.
SEED_FQCNS = [
    "java.io.File", "java.io.IOException", "java.io.InputStream",
    "java.io.InputStreamReader", "java.nio.charset.StandardCharsets",
    "java.util.ArrayList", "java.util.ArrayDeque", "java.util.Deque", "java.util.HashMap",
    "java.util.HashSet", "java.util.List", "java.util.Locale", "java.util.Map", "java.util.Set",
    "java.util.UUID", "java.time.Instant", "java.time.ZoneId",
    "java.time.format.DateTimeFormatter",
    "net.kyori.adventure.key.Key", "net.kyori.adventure.text.Component",
    "net.kyori.adventure.text.TextComponent",
    "net.kyori.adventure.text.format.NamedTextColor",
    "net.kyori.adventure.text.format.TextDecoration",
    "net.kyori.adventure.text.minimessage.MiniMessage",
    "org.bukkit.Bukkit", "org.bukkit.Location", "org.bukkit.Material", "org.bukkit.NamespacedKey",
    "org.bukkit.OfflinePlayer", "org.bukkit.Registry", "org.bukkit.Sound", "org.bukkit.World",
    "org.bukkit.command.Command", "org.bukkit.command.CommandExecutor",
    "org.bukkit.command.CommandSender", "org.bukkit.command.PluginCommand",
    "org.bukkit.command.TabCompleter",
    "org.bukkit.configuration.ConfigurationSection",
    "org.bukkit.configuration.file.FileConfiguration",
    "org.bukkit.configuration.file.YamlConfiguration",
    "org.bukkit.entity.Player",
    "org.bukkit.event.EventHandler", "org.bukkit.event.Listener",
    "org.bukkit.event.inventory.InventoryClickEvent", "org.bukkit.event.inventory.InventoryDragEvent",
    "org.bukkit.inventory.Inventory", "org.bukkit.inventory.InventoryHolder",
    "org.bukkit.inventory.ItemStack", "org.bukkit.inventory.meta.ItemMeta",
    "org.bukkit.plugin.Plugin", "org.bukkit.plugin.RegisteredServiceProvider",
    "org.bukkit.plugin.java.JavaPlugin", "org.bukkit.scheduler.BukkitTask",
    "io.papermc.paper.connection.PlayerGameConnection", "io.papermc.paper.dialog.Dialog",
    "io.papermc.paper.dialog.DialogResponseView",
    "io.papermc.paper.event.player.PlayerCustomClickEvent",
    "io.papermc.paper.registry.data.dialog.ActionButton",
    "io.papermc.paper.registry.data.dialog.DialogBase",
    "io.papermc.paper.registry.data.dialog.action.DialogAction",
    "io.papermc.paper.registry.data.dialog.body.DialogBody",
    "io.papermc.paper.registry.data.dialog.input.DialogInput",
    "io.papermc.paper.registry.data.dialog.type.DialogType",
]


def check_external_imports(sources: dict[Path, str]) -> None:
    """A file using an external simple name (Player, ItemStack, ...) must
    import it — the bug class behind the missing Player import in DialogMenu.

    The vocabulary is every import across the project plus a seed list of the
    external APIs this plugin uses; java.lang names and fully-qualified usages
    (java.util.Map.of) are exempt.
    """
    vocab: dict[str, str] = {}
    for fqcn in SEED_FQCNS:
        vocab.setdefault(fqcn.rsplit(".", 1)[1], fqcn)
    for text in sources.values():
        body = strip_pattern.sub("", text)
        for m in re.finditer(r"^import\s+(?:static\s+)?([\w.]+)\s*;", body, re.MULTILINE):
            fqcn = m.group(1)
            if not fqcn.endswith(".*"):
                vocab.setdefault(fqcn.rsplit(".", 1)[1], fqcn)

    # Fully-qualified class references (java.util.Map.of) compile without an
    # import — replace whole package+classname runs so only bare simple names
    # are left to scan.
    mask_fq = re.compile(r"\b(?:[a-z_][\w]*\.)+[A-Z]\w*")
    missing = []
    for path, text in sources.items():
        body = strip_pattern.sub("", text)
        imported = set()
        for m in re.finditer(r"^import\s+(?:static\s+)?([\w.]+)\s*;", body, re.MULTILINE):
            imported.add(m.group(1))
        # Mask package / receiver prefixes (java.util., player.getScheduler().)
        # so only plain simple-name usages are left to scan.
        usage = mask_fq.sub("", body)
        own = re.search(r"^package\s+([\w.]+);", text, re.MULTILINE)
        own_pkg = own.group(1) if own else ""
        for simple, fqcn in vocab.items():
            if simple in JAVA_LANG or not re.search(rf"\b{simple}\b", usage):
                continue
            if fqcn in imported or fqcn.startswith(f"{own_pkg}."):
                continue
            missing.append(f"{path.name} uses {fqcn} without importing it")
    for message in sorted(set(missing)):
        fail(message)
    if not missing:
        print(f"  external references: all {len(vocab)} known API classes imported")


def check_folia_safety(sources: dict[Path, str]) -> None:
    """The legacy BukkitScheduler throws on Folia. Only the global region,
    async, region and entity schedulers are allowed. Player/entity
    getScheduler() is the Folia entity scheduler and fine; any
    getServer().getScheduler() or Bukkit.getScheduler() use is legacy."""
    legacy = re.compile(
        r"(?:getServer\(\)|Bukkit)\s*\.\s*getScheduler\(\)")
    offenders = []
    for path, text in sources.items():
        body = strip_pattern.sub("", text)
        if legacy.search(body):
            offenders.append(f"{path.name}: uses the legacy BukkitScheduler"
                             " — use the global region / async / entity scheduler instead")
    for message in sorted(set(offenders)):
        fail(message)
    if not offenders:
        print("  folia-safety: no legacy BukkitScheduler calls")


def report() -> int:
    for message in failures:
        print(f"FAIL: {message}")
    for message in warnings:
        print(f"warn: {message}")
    print(f"\n{'FAILED' if failures else 'OK'}: {len(failures)} failures, {len(warnings)} warnings")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
