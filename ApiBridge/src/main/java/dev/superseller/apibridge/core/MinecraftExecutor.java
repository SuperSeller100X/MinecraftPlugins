package dev.superseller.apibridge.core;

public interface MinecraftExecutor {
    void runGlobal(Runnable task);

    static MinecraftExecutor direct() {
        return Runnable::run;
    }
}
