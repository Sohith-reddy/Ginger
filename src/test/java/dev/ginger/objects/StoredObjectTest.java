package dev.ginger.objects;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoredObjectTest {

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void encodesTypeLengthAndPayload() {
        assertArrayEquals(ascii("blob 5\0hello"), StoredObject.blob(ascii("hello")).encode());
        assertArrayEquals(ascii("blob 0\0"), StoredObject.blob(new byte[0]).encode());
        assertArrayEquals(ascii("tree 1\0x"), new StoredObject(ObjectType.TREE, ascii("x")).encode());
    }

    @Test
    void decodeRoundTrips() throws CorruptObjectException {
        StoredObject object = new StoredObject(ObjectType.COMMIT, new byte[]{0, 1, 2, (byte) 0xff});

        assertEquals(object, StoredObject.decode(object.encode()));
    }

    @Test
    void hashIsKnownSha256OfEncoding() {
        assertEquals(HashUtil.sha256(ascii("blob 5\0hello")), HashUtil.hash(StoredObject.blob(ascii("hello"))));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HashUtil.sha256(new byte[0]).hex());
    }

    @Test
    void identicalPayloadsHashTheSame() {
        assertEquals(HashUtil.hash(StoredObject.blob(ascii("same"))), HashUtil.hash(StoredObject.blob(ascii("same"))));
        assertNotEquals(HashUtil.hash(StoredObject.blob(ascii("a"))), HashUtil.hash(StoredObject.blob(ascii("b"))));
        assertNotEquals(HashUtil.hash(StoredObject.blob(ascii("a"))),
                HashUtil.hash(new StoredObject(ObjectType.TREE, ascii("a"))));
    }

    @Test
    void contentIsDefensivelyCopied() {
        byte[] bytes = ascii("abc");
        StoredObject object = StoredObject.blob(bytes);
        bytes[0] = 'z';
        object.content()[1] = 'z';

        assertArrayEquals(ascii("abc"), object.content());
    }

    @Test
    void rejectsMalformedEncodings() {
        assertThrows(CorruptObjectException.class, () -> StoredObject.decode(ascii("blob 5hello")));
        assertThrows(CorruptObjectException.class, () -> StoredObject.decode(ascii("blob5\0hello")));
        assertThrows(CorruptObjectException.class, () -> StoredObject.decode(ascii("tag 5\0hello")));
        assertThrows(CorruptObjectException.class, () -> StoredObject.decode(ascii("blob 6\0hello")));
        assertThrows(CorruptObjectException.class, () -> StoredObject.decode(ascii("blob -5\0hello")));
        assertThrows(CorruptObjectException.class, () -> StoredObject.decode(ascii("blob 05\0hello")));
        assertThrows(CorruptObjectException.class, () -> StoredObject.decode(new byte[0]));
    }
}
