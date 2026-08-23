package dev.superseller.teleportsigns.model;

import java.util.UUID;

/**
 * Encodes a teleport-sign payload as a single portable string so it can live
 * in a sign's PersistentDataContainer and in {@code signs.yml}.
 *
 * <p>Format: {@code v1;world;x;y;z;hasRot;yaw;pitch;cost;creator;created}</p>
 */
public final class SignCodec {

    public static final String VERSION = "v1";
    private static final String SEP = ";";

    private SignCodec() {
    }

    public static String encode(TeleportSign sign) {
        if (sign == null || sign.destination() == null) {
            return "";
        }
        WarpDestination dest = sign.destination();
        StringBuilder out = new StringBuilder(96);
        out.append(VERSION).append(SEP);
        out.append(escape(dest.worldName())).append(SEP);
        out.append(dest.x()).append(SEP);
        out.append(dest.y()).append(SEP);
        out.append(dest.z()).append(SEP);
        out.append(dest.hasRotation() ? "1" : "0").append(SEP);
        out.append(dest.yaw()).append(SEP);
        out.append(dest.pitch()).append(SEP);
        out.append(sign.cost()).append(SEP);
        out.append(sign.creator() == null ? "" : sign.creator().toString()).append(SEP);
        out.append(sign.createdAt());
        return out.toString();
    }

    public static TeleportSign decode(SignKey key, String raw) {
        if (key == null || raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = split(raw);
        if (parts.length < 11 || !"v1".equals(parts[0])) {
            return null;
        }
        try {
            String world = unescape(parts[1]);
            double x = Double.parseDouble(parts[2]);
            double y = Double.parseDouble(parts[3]);
            double z = Double.parseDouble(parts[4]);
            boolean hasRot = "1".equals(parts[5]);
            float yaw = Float.parseFloat(parts[6]);
            float pitch = Float.parseFloat(parts[7]);
            double cost = Double.parseDouble(parts[8]);
            UUID creator = parts[9].isEmpty() ? null : UUID.fromString(parts[9]);
            long created = Long.parseLong(parts[10]);
            WarpDestination dest = new WarpDestination(world, x, y, z, yaw, pitch, hasRot);
            return new TeleportSign(key, dest, cost, creator, created);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace(SEP, "\\;");
    }

    public static String unescape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        boolean slash = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (slash) {
                out.append(ch);
                slash = false;
            } else if (ch == '\\') {
                slash = true;
            } else {
                out.append(ch);
            }
        }
        if (slash) {
            out.append('\\');
        }
        return out.toString();
    }

    static String[] split(String raw) {
        java.util.ArrayList<String> parts = new java.util.ArrayList<String>(11);
        StringBuilder current = new StringBuilder();
        boolean slash = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (slash) {
                current.append('\\').append(ch);
                slash = false;
            } else if (ch == '\\') {
                slash = true;
            } else if (ch == ';') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (slash) {
            current.append('\\');
        }
        parts.add(current.toString());
        return parts.toArray(new String[0]);
    }
}
