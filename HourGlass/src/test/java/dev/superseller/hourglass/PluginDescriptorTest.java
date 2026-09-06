package dev.superseller.hourglass;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads the real {@code plugin.yml} that is packed into the jar and checks the
 * parts that silently break a server startup: a typo in {@code main:}, a
 * command whose permission does not exist, a missing {@code api-version}.
 */
class PluginDescriptorTest {

    private static final String RESOURCE = "/plugin.yml";

    private static PluginDescriptionFile descriptor() throws Exception {
        try (InputStream in = PluginDescriptorTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "plugin.yml must be on the classpath (src/main/resources/plugin.yml)");
            return new PluginDescriptionFile(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static YamlConfiguration raw() throws Exception {
        try (Reader reader = new InputStreamReader(
                PluginDescriptorTest.class.getResourceAsStream(RESOURCE), StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        }
    }

    @Test
    @DisplayName("plugin.yml parses and identifies the plugin")
    void identity() throws Exception {
        PluginDescriptionFile description = descriptor();
        assertEquals("HourGlass", description.getName());
        assertEquals("dev.superseller.hourglass.HourGlassPlugin", description.getMain());
        assertTrue(description.getAPIVersion().startsWith("26.2"),
                "api-version must match the paper-api version in pom.xml, was: " + description.getAPIVersion());
        assertFalse(description.getVersion().isBlank());
        assertFalse(description.getVersion().contains("$"),
                "${project.version} was not filtered — resource filtering must stay on");
        assertTrue(List.of("SuperSeller100X").containsAll(description.getAuthors()),
                String.valueOf(description.getAuthors()));
        assertFalse(description.getDescription().isBlank());
    }

    @Test
    @DisplayName("the plugin declares itself Folia-ready and needs no dependencies")
    void foliaAndDependencies() throws Exception {
        YamlConfiguration yaml = raw();
        assertEquals(Boolean.TRUE, yaml.get("folia-supported"),
                "every timer goes through PlatformScheduler, so Folia must be allowed");
        assertTrue(yaml.getStringList("depend").isEmpty(),
                "only softdepend on PlaceholderAPI, never a hard dependency");
        assertEquals(List.of("PlaceholderAPI"), yaml.getStringList("softdepend"));
        assertEquals("HourGlass", yaml.getString("name"));
        assertTrue(String.valueOf(yaml.get("description")).toLowerCase(java.util.Locale.US).contains("playtime"));
    }

    @Test
    @DisplayName("both commands are complete, with short aliases")
    void commands() throws Exception {
        PluginDescriptionFile description = descriptor();
        Map<String, Map<String, Object>> commands = description.getCommands();
        assertNotNull(commands);
        assertTrue(commands.containsKey("playtime"), commands.keySet().toString());
        assertTrue(commands.containsKey("playtimeadmin"), commands.keySet().toString());

        Set<String> allAliases = new HashSet<>();
        for (Map.Entry<String, Map<String, Object>> entry : commands.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> body = entry.getValue();
            for (String key : List.of("description", "usage", "permission", "aliases")) {
                assertTrue(body.containsKey(key), name + " is missing '" + key + "'");
            }
            assertTrue(String.valueOf(body.get("description")).length() > 10, name + " description too short");
            String usage = String.valueOf(body.get("usage"));
            assertTrue(usage.startsWith("/<command>") || usage.startsWith("/" + name),
                    name + " usage should start with the command: " + usage);
            assertTrue(usage.contains("["), name + " usage should document its sub-commands");
            assertTrue(allAliases.add(name), name + " is used twice");
            Object aliases = body.get("aliases");
            assertTrue(aliases instanceof List<?>, name + " aliases must be a list");
            for (Object alias : (List<?>) aliases) {
                String text = String.valueOf(alias);
                assertTrue(allAliases.add(text), text + " is claimed by two commands");
                assertFalse(text.equalsIgnoreCase(name), name + " must not alias itself");
                assertTrue(text.matches("[a-z0-9]+"), "aliases stay lowercase alnum: " + text);
            }
            PermissionDefault defaultPermission = null;
            String permission = String.valueOf(body.get("permission"));
            for (Permission declared : description.getPermissions()) {
                if (declared.getName().equals(permission)) {
                    defaultPermission = declared.getDefault();
                }
            }
            assertNotNull(defaultPermission, name + " uses an undeclared permission: " + permission);
        }
        // The documented shortcuts for /playtime.
        Map<String, Object> playtime = commands.get("playtime");
        assertTrue(((List<?>) playtime.get("aliases")).stream().map(String::valueOf).toList().contains("pt"),
                "/pt must exist, it is in every message");
    }

    @Test
    @DisplayName("permissions are documented, defaulted and their children resolve")
    void permissions() throws Exception {
        PluginDescriptionFile description = descriptor();
        assertFalse(description.getPermissions().isEmpty());
        Set<String> declared = new HashSet<>();
        for (Permission permission : description.getPermissions()) {
            declared.add(permission.getName());
        }
        for (Permission permission : description.getPermissions()) {
            assertFalse(permission.getDescription() == null || permission.getDescription().isBlank(),
                    permission.getName() + " needs a description");
            assertNotNull(permission.getDefault(), permission.getName() + " needs a default");
            if (permission.getDefault() == PermissionDefault.TRUE) {
                assertTrue(permission.getName().startsWith("hourglass."),
                        "everything granted to everyone must live under hourglass.");
            }
            for (String child : permission.getChildren().keySet()) {
                assertTrue(declared.contains(child),
                        permission.getName() + " declares " + child + " as a child but it is not defined");
            }
        }
        for (String required : List.of("hourglass.use", "hourglass.admin.reload", "hourglass.admin.edit",
                "hourglass.see.others")) {
            assertTrue(declared.contains(required), "missing permission: " + required);
        }
        assertTrue(declared.stream().anyMatch(node -> node.endsWith(".*")),
                "the admin parent nodes must exist so a server can grant whole groups");
    }

    @Test
    @DisplayName("every shipped resource is in the jar")
    void resources() {
        for (String name : List.of("/plugin.yml", "/config.yml", "/messages.yml", "/gui.yml")) {
            assertNotNull(PluginDescriptorTest.class.getResourceAsStream(name), "missing resource " + name);
        }
    }
}
