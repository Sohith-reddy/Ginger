package dev.ginger.objects;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {

    private HashUtil() {
    }

    public static ObjectId sha256(byte[] data) {
        try {
            return ObjectId.fromBytes(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static ObjectId hash(StoredObject object) {
        return sha256(object.encode());
    }
}
