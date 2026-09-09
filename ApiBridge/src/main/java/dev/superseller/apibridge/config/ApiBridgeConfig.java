package dev.superseller.apibridge.config;

import dev.superseller.apibridge.api.RateLimitPolicy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.plugin.java.JavaPlugin;

public final class ApiBridgeConfig {
    private static final Pattern SHA256 = Pattern.compile("^[a-fA-F0-9]{64}$");

    private final JavaPlugin plugin;
    private boolean enabled;
    private String bindHost;
    private int port;
    private String advertisedUrl;
    private int backlog;
    private int workerThreads;
    private int maxActiveRequests;
    private int maxRequestBytes;
    private int maxHeaderBytes;
    private long requestTimeoutMillis;
    private long shutdownGraceMillis;
    private boolean requireJsonContentType;
    private boolean logRequestBodies;
    private boolean tlsEnabled;
    private String keyStorePath;
    private String keyStorePasswordEnv;
    private String keyStoreType;
    private boolean corsEnabled;
    private List<String> corsOrigins;
    private List<String> corsMethods;
    private List<String> corsHeaders;
    private int corsMaxAgeSeconds;
    private String clientsEnv;
    private List<ClientConfig> clients;
    private boolean rateLimitEnabled;
    private int rateLimitCapacity;
    private int rateLimitRefillPerMinute;
    private int maxRateLimitBuckets;
    private long bucketIdleTtlMillis;
    private int recentErrorCapacity;
    private boolean playerBankIntegrationEnabled;

