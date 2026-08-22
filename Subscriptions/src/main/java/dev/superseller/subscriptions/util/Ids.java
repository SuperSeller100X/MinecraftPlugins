package dev.superseller.subscriptions.util;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Ids {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private Ids() {
    }

    public static String planId(String name) {
        String slug = slug(name);
        if (slug.isBlank()) {
            slug = "plan";
        }
        if (slug.length() > 18) {
            slug = slug.substring(0, 18);
        }
        return slug + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    public static String subscriptionId() {
        return "sub-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String slug(String name) {
        if (name == null) {
            return "";
        }
        String slug = NON_SLUG.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-");
        while (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        while (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug;
    }
}
