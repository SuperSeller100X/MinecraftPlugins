package dev.superseller.apibridge.config;

import dev.superseller.apibridge.core.SecretHasher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class EnvClientParser {
    private EnvClientParser() { }

    static List<ApiBridgeConfig.ClientConfig> parse(String value) {
        List<ApiBridgeConfig.ClientConfig> clients = new ArrayList<>();
        for (String entry : value.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split(":", 3);
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                continue;
            }
            Set<String> scopes = new LinkedHashSet<>();
            if (parts.length == 3) {
                for (String scope : parts[2].split(",")) {
                    if (!scope.isBlank()) {
                        scopes.add(scope.trim());
                    }
                }
            }
            clients.add(new ApiBridgeConfig.ClientConfig(parts[0].trim(), SecretHasher.sha256Hex(parts[1]), true, scopes, null));
        }
        return clients;
    }
}
