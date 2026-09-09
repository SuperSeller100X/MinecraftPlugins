# ApiBridge

ApiBridge is a reusable, authenticated HTTP bridge for controlled backend-to-Minecraft integrations.

Intended deployment:

```text
Website/browser -> external HTTPS backend/API -> ApiBridge on the Minecraft server -> registered plugin action
```

ApiBridge is **not** a public command proxy and does not provide an arbitrary command execution endpoint. Plugins register explicit, versioned, typed endpoints with required authorization scopes.

## Security defaults

- Disabled by default.
- Binds to `127.0.0.1` by default.
- Requires an API key for every registered endpoint.
- Stores API keys as SHA-256 hashes in config; raw keys should be managed outside source control.
- Supports environment-provided clients for rotation without editing tracked files.
- Request bodies are not logged by default.
- CORS is disabled by default because backend-to-backend calls should not need browser CORS.
- Rate limiting is enabled by default and bounded to prevent unbounded memory growth.

## Configuration

Edit `plugins/ApiBridge/config.yml` after first start.

Minimum local reverse-proxy configuration:

```yaml
enabled: true
http:
  bind-host: "127.0.0.1"
  port: 8765

authentication:
  clients:
    - id: "website-backend"
      enabled: true
      key-sha256: "<sha256 hex of your real API key>"
      scopes: ["status:read"]
```

Generate a SHA-256 hex digest outside the repository, for example:

```bash
printf '%s' 'your-long-random-api-key' | sha256sum
```

Do not commit raw keys. Do not paste keys into support logs.

### Environment client override

`authentication.clients-env` defaults to `APIBRIDGE_CLIENTS`. If set, it accepts:

```text
clientId:rawApiKey:scope1,scope2;clientId2:rawApiKey2:scope
```

ApiBridge hashes these raw environment keys at load time. This is convenient for containers and secret managers. Reload with `/apibridge reload` to pick up changed configuration/environment values where the process environment has changed.

### Bind address and TLS

`http.bind-host` is the local listener address. Keep `127.0.0.1` when an external backend runs on the same host or a reverse proxy tunnels to the server. `http.advertised-url` is only informational for diagnostics/documentation and does not change the bind address.

Production options:

1. **Recommended:** terminate public HTTPS at your external backend or trusted reverse proxy, and connect privately to ApiBridge on `127.0.0.1` or a private network.
2. **Direct Java TLS:** set `http.tls.enabled: true`, configure a local PKCS12/JKS keystore path, and provide the password through the configured environment variable. Never commit private keys or certificates.

If TLS terminates at a reverse proxy, encryption is not end-to-end between the original browser and the Minecraft JVM unless the proxy-to-Minecraft hop also uses TLS or a trusted private channel.

### CORS

CORS is off by default. Enable only for deliberate direct browser integrations. Configure explicit origins; do not use wildcard origins for browser-accessible authenticated APIs.

## Authentication

Requests authenticate with either:

```http
Authorization: Bearer <api-key>
```

or:

```http
X-Api-Key: <api-key>
```

Keys are compared using SHA-256 hashes and constant-time comparison. Rotate or revoke credentials by adding/removing clients or changing hashes, then reload. ApiBridge never logs presented keys.

## Authorization scopes

Every endpoint declares zero or more required scopes. The authenticated client must have all required scopes or ApiBridge returns `403 AUTHORIZATION_DENIED` before running validation or handler code.

Recommended scope style is `domain:resource:action`, for example:

- `status:read`
- `metrics:read`
- `playerbank:balance:read`
- `myplugin:reward:grant`

## Endpoint registration API

Other plugins can retrieve the service from Bukkit's services manager:

```java
RegisteredServiceProvider<MinecraftApiBridge> rsp =
    getServer().getServicesManager().getRegistration(MinecraftApiBridge.class);
MinecraftApiBridge bridge = rsp == null ? null : rsp.getProvider();
```

Then register a versioned endpoint:

```java
bridge.registerEndpoint(ApiEndpoint.builder("MyPlugin", 1, HttpMethod.POST, "/myplugin/action")
    .requireScope("myplugin:action:write")
    .executionMode(ExecutionMode.GLOBAL_MINECRAFT)
    .timeout(Duration.ofSeconds(3))
    .validator(context -> {
        if (!(context.jsonBody() instanceof Map<?, ?> body) || !body.containsKey("id")) {
            throw new ValidationException("id is required");
        }
    })
    .handler(context -> CompletableFuture.completedFuture(ApiResponse.ok(Map.of("done", true))))
    .build());
```

