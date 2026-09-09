package dev.superseller.apibridge.api;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ApiHandler {
    CompletionStage<ApiResponse> handle(ApiRequestContext context) throws Exception;
}
