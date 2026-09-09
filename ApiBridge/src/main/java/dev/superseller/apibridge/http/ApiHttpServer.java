package dev.superseller.apibridge.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.superseller.apibridge.api.HttpMethod;
import dev.superseller.apibridge.config.ApiBridgeConfig;
import dev.superseller.apibridge.core.ApiManager;
import dev.superseller.apibridge.core.DispatchResult;
import dev.superseller.apibridge.core.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public final class ApiHttpServer {
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final ApiBridgeConfig config;
    private final ApiManager manager;
    private final Logger logger;
    private HttpServer server;
    private ExecutorService httpExecutor;
    private Semaphore activeLimiter;

    public ApiHttpServer(ApiBridgeConfig config, ApiManager manager, Logger logger) {
        this.config = config;
        this.manager = manager;
        this.logger = logger;
    }

    public void start() throws Exception {
        InetSocketAddress address = new InetSocketAddress(config.bindHost(), config.port());
        server = config.tlsEnabled() ? createHttps(address) : HttpServer.create(address, config.backlog());
        activeLimiter = new Semaphore(config.maxActiveRequests());
        server.createContext("/", new Handler());
        httpExecutor = Executors.newFixedThreadPool(config.workerThreads(), task -> {
            Thread thread = new Thread(task, "ApiBridge-HTTP");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(httpExecutor);
        server.start();
        logger.info("api_event=http_started bind=" + config.bindHost() + ':' + config.port() + " tls=" + config.tlsEnabled());
    }

    public void stop() {
        if (server != null) {
            server.stop((int) Math.ceil(config.shutdownGraceMillis() / 1000D));
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
        }
        logger.info("api_event=http_stopped");
    }

    private HttpsServer createHttps(InetSocketAddress address) throws Exception {
        if (config.keyStorePath() == null || config.keyStorePath().isBlank()) {
            throw new IllegalStateException("TLS is enabled but no key-store-path is configured");
        }
        String password = System.getenv(config.keyStorePasswordEnv());
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("TLS is enabled but keystore password environment variable is not set");
        }
        KeyStore keyStore = KeyStore.getInstance(config.keyStoreType());
        try (InputStream in = java.nio.file.Files.newInputStream(Path.of(config.keyStorePath()))) {
            keyStore.load(in, password.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        HttpsServer https = HttpsServer.create(address, config.backlog());
        https.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        return https;
    }

    private final class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            boolean acquired = activeLimiter.tryAcquire();
            if (!acquired) {
                writeEnvelope(exchange, 503, requestId(exchange.getRequestHeaders()), "SERVICE_UNAVAILABLE", "Too many active requests");
                return;
            }
            try {
                handleLimited(exchange);
            } finally {
                activeLimiter.release();
            }
        }

        private void handleLimited(HttpExchange exchange) throws IOException {
            String methodName = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            if (config.corsEnabled()) {
                applyCors(exchange);
            }
            if ("OPTIONS".equals(methodName)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            HttpMethod method;
            try {
                method = HttpMethod.parse(methodName);
            } catch (IllegalArgumentException e) {
                writeEnvelope(exchange, 405, requestId(exchange.getRequestHeaders()), "METHOD_NOT_ALLOWED", "Unsupported HTTP method");
                return;
            }
            if (!exchange.getRequestURI().getPath().startsWith("/api/")) {
                writeEnvelope(exchange, 404, requestId(exchange.getRequestHeaders()), "NOT_FOUND", "No endpoint is registered for this route");
                return;
            }
            if (headersTooLarge(exchange.getRequestHeaders())) {
                writeEnvelope(exchange, 431, requestId(exchange.getRequestHeaders()), "HEADERS_TOO_LARGE", "Request headers are too large");
                return;
            }
            Object jsonBody = null;
            if (BODY_METHODS.contains(methodName)) {
                String contentType = firstHeader(exchange.getRequestHeaders(), "Content-Type");
                boolean hasBody = exchange.getRequestHeaders().containsKey("Content-Length") || contentType != null;
                if (config.requireJsonContentType() && hasBody && (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json"))) {
                    writeEnvelope(exchange, 415, requestId(exchange.getRequestHeaders()), "UNSUPPORTED_CONTENT_TYPE", "Expected application/json");
                    return;
                }
                byte[] raw = readLimited(exchange.getRequestBody(), config.maxRequestBytes());
                if (raw == null) {
                    writeEnvelope(exchange, 413, requestId(exchange.getRequestHeaders()), "REQUEST_TOO_LARGE", "Request body is too large");
                    return;
                }
                if (raw.length > 0) {
                    try {
                        jsonBody = Json.parse(new String(raw, StandardCharsets.UTF_8));
                    } catch (Json.JsonParseException e) {
                        writeEnvelope(exchange, 400, requestId(exchange.getRequestHeaders()), "MALFORMED_JSON", "Request body is not valid JSON");
                        return;
                    }
                }
            }
            URI uri = exchange.getRequestURI();
            DispatchResult result = manager.dispatch(method, uri.getPath(), parseQuery(uri.getRawQuery()), lowerHeaders(exchange.getRequestHeaders()), safeRemote(exchange), jsonBody, config.requestTimeoutMillis());
            for (Map.Entry<String, String> header : result.headers().entrySet()) {
                exchange.getResponseHeaders().set(header.getKey(), header.getValue());
            }
            byte[] bytes = result.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(result.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private void applyCors(HttpExchange exchange) {
        String origin = firstHeader(exchange.getRequestHeaders(), "Origin");
        if (origin == null || !config.corsOrigins().contains(origin)) {
            return;
        }
        Headers response = exchange.getResponseHeaders();
        response.set("Access-Control-Allow-Origin", origin);
        response.set("Vary", "Origin");
        response.set("Access-Control-Allow-Methods", String.join(", ", config.corsMethods()));
        response.set("Access-Control-Allow-Headers", String.join(", ", config.corsHeaders()));
        response.set("Access-Control-Max-Age", String.valueOf(config.corsMaxAgeSeconds()));
    }

    private byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                return null;
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private boolean headersTooLarge(Headers headers) {
        int size = 0;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            size += entry.getKey().length();
            for (String value : entry.getValue()) {
                size += value.length();
            }
        }
        return size > config.maxHeaderBytes();
    }

    private Map<String, List<String>> parseQuery(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        Map<String, List<String>> query = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = decode(eq >= 0 ? pair.substring(0, eq) : pair);
            String value = decode(eq >= 0 ? pair.substring(eq + 1) : "");
            query.computeIfAbsent(key, unused -> new ArrayList<>()).add(value);
        }
        return query;
    }

    private Map<String, List<String>> lowerHeaders(Headers headers) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        headers.forEach((key, value) -> out.put(key.toLowerCase(Locale.ROOT), List.copyOf(value)));
        return out;
    }

    private String decode(String raw) {
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    private String requestId(Headers headers) {
        String provided = firstHeader(headers, "X-Request-Id");
        if (provided != null && provided.matches("[A-Za-z0-9._:-]{1,80}")) {
            return provided;
        }
        return java.util.UUID.randomUUID().toString();
    }

    private String firstHeader(Headers headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private String safeRemote(HttpExchange exchange) {
        return exchange.getRemoteAddress() == null ? "unknown" : exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private void writeEnvelope(HttpExchange exchange, int status, String requestId, String code, String message) throws IOException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", false);
        envelope.put("requestId", requestId);
        envelope.put("data", null);
        envelope.put("error", error);
        writeJson(exchange, status, envelope);
    }

    private void writeJson(HttpExchange exchange, int status, Map<String, Object> envelope) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = Json.stringify(envelope).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