Register paths relative to `/api/vN`. For example, version `1` and path `/myplugin/action` becomes:

```http
POST /api/v1/myplugin/action
```

Duplicate method/version/path registration fails with `409 ROUTE_CONFLICT` rather than overwriting the existing endpoint. Unregister endpoints on plugin disable with `unregisterOwner("MyPlugin")` or `unregisterEndpoint(...)`.

## Execution modes and Folia

- `HTTP_WORKER`: handler runs on the HTTP request thread. Use for pure CPU/lightweight logic that does not touch Bukkit/Paper/Folia APIs.
- `ASYNC_WORKER`: handler runs on ApiBridge's async executor. Use for asynchronous non-Minecraft work.
- `GLOBAL_MINECRAFT`: handler is scheduled through a Folia-aware global-region scheduler when available, with Bukkit scheduler fallback.

Folia has no single universal main thread for all world/entity operations. `GLOBAL_MINECRAFT` is appropriate for global state and service calls, but endpoint authors that touch entities, regions, or locations should schedule to the relevant entity or region scheduler inside their own plugin before mutating Minecraft state.

## Request context

Handlers receive:

- request ID (`X-Request-Id` if valid, otherwise generated UUID)
- authenticated client id
- client scopes
- HTTP method
- registered route/path
- timestamp
- safe remote address
- parsed query parameters
- headers
- parsed JSON body
- deadline/timeout information

## Responses

All JSON responses use one envelope:

Success:

```json
{
  "success": true,
  "requestId": "...",
  "data": {},
  "error": null
}
```

Error:

```json
{
  "success": false,
  "requestId": "...",
  "data": null,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "uuid must be a valid UUID",
    "details": {}
  }
}
```

Implemented error categories include authentication failures, authorization failures, validation failures, malformed JSON, unsupported content type, not found, route conflicts, rate limiting, request/body/header limits, handler timeout, internal failure, and disabled/unavailable service.

HTTP responses never intentionally include stack traces, file paths, secrets, or raw internal exception details.

## Built-in endpoints

- `GET /api/v1/status` requires `status:read`.
- `GET /api/v1/metrics` requires `metrics:read`.

## Existing integration: PlayerBank

When the existing PlayerBank plugin is installed and enabled, ApiBridge registers a read-only endpoint:

```http
GET /api/v1/playerbank/balance?uuid=<player-uuid>
Authorization: Bearer <api-key-with-playerbank:balance:read>
```

Required scope: `playerbank:balance:read`.

The endpoint validates that `uuid` is a syntactically valid UUID and then reads the existing PlayerBank storage through its public plugin accessors. It does not create accounts, mutate balances, withdraw/deposit wallet money, or run commands.

## Rate limiting

Global defaults are under `rate-limit`. Endpoint-specific policies and client-specific policies are supported; endpoint policy wins, then client policy, then global default. Buckets are keyed per client and endpoint. Idle buckets expire, and the bucket map is capped by `max-buckets` to avoid uncontrolled memory growth.

## Diagnostics command

`/apibridge status` shows enabled/running state, bind host/port, TLS status, endpoint count, and configured client count.

`/apibridge endpoints` lists method, route, owner, scopes, and execution mode.

`/apibridge metrics` shows request totals, failures, latency, active requests, and rate-limit rejections.

`/apibridge errors` shows recent sanitized API errors.

`/apibridge clients` lists client ids, enabled status, and scope counts. Secrets are never displayed.

`/apibridge reload` reloads config, credentials, integrations, endpoints, and the HTTP listener.

## External backend integration notes

- Keep ApiBridge private to your backend or reverse proxy.
- Use HTTPS from browsers to your external backend.
- Use long random API keys stored in a secret manager.
- Send a unique `X-Request-Id` from the backend and propagate it in your logs.
- Treat 401/403/429/5xx distinctly in backend retry logic.
- Do not expose direct browser controls unless CORS and credential handling are intentionally designed.

## Build and tests

The plugin follows the repository's Maven-per-plugin convention:

```bash
cd ApiBridge
mvn -B clean verify
```
