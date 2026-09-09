# ApiBridge Security Review

Date: 2026-09-09

This review covers the ApiBridge implementation added in this repository.

## Authentication bypass

- All dispatched registered endpoints require a valid API key before handler execution.
- API keys are accepted only via `Authorization: Bearer` or `X-Api-Key`.
- Config stores SHA-256 hashes; environment-provided raw keys are hashed at load time.
- Hash comparisons use `MessageDigest.isEqual`.
- No raw secrets are logged or returned in responses.

Limitations: API keys are bearer credentials. If a key is stolen, it can be replayed until revoked. Operators should use HTTPS/private networking and rotate keys.

## Authorization bypass

- Endpoint required scopes are checked with `containsAll` before request validation and handler execution.
- Diagnostics and PlayerBank integration endpoints declare explicit read scopes.
- Route conflicts fail with `409 ROUTE_CONFLICT`, preventing accidental endpoint replacement.

## Arbitrary command execution

- No generic command execution endpoint exists.
- The included PlayerBank integration is read-only and calls storage accessors reflectively; it does not invoke commands or mutate balances.

## Input validation and injection

- Routes are normalized and constrained to versioned `/api/vN/...` paths.
- JSON parsing rejects malformed JSON and control characters in strings.
- Request bodies and headers have configurable size limits.
- PlayerBank integration validates UUID syntax before any lookup.
- No SQL, shell, filesystem path construction from user input, or outbound URL fetching is introduced.

## SSRF and outbound networking

- ApiBridge introduces no outbound HTTP client and does not fetch URLs supplied by users.

## Path traversal and unsafe filesystem access

- HTTP routing does not serve files.
- TLS keystore path is operator-configured only, not request-controlled.

## Unsafe reflection

- Reflection is used only for optional PlayerBank integration against a known local plugin API and for Folia scheduler detection.
- Request data does not control reflected class or method names.

## Race conditions and thread safety

- Endpoint registry uses `ConcurrentHashMap`.
- Rate limiter synchronization bounds bucket cleanup and updates.
- Metrics use atomics/concurrent maps.
- Folia-aware global scheduler is used for the PlayerBank integration endpoint.

Limitations: Plugins registering their own endpoints remain responsible for choosing the correct execution mode and scheduling entity/region-specific work safely on Folia.

## Resource leaks

- HTTP and async executors are shut down on reload/disable.
- `HttpExchange` response streams are closed with try-with-resources.
- Endpoint registrations are cleared on shutdown; integration endpoints unregister on disable.

## Uncontrolled memory growth

- Request body/header sizes are bounded.
- Active requests are limited with a semaphore.
- Rate-limit buckets have idle TTL and max bucket count.
- Recent errors are capped.

## Secret leakage

- Commands display client ids, enabled status, and scope counts only.
- HTTP responses use sanitized machine-readable errors and no stack traces.
- Logs include request id, route, status, and error code, not request bodies or secrets.

## Denial of service

- Configurable worker thread count, active request cap, body/header limits, rate limiting, and handler timeouts mitigate common request floods.
- Safe default bind is loopback and disabled until configured.

Limitations: `com.sun.net.httpserver` is intentionally lightweight; high-volume public edge traffic should terminate at a hardened reverse proxy/backend before reaching ApiBridge.
