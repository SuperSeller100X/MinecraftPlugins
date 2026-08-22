package dev.superseller.subscriptions.hook;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Optional LuckPerms integration via reflection so the plugin still loads
 * when LuckPerms is absent. Grants / revokes nodes and groups; temporary
 * nodes use the plan interval when available.
 */
public final class LuckPermsHook {

    private final Logger logger;
    private Object api;
    private Method loadUser;
    private Method getGroupManager;
    private Method isLoadedGroup;
    private boolean enabled;

    public LuckPermsHook(Logger logger) {
        this.logger = logger;
    }

    public void init() {
        enabled = false;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            api = provider.getMethod("get").invoke(null);
            Class<?> lp = api.getClass();
            loadUser = find(lp, "getUserManager");
            getGroupManager = find(lp, "getGroupManager");
            if (getGroupManager != null) {
                Object groups = getGroupManager.invoke(api);
                isLoadedGroup = find(groups.getClass(), "isLoaded", String.class);
            }
            enabled = api != null && loadUser != null;
            if (enabled) {
                logger.info("LuckPerms hooked.");
            }
        } catch (Throwable t) {
            enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean groupExists(String group) {
        if (!enabled || group == null || group.isBlank()) {
            return false;
        }
        try {
            Object manager = getGroupManager.invoke(api);
            if (isLoadedGroup != null) {
                Object loaded = isLoadedGroup.invoke(manager, group);
                if (loaded instanceof Boolean bool) {
                    return bool;
                }
            }
            Method getGroup = find(manager.getClass(), "getGroup", String.class);
            return getGroup != null && getGroup.invoke(manager, group) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean grantPermission(UUID uuid, String node, Duration duration) {
        return mutate(uuid, node, true, false, duration);
    }

    public boolean revokePermission(UUID uuid, String node) {
        return mutate(uuid, node, false, false, null);
    }

    public boolean grantGroup(UUID uuid, String group, Duration duration) {
        return mutate(uuid, group, true, true, duration);
    }

    public boolean revokeGroup(UUID uuid, String group) {
        return mutate(uuid, group, false, true, null);
    }

    private boolean mutate(UUID uuid, String node, boolean add, boolean group, Duration duration) {
        if (!enabled || uuid == null || node == null || node.isBlank()) {
            return false;
        }
        try {
            Object userManager = loadUser.invoke(api);
            Method load = find(userManager.getClass(), "modifyUser", UUID.class, java.util.function.Consumer.class);
            if (load == null) {
                return fallbackModify(userManager, uuid, node, add, group, duration);
            }
            load.invoke(userManager, uuid, (java.util.function.Consumer<Object>) user ->
                    apply(user, node, add, group, duration));
            return true;
        } catch (Throwable t) {
            logger.warning("LuckPerms update failed: " + t.getMessage());
            return false;
        }
    }

    private boolean fallbackModify(Object userManager, UUID uuid, String node, boolean add, boolean group, Duration duration) {
        try {
            Method loadUser = find(userManager.getClass(), "loadUser", UUID.class);
            if (loadUser == null) {
                return false;
            }
            Object future = loadUser.invoke(userManager, uuid);
            Object user = future.getClass().getMethod("join").invoke(future);
            apply(user, node, add, group, duration);
            Method save = find(userManager.getClass(), "saveUser", user.getClass());
            if (save != null) {
                save.invoke(userManager, user);
            }
            return true;
        } catch (Throwable t) {
            logger.warning("LuckPerms fallback failed: " + t.getMessage());
            return false;
        }
    }

    private void apply(Object user, String node, boolean add, boolean group, Duration duration) {
        try {
            Object data = user.getClass().getMethod("data").invoke(user);
            Object built = buildNode(node, group, duration);
            if (built == null) {
                return;
            }
            if (add) {
                data.getClass().getMethod("add", Class.forName("net.luckperms.api.node.Node")).invoke(data, built);
            } else {
                try {
                    data.getClass().getMethod("remove", Class.forName("net.luckperms.api.node.Node")).invoke(data, built);
                } catch (Throwable ignored) {
                    Method removeIf = find(data.getClass(), "clear", java.util.function.Predicate.class);
                    if (removeIf != null) {
                        String match = group ? "group." + node : node;
                        removeIf.invoke(data, (java.util.function.Predicate<Object>) n ->
                                String.valueOf(n).contains(match));
                    }
                }
            }
        } catch (Throwable t) {
            logger.warning("LuckPerms node apply failed: " + t.getMessage());
        }
    }

    private Object buildNode(String node, boolean group, Duration duration) {
        try {
            Class<?> builderClass = Class.forName(group
                    ? "net.luckperms.api.node.types.InheritanceNode"
                    : "net.luckperms.api.node.Node");
            Method builder = group
                    ? builderClass.getMethod("builder", String.class)
                    : Class.forName("net.luckperms.api.node.Node").getMethod("builder", String.class);
            Object b = builder.invoke(null, node);
            if (duration != null && !duration.isZero() && !duration.isNegative()) {
                Method expiry = find(b.getClass(), "expiry", Duration.class);
                if (expiry != null) {
                    b = expiry.invoke(b, duration);
                }
            }
            return b.getClass().getMethod("build").invoke(b);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method find(Class<?> type, String name, Class<?>... args) {
        try {
            return type.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
