package dev.superseller.apibridge.api;

/**
 * Controls where an endpoint handler runs. Bukkit/Paper/Folia API calls should
 * use GLOBAL_MINECRAFT unless the handler schedules its own entity/region work.
 */
public enum ExecutionMode {
    HTTP_WORKER,
    ASYNC_WORKER,
    GLOBAL_MINECRAFT
}
