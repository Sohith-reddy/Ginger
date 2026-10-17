package dev.ginger.objects;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public record StoredObject(ObjectType type, byte[] content) {

    public StoredObject {
        Objects.requireNonNull(type, "type");
        content = content.clone();
    }

    public static StoredObject blob(byte[] content) {
        return new StoredObject(ObjectType.BLOB, content);
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public byte[] encode() {
        byte[] header = (type.label() + " " + content.length).getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream(header.length + 1 + content.length);
        out.writeBytes(header);
        out.write(0);
        out.writeBytes(content);
        return out.toByteArray();
    }

    public static StoredObject decode(byte[] data) throws CorruptObjectException {
        int nul = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                nul = i;
                break;
            }
        }
        if (nul < 0) {
            throw new CorruptObjectException("missing header terminator");
        }

        String header = new String(data, 0, nul, StandardCharsets.US_ASCII);
        int space = header.indexOf(' ');
        if (space < 0) {
            throw new CorruptObjectException("malformed header: " + header);
        }

        ObjectType type;
        try {
            type = ObjectType.fromLabel(header.substring(0, space));
        } catch (IllegalArgumentException e) {
            throw new CorruptObjectException(e.getMessage());
        }

        String lengthText = header.substring(space + 1);
        if (!lengthText.matches("0|[1-9][0-9]{0,9}")) {
            throw new CorruptObjectException("malformed length: " + lengthText);
        }
        long length = Long.parseLong(lengthText);
        int payloadLength = data.length - nul - 1;
        if (length != payloadLength) {
            throw new CorruptObjectException("length mismatch: header says " + length + ", found " + payloadLength);
        }

        return new StoredObject(type, Arrays.copyOfRange(data, nul + 1, data.length));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StoredObject other && type == other.type && Arrays.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "StoredObject[" + type.label() + ", " + content.length + " bytes]";
    }
}
