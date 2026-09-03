package dev.superseller.rapidhoppers.smoke;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import dev.superseller.rapidhoppers.command.RapidHoppersCommand;
import dev.superseller.rapidhoppers.config.Messages;

/**
 * Consistency checks that need no Minecraft server: tab-completion filtering,
 * message placeholder substitution, and cross-file agreement between
 * plugin.yml, config.yml and messages.yml.
 */
public final class CommandTest {

    public static void main(String[] args) throws IOException {
        testFilter();
        testPlaceholders();
        testResources();
        System.out.println("CommandTest: " + EngineTest.checks + " checks passed");
    }

    private static void testFilter() {
        List<String> subs = List.of("info", "i", "stats", "s", "gui", "g", "help", "h");
        EngineTest.yes(RapidHoppersCommand.filter(subs, "s").containsAll(List.of("stats", "s")),
                "prefix filter matches long and short form");
        EngineTest.eq(subs.size(), RapidHoppersCommand.filter(subs, "").size(), "empty prefix returns all");
        EngineTest.eq(0, RapidHoppersCommand.filter(subs, "zzz").size(), "no matches");
        EngineTest.yes(RapidHoppersCommand.filter(subs, "IN").contains("info"), "filter is case-insensitive");
        EngineTest.eq(subs.size(), RapidHoppersCommand.filter(subs, null).size(), "null prefix is safe");
    }

    private static void testPlaceholders() {
        String out = Messages.apply("interval {interval} speed {speed}",
                Map.of("interval", "2", "speed", "4.0"));
        EngineTest.yes("interval 2 speed 4.0".equals(out), "placeholders replaced");
        EngineTest.yes("none".equals(Messages.apply("none", null)), "null placeholder map is safe");
        EngineTest.yes("{unset}".equals(Messages.apply("{unset}", Map.of())), "unknown placeholder untouched");
    }

    private static void testResources() throws IOException {
        Path resources = Path.of("src", "main", "resources");
        String pluginYml = read(resources.resolve("plugin.yml"));
        String configYml = read(resources.resolve("config.yml"));
        String messagesYml = read(resources.resolve("messages.yml"));

        EngineTest.yes(pluginYml.contains("api-version: '26.2'"), "plugin.yml targets Minecraft 26.2");
        EngineTest.yes(pluginYml.contains("folia-supported: true"), "plugin.yml declares Folia support");
        EngineTest.yes(pluginYml.contains("main: dev.superseller.rapidhoppers.RapidHoppersPlugin"),
                "plugin.yml main class matches");
        EngineTest.yes(new File("src/main/java/dev/superseller/rapidhoppers/RapidHoppersPlugin.java").isFile(),
                "main class file exists");

        for (String alias : List.of("rh]", "rhoppers", "rha]", "rhadmin")) {
            EngineTest.yes(pluginYml.contains(alias.replace("]", "")), "alias declared: " + alias);
        }
        for (String node : List.of("rapidhoppers.use", "rapidhoppers.gui", "rapidhoppers.admin",
                "rapidhoppers.admin.reload", "rapidhoppers.admin.speed", "rapidhoppers.admin.stack",
                "rapidhoppers.admin.world", "rapidhoppers.admin.throttle", "rapidhoppers.admin.limit",
                "rapidhoppers.admin.debug")) {
            EngineTest.yes(pluginYml.contains(node), "permission declared: " + node);
        }
        for (String key : List.of("engine.interval-ticks", "items-per-transfer", "max-containers-per-chunk",
                "soft-tps", "hard-tps", "sounds:", "gui:")) {
            EngineTest.yes(configYml.contains(key.replace("engine.", "")), "config key present: " + key);
        }
        for (String key : List.of("general.no-permission".substring(8), "status.enabled".substring(7),
                "gui.item.engine-name".substring(9), "help:")) {
            EngineTest.yes(messagesYml.contains(key), "message key present: " + key);
        }
        EngineTest.yes(read(Path.of("pom.xml")).contains("<release>25</release>"), "pom targets Java 25");
        EngineTest.yes(read(Path.of("pom.xml")).contains("paper-api"), "pom depends on paper-api");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
