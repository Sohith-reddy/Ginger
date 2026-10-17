package dev.ginger.objects;

import java.io.IOException;

public class ObjectNotFoundException extends IOException {

    public ObjectNotFoundException(ObjectId id) {
        super("object not found: " + id);
    }
}
