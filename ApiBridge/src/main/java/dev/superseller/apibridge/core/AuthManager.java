package dev.superseller.apibridge.core;

import dev.superseller.apibridge.config.ApiBridgeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AuthManager {
    private final List<ApiBridgeConfig.ClientConfig> clients;

    public AuthManager(List<ApiBridgeConfig.ClientConfig> clients) {
        this.clients = clients == null ? List.of() : List.copyOf(clients);
    }

    public Optional<ApiClient> authenticate(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return Optional.empty();
        }
        String hash = SecretHasher.sha256Hex(presentedKey.trim());
        for (ApiBridgeConfig.ClientConfig client : clients) {
            if (client.enabled() && SecretHasher.constantTimeEquals(client.keySha256(), hash)) {
                return Optional.of(new ApiClient(client.id(), client.scopes(), client.rateLimitPolicy()));
            }
        }
        return Optional.empty();
    }

    public int configuredClientCount() {
        return clients.size();
    }

    public List<String> clientSummaries() {
        List<String> out = new ArrayList<>();
        for (ApiBridgeConfig.ClientConfig client : clients) {
            out.add(client.id() + " enabled=" + client.enabled() + " scopes=" + client.scopes().size());
        }
        return out;
    }
}
