package dev.superseller.minecraftwiki.article;

import java.util.Locale;
import java.util.Objects;

/**
 * A normalised, stable, unique article identifier such as {@code blocks:diamond_ore}.
 *
 * <p>Normalisation keeps ids usable in commands, file names, permission nodes and search
 * queries: they are lower-cased and restricted to {@code a-z 0-9 _ . - :}.</p>
 */
public record ArticleId(String value) implements Comparable<ArticleId> {

    public ArticleId {
        Objects.requireNonNull(value, "article id");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("article id must not be empty");
        }
    }

    /** Normalises raw input into a canonical id. Returns {@code null} when nothing usable remains. */
    public static ArticleId of(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-' || c == ':';
            out.append(allowed ? c : '_');
        }
        String normalised = out.toString();
        while (normalised.startsWith("_")) {
            normalised = normalised.substring(1);
        }
        return normalised.isEmpty() ? null : new ArticleId(normalised);
    }

    /** Builds {@code category:key} from already-normalised parts. */
    public static ArticleId ofCategory(String category, String key) {
        ArticleId id = of(category + ":" + key);
        return id;
    }

    /** The part after the first {@code :}, or the whole id when there is none. */
    public String key() {
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1);
    }

    /** The part before the first {@code :}, or an empty string when there is none. */
    public String namespace() {
        int colon = value.indexOf(':');
        return colon < 0 ? "" : value.substring(0, colon);
    }

    @Override
    public int compareTo(ArticleId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
