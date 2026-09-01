package dev.superseller.shardtools.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A sound plus volume and pitch, parsed from config strings like
 * "entity.enderman_teleport 1.0 1.3" (volume/pitch optional).
 */
public final class SoundSpec {

    private final String id;
    private final float volume;
    private final float pitch;

    public SoundSpec(String id, float volume, float pitch) {
        this.id = id;
        this.volume = volume;
        this.pitch = pitch;
    }

    public String id() {
        return id;
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }

    /** Parses "id [volume] [pitch]"; defaults 1.0 / 1.0. */
    public static SoundSpec parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        float volume = 1.0f;
        float pitch = 1.0f;
        if (parts.length >= 2) {
            try {
                volume = Float.parseFloat(parts[1]);
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        if (parts.length >= 3) {
            try {
                pitch = Float.parseFloat(parts[2]);
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        return new SoundSpec(parts[0].toLowerCase(Locale.ROOT), volume, pitch);
    }

    /** Parses a list of sound lines, skipping blanks. */
    public static List<SoundSpec> parseAll(List<String> lines) {
        List<SoundSpec> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            SoundSpec spec = parse(line);
            if (spec != null && !spec.id().isEmpty()) {
                out.add(spec);
            }
        }
        return out;
    }
}
