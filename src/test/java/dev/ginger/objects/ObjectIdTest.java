package dev.ginger.objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectIdTest {

    private static final String HEX = "ab" + "c".repeat(62);

    @Test
    void splitsIntoDirectoryAndFileName() {
        ObjectId id = ObjectId.of(HEX);

        assertEquals("ab", id.directory());
        assertEquals("c".repeat(62), id.fileName());
        assertEquals(HEX, id.toString());
    }

    @Test
    void rejectsInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> ObjectId.of(null));
        assertThrows(IllegalArgumentException.class, () -> ObjectId.of(""));
        assertThrows(IllegalArgumentException.class, () -> ObjectId.of("abc"));
        assertThrows(IllegalArgumentException.class, () -> ObjectId.of(HEX + "0"));
        assertThrows(IllegalArgumentException.class, () -> ObjectId.of(HEX.toUpperCase()));
        assertThrows(IllegalArgumentException.class, () -> ObjectId.of("g" + HEX.substring(1)));
        assertThrows(IllegalArgumentException.class, () -> ObjectId.of("../" + HEX.substring(3)));
    }
}
