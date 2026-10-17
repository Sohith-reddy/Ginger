package dev.ginger.objects;

import java.io.IOException;

public class CorruptObjectException extends IOException {

    public CorruptObjectException(String message) {
        super(message);
    }
}
