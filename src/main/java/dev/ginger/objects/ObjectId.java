package dev.ginger.objects;

import java.util.HexFormat;
import java.util.regex.Pattern;

public record ObjectId(String hex) {

    public static final int HEX_LENGTH = 64;

    private static final Pattern VALID = Pattern.compile("[0-9a-f]{" + HEX_LENGTH + "}");

    public ObjectId {
        if (hex == null || !VALID.matcher(hex).matches()) {
            throw new IllegalArgumentException("invalid object id: " + hex);
        }
    }

    public static ObjectId of(String hex) {
        return new ObjectId(hex);
    }

    public static ObjectId fromBytes(byte[] digest) {
        return new ObjectId(HexFormat.of().formatHex(digest));
    }

    public byte[] toBytes() {
        return HexFormat.of().parseHex(hex);
    }

    public String abbreviate() {
        return hex.substring(0, 7);
    }

    public String directory() {
        return hex.substring(0, 2);
    }

    public String fileName() {
        return hex.substring(2);
    }

    @Override
    public String toString() {
        return hex;
    }
}