    public ApiBridgeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        enabled = plugin.getConfig().getBoolean("enabled", false);
        bindHost = plugin.getConfig().getString("http.bind-host", "127.0.0.1");
        port = clamp(plugin.getConfig().getInt("http.port", 8765), 1, 65535);
        advertisedUrl = plugin.getConfig().getString("http.advertised-url", "");
        backlog = clamp(plugin.getConfig().getInt("http.backlog", 64), 1, 1024);
        workerThreads = clamp(plugin.getConfig().getInt("http.worker-threads", 4), 1, 64);
        maxActiveRequests = clamp(plugin.getConfig().getInt("http.max-active-requests", 64), 1, 4096);
        maxRequestBytes = clamp(plugin.getConfig().getInt("http.max-request-bytes", 1048576), 1024, 10 * 1024 * 1024);
        maxHeaderBytes = clamp(plugin.getConfig().getInt("http.max-header-bytes", 16384), 1024, 1024 * 1024);
        requestTimeoutMillis = clampLong(plugin.getConfig().getLong("http.request-timeout-millis", 5000), 250, 120000);
        shutdownGraceMillis = clampLong(plugin.getConfig().getLong("http.shutdown-grace-millis", 3000), 0, 30000);
        requireJsonContentType = plugin.getConfig().getBoolean("http.require-json-content-type", true);
        logRequestBodies = plugin.getConfig().getBoolean("http.log-request-bodies", false);
        tlsEnabled = plugin.getConfig().getBoolean("http.tls.enabled", false);
        keyStorePath = plugin.getConfig().getString("http.tls.key-store-path", "");
        keyStorePasswordEnv = plugin.getConfig().getString("http.tls.key-store-password-env", "APIBRIDGE_TLS_KEYSTORE_PASSWORD");
        keyStoreType = plugin.getConfig().getString("http.tls.key-store-type", "PKCS12");
        corsEnabled = plugin.getConfig().getBoolean("http.cors.enabled", false);
        corsOrigins = plugin.getConfig().getStringList("http.cors.allowed-origins");
        corsMethods = plugin.getConfig().getStringList("http.cors.allowed-methods");
        corsHeaders = plugin.getConfig().getStringList("http.cors.allowed-headers");
        corsMaxAgeSeconds = clamp(plugin.getConfig().getInt("http.cors.max-age-seconds", 600), 0, 86400);
        rateLimitEnabled = plugin.getConfig().getBoolean("rate-limit.enabled", true);
        rateLimitCapacity = clamp(plugin.getConfig().getInt("rate-limit.capacity", 60), 1, 1_000_000);
        rateLimitRefillPerMinute = clamp(plugin.getConfig().getInt("rate-limit.refill-per-minute", 60), 1, 1_000_000);
        maxRateLimitBuckets = clamp(plugin.getConfig().getInt("rate-limit.max-buckets", 2048), 64, 1_000_000);
        bucketIdleTtlMillis = clampLong(plugin.getConfig().getLong("rate-limit.bucket-idle-ttl-seconds", 900), 30, 86400) * 1000L;
        clientsEnv = plugin.getConfig().getString("authentication.clients-env", "APIBRIDGE_CLIENTS");
        clients = loadClients();
        recentErrorCapacity = clamp(plugin.getConfig().getInt("metrics.recent-error-capacity", 50), 1, 500);
        playerBankIntegrationEnabled = plugin.getConfig().getBoolean("integrations.playerbank.enabled", true);
    }

    private List<ClientConfig> loadClients() {
        List<ClientConfig> loaded = new ArrayList<>();
        for (java.util.Map<?, ?> section : plugin.getConfig().getMapList("authentication.clients")) {
            Object rawId = section.get("id");
            Object rawHash = section.get("key-sha256");
            String id = rawId == null ? "" : String.valueOf(rawId).trim();
            String hash = rawHash == null ? "" : String.valueOf(rawHash).trim();
            boolean clientEnabled = !section.containsKey("enabled") || Boolean.parseBoolean(String.valueOf(section.get("enabled")));
            Set<String> scopes = new LinkedHashSet<>();
            Object rawScopes = section.get("scopes");
            if (rawScopes instanceof Iterable<?> iterable) {
                for (Object scope : iterable) {
                    if (scope != null && !String.valueOf(scope).isBlank()) scopes.add(String.valueOf(scope).trim());
                }
            }
            RateLimitPolicy policy = null;
            Object rawRateLimit = section.get("rate-limit");
            if (rawRateLimit instanceof java.util.Map<?, ?> rl) {
                policy = RateLimitPolicy.of(toInt(rl.get("capacity"), rateLimitCapacity), toInt(rl.get("refill-per-minute"), rateLimitRefillPerMinute));
            }
            if (!id.isEmpty() && SHA256.matcher(hash).matches()) {
                loaded.add(new ClientConfig(id, hash.toLowerCase(), clientEnabled, scopes, policy));
            }
        }
        String env = clientsEnv == null ? null : System.getenv(clientsEnv);
        if (env != null && !env.isBlank()) {
            loaded.addAll(EnvClientParser.parse(env));
        }
        return List.copyOf(loaded);
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean enabled() { return enabled; }
    public String bindHost() { return bindHost; }
    public int port() { return port; }
    public String advertisedUrl() { return advertisedUrl; }
    public int backlog() { return backlog; }
    public int workerThreads() { return workerThreads; }
    public int maxActiveRequests() { return maxActiveRequests; }
    public int maxRequestBytes() { return maxRequestBytes; }
    public int maxHeaderBytes() { return maxHeaderBytes; }
    public long requestTimeoutMillis() { return requestTimeoutMillis; }
    public long shutdownGraceMillis() { return shutdownGraceMillis; }
    public boolean requireJsonContentType() { return requireJsonContentType; }
    public boolean logRequestBodies() { return logRequestBodies; }
    public boolean tlsEnabled() { return tlsEnabled; }
    public String keyStorePath() { return keyStorePath; }
    public String keyStorePasswordEnv() { return keyStorePasswordEnv; }
    public String keyStoreType() { return keyStoreType; }
    public boolean corsEnabled() { return corsEnabled; }
    public List<String> corsOrigins() { return corsOrigins; }
    public List<String> corsMethods() { return corsMethods; }
    public List<String> corsHeaders() { return corsHeaders; }
    public int corsMaxAgeSeconds() { return corsMaxAgeSeconds; }
    public List<ClientConfig> clients() { return clients; }
    public boolean rateLimitEnabled() { return rateLimitEnabled; }
    public RateLimitPolicy defaultRateLimitPolicy() { return RateLimitPolicy.of(rateLimitCapacity, rateLimitRefillPerMinute); }
    public int maxRateLimitBuckets() { return maxRateLimitBuckets; }
    public long bucketIdleTtlMillis() { return bucketIdleTtlMillis; }
    public int recentErrorCapacity() { return recentErrorCapacity; }
    public boolean playerBankIntegrationEnabled() { return playerBankIntegrationEnabled; }

    public record ClientConfig(String id, String keySha256, boolean enabled, Set<String> scopes, RateLimitPolicy rateLimitPolicy) { }
}
