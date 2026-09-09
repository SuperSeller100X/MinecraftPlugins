package dev.superseller.apibridge.integration;

import dev.superseller.apibridge.api.ApiEndpoint;
import dev.superseller.apibridge.api.ApiResponse;
import dev.superseller.apibridge.api.ExecutionMode;
import dev.superseller.apibridge.api.HttpMethod;
import dev.superseller.apibridge.api.ValidationException;
import dev.superseller.apibridge.core.ApiManager;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerBankIntegration {
    private static final String OWNER = "ApiBridge:PlayerBank";
    private final JavaPlugin plugin;
    private final ApiManager manager;
    private boolean registered;

    public PlayerBankIntegration(JavaPlugin plugin, ApiManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void registerIfAvailable() {
        Plugin playerBank = plugin.getServer().getPluginManager().getPlugin("PlayerBank");
        if (playerBank == null || !playerBank.isEnabled()) {
            plugin.getLogger().info("api_event=integration_skipped integration=PlayerBank reason=not_loaded");
            return;
        }
        try {
            manager.registerEndpoint(ApiEndpoint.builder(OWNER, 1, HttpMethod.GET, "/playerbank/balance")
                    .requireScope("playerbank:balance:read")
                    .executionMode(ExecutionMode.GLOBAL_MINECRAFT)
                    .timeout(Duration.ofSeconds(3))
                    .description("Read an existing PlayerBank account balance by validated UUID")
                    .validator(context -> {
                        String uuid = context.firstQuery("uuid").orElse(null);
                        if (uuid == null || uuid.isBlank()) {
                            throw new ValidationException("uuid query parameter is required");
                        }
                        try {
                            UUID.fromString(uuid);
                        } catch (IllegalArgumentException e) {
                            throw new ValidationException("uuid must be a valid UUID");
                        }
                    })
                    .handler(context -> {
                        UUID uuid = UUID.fromString(context.firstQuery("uuid").orElseThrow());
                        return CompletableFuture.completedFuture(ApiResponse.ok(readBalance(playerBank, uuid)));
                    })
                    .build());
            registered = true;
            plugin.getLogger().info("api_event=integration_registered integration=PlayerBank endpoint=/api/v1/playerbank/balance");
        } catch (Exception e) {
            plugin.getLogger().warning("api_event=integration_failed integration=PlayerBank reason=\"" + safe(e.getMessage()) + "\"");
        }
    }

    public void unregister() {
        if (registered) {
            manager.unregisterOwner(OWNER);
            registered = false;
        }
    }

    private Map<String, Object> readBalance(Plugin playerBank, UUID uuid) throws Exception {
        Method storageMethod = playerBank.getClass().getMethod("storage");
        Object storage = storageMethod.invoke(playerBank);
        Object account = storage.getClass().getMethod("get", UUID.class).invoke(storage, uuid);
        if (account == null) {
            return Map.of("uuid", uuid.toString(), "exists", false, "balance", 0D);
        }
        Object lastName = account.getClass().getMethod("lastName").invoke(account);
        Object balance = account.getClass().getMethod("balance").invoke(account);
        return Map.of("uuid", uuid.toString(), "exists", true, "lastName", String.valueOf(lastName), "balance", balance);
    }

    private String safe(String message) {
        return message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
    }
}
