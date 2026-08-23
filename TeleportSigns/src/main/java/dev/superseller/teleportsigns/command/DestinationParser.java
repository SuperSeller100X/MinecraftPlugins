package dev.superseller.teleportsigns.command;

import dev.superseller.teleportsigns.util.Numbers;

/**
 * Parses {@code [world] <x> <y> <z> [yaw] [pitch]} including {@code ~} relative
 * coordinates. Pure logic — no Bukkit types — so it can be smoke-tested offline.
 */
public final class DestinationParser {

    private DestinationParser() {
    }

    public static final class Coord {
        public final boolean relative;
        public final double value;

        public Coord(boolean relative, double value) {
            this.relative = relative;
            this.value = value;
        }

        public double resolve(double origin) {
            return relative ? origin + value : value;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String errorKey;
        public final String errorInput;
        public final String world;
        public final Coord x;
        public final Coord y;
        public final Coord z;
        public final Float yaw;
        public final Float pitch;

        private Result(boolean ok, String errorKey, String errorInput, String world,
                       Coord x, Coord y, Coord z, Float yaw, Float pitch) {
            this.ok = ok;
            this.errorKey = errorKey;
            this.errorInput = errorInput;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public static Result error(String key, String input) {
            return new Result(false, key, input, null, null, null, null, null, null);
        }

        public static Result error(String key) {
            return error(key, "");
        }

        public boolean hasWorld() {
            return world != null && !world.isEmpty();
        }

        public boolean hasRotation() {
            return yaw != null;
        }
    }

    public static Result parse(String[] args) {
        if (args == null || args.length == 0) {
            return Result.error("usage-set");
        }

        int index = 0;
        String world = null;
        if (!isCoordToken(args[0])) {
            world = args[0];
            index = 1;
        }
        if (args.length - index < 3) {
            return Result.error("usage-set");
        }

        Coord x = parseCoord(args[index]);
        if (x == null) {
            return Result.error("invalid-number", args[index]);
        }
        Coord y = parseCoord(args[index + 1]);
        if (y == null) {
            return Result.error("invalid-number", args[index + 1]);
        }
        Coord z = parseCoord(args[index + 2]);
        if (z == null) {
            return Result.error("invalid-number", args[index + 2]);
        }
        index += 3;

        Float yaw = null;
        Float pitch = null;
        if (args.length > index) {
            Double parsedYaw = Numbers.parseDouble(args[index]);
            if (parsedYaw == null) {
                return Result.error("invalid-number", args[index]);
            }
            yaw = parsedYaw.floatValue();
            index++;
        }
        if (args.length > index) {
            Double parsedPitch = Numbers.parseDouble(args[index]);
            if (parsedPitch == null) {
                return Result.error("invalid-number", args[index]);
            }
            pitch = parsedPitch.floatValue();
            index++;
        }
        if (args.length > index) {
            return Result.error("usage-set");
        }
        if (yaw != null && pitch == null) {
            pitch = Float.valueOf(0.0f);
        }
        return new Result(true, null, null, world, x, y, z, yaw, pitch);
    }

    public static boolean isCoordToken(String raw) {
        if (raw == null) {
            return false;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return false;
        }
        if (text.charAt(0) == '~') {
            return text.length() == 1 || Numbers.parseDouble(text.substring(1)) != null;
        }
        return Numbers.parseDouble(text) != null;
    }

    public static Coord parseCoord(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.charAt(0) == '~') {
            if (text.length() == 1) {
                return new Coord(true, 0.0d);
            }
            Double offset = Numbers.parseDouble(text.substring(1));
            if (offset == null) {
                return null;
            }
            return new Coord(true, offset.doubleValue());
        }
        Double value = Numbers.parseDouble(text);
        if (value == null) {
            return null;
        }
        return new Coord(false, value.doubleValue());
    }
}
