package dev.superseller.apibridge.api;

import java.util.List;

public interface MinecraftApiBridge {
    void registerEndpoint(ApiEndpoint endpoint) throws ApiException;
    void unregisterEndpoint(String owner, int version, HttpMethod method, String path);
    int unregisterOwner(String owner);
    List<ApiEndpoint> endpoints();
    boolean isRunning();
}
