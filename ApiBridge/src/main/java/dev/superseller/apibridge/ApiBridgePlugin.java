package dev.superseller.apibridge;

import dev.superseller.apibridge.api.ApiEndpoint;
import dev.superseller.apibridge.api.ApiResponse;
import dev.superseller.apibridge.api.ExecutionMode;
import dev.superseller.apibridge.api.HttpMethod;
import dev.superseller.apibridge.command.ApiBridgeCommand;
import dev.superseller.apibridge.config.ApiBridgeConfig;
import dev.superseller.apibridge.core.ApiErrorLog;
import dev.superseller.apibridge.core.ApiManager;
import dev.superseller.apibridge.core.ApiMetrics;
import dev.superseller.apibridge.core.AuthManager;
import dev.superseller.apibridge.core.TokenBucketRateLimiter;
import dev.superseller.apibridge.http.ApiHttpServer;
import dev.superseller.apibridge.integration.PlayerBankIntegration;
import dev.superseller.apibridge.scheduler.PlatformScheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ApiBridgePlugin extends JavaPlugin {
    private ApiBridgeConfig bridgeConfig;
    private ApiManager apiManager;
    private ApiHttpServer httpServer;
    private PlayerBankIntegration playerBankIntegration;
    private Instant startedAt;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bridgeConfig = new ApiBridgeConfig(this);
        bridgeConfig.load();
        startServices();
        PluginCommand command = getCommand("apibridge");
        if (command != null) {
            ApiBridgeCommand executor = new ApiBridgeCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        stopServices();
    }

    public void reloadBridge() {
        stopServices();
        reloadConfig();
        bridgeConfig.load();
        startServices();
    }

    private void startServices() {
        ExecutorService asyncExecutor = Executors.newFixedThreadPool(Math.max(2, bridgeConfig.workerThreads()), task -> {
            Thread thread = new Thread(task, "ApiBridge-Async");
            thread.setDaemon(true);
            return thread;
        });
        apiManager = new ApiManager(
                new AuthManager(bridgeConfig.clients()),
                new TokenBucketRateLimiter(bridgeConfig.maxRateLimitBuckets(), bridgeConfig.bucketIdleTtlMillis()),
                bridgeConfig.defaultRateLimitPolicy(),
                bridgeConfig.rateLimitEnabled(),
                asyncExecutor,
                new PlatformScheduler(this),
                getLogger(),
                new ApiMetrics(),
                new ApiErrorLog(bridgeConfig.recentErrorCapacity()));
        apiManager.start();
        getServer().getServicesManager().register(dev.superseller.apibridge.api.MinecraftApiBridge.class, apiManager, this, ServicePriority.Normal);
        registerBuiltInEndpoints();
        if (bridgeConfig.playerBankIntegrationEnabled()) {
            playerBankIntegration = new PlayerBankIntegration(this, apiManager);
            playerBankIntegration.registerIfAvailable();
        }
        if (bridgeConfig.enabled()) {
            httpServer = new ApiHttpServer(bridgeConfig, apiManager, getLogger());
            try {
                httpServer.start();
                startedAt = Instant.now();
            } catch (Exception e) {
                getLogger().severe("ApiBridge HTTP server failed to start: " + safe(e.getMessage()));
                apiManager.shutdown(bridgeConfig.shutdownGraceMillis());
            }
        } else {
            getLogger().info("ApiBridge is disabled by config. Registered endpoint API remains available for diagnostics only.");
        }
    }

    private void stopServices() {
        if (playerBankIntegration != null) {
            playerBankIntegration.unregister();
            playerBankIntegration = null;
        }
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        if (apiManager != null) {
            getServer().getServicesManager().unregister(dev.superseller.apibridge.api.MinecraftApiBridge.class, apiManager);
            apiManager.shutdown(bridgeConfig == null ? 0 : bridgeConfig.shutdownGraceMillis());
            apiManager = null;
        }
    }

    private void registerBuiltInEndpoints() {
        try {
            apiManager.registerEndpoint(ApiEndpoint.builder("ApiBridge", 1, HttpMethod.GET, "/status")
                    .requireScope("status:read")
                    .executionMode(ExecutionMode.HTTP_WORKER)
                    .timeout(Duration.ofSeconds(2))
                    .description("Read API bridge status and high-level server state")
                    .handler(context -> java.util.concurrent.CompletableFuture.completedFuture(ApiResponse.ok(Map.of(
                            "enabled", bridgeConfig.enabled(),
                            "running", apiManager.isRunning(),
                            "bindHost", bridgeConfig.bindHost(),
                            "port", bridgeConfig.port(),
                            "advertisedUrl", bridgeConfig.advertisedUrl(),
                            "tlsEnabled", bridgeConfig.tlsEnabled(),
                            "registeredEndpoints", apiManager.endpoints().size(),
                            "configuredClients", apiManager.authManager().configuredClientCount(),
                            "startedAt", startedAt == null ? "" : startedAt.toString()))))
                    .build());
            apiManager.registerEndpoint(ApiEndpoint.builder("ApiBridge", 1, HttpMethod.GET, "/metrics")
                    .requireScope("metrics:read")
                    .executionMode(ExecutionMode.HTTP_WORKER)
                    .timeout(Duration.ofSeconds(2))
                    .description("Read API bridge request metrics")
                    .handler(context -> java.util.concurrent.CompletableFuture.completedFuture(ApiResponse.ok(apiManager.metrics().snapshot())))
                    .build());
        } catch (Exception e) {
            getLogger().severe("Could not register built-in ApiBridge endpoints: " + safe(e.getMessage()));
        }
    }

    private String safe(String message) {
        return message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
    }

    public ApiBridgeConfig bridgeConfig() { return bridgeConfig; }
    public ApiManager apiManager() { return apiManager; }
}
