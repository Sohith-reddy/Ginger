package dev.ginger.objects;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public record Tree(List<TreeEntry> entries) {

    private static final int ID_BYTES = ObjectId.HEX_LENGTH / 2;

    public Tree {
        entries = entries.stream().sorted(Comparator.comparing(TreeEntry::name)).toList();
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).name().equals(entries.get(i - 1).name())) {
                throw new IllegalArgumentException("duplicate tree entry: " + entries.get(i).name());
            }
        }
    }

    public StoredObject toObject() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (TreeEntry entry : entries) {
            out.writeBytes((entry.type().label() + " " + entry.name()).getBytes(StandardCharsets.UTF_8));
            out.write(0);
            out.writeBytes(entry.id().toBytes());
        }
        return new StoredObject(ObjectType.TREE, out.toByteArray());
    }

    public static Tree fromObject(StoredObject object) throws CorruptObjectException {
        if (object.type() != ObjectType.TREE) {
            throw new CorruptObjectException("not a tree: " + object.type().label());
        }
        byte[] data = object.content();
        List<TreeEntry> entries = new ArrayList<>();
        String previous = null;
        int pos = 0;
        while (pos < data.length) {
            int nul = pos;
            while (nul < data.length && data[nul] != 0) {
                nul++;
            }
            if (nul >= data.length || nul + 1 + ID_BYTES > data.length) {
                throw new CorruptObjectException("truncated tree entry");
            }
            String header = new String(data, pos, nul - pos, StandardCharsets.UTF_8);
            int space = header.indexOf(' ');
            if (space < 0) {
                throw new CorruptObjectException("malformed tree entry: " + header);
            }
            try {
                ObjectType type = ObjectType.fromLabel(header.substring(0, space));
                String name = header.substring(space + 1);
                if (previous != null && previous.compareTo(name) >= 0) {
                    throw new CorruptObjectException("tree entries are not sorted");
                }
                ObjectId id = ObjectId.fromBytes(Arrays.copyOfRange(data, nul + 1, nul + 1 + ID_BYTES));
                entries.add(new TreeEntry(name, type, id));
                previous = name;
            } catch (IllegalArgumentException e) {
                throw new CorruptObjectException(e.getMessage());
            }
            pos = nul + 1 + ID_BYTES;
        }
        return new Tree(entries);
    }
}
