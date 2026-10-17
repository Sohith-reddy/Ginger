package dev.ginger.diff;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public final class UnifiedDiff {

    public static final int CONTEXT = 3;
    public static final int MAX_TEXT_BYTES = 8 * 1024 * 1024;
    public static final int MAX_EDITS = 2000;

    private UnifiedDiff() {
    }

    public static String format(String path, byte[] before, byte[] after) {
        StringBuilder out = new StringBuilder();
        out.append("diff --ginger a/").append(path).append(" b/").append(path).append('\n');
        if (before == null) {
            out.append("new file\n");
        } else if (after == null) {
            out.append("deleted file\n");
        }

        byte[] oldBytes = before == null ? new byte[0] : before;
        byte[] newBytes = after == null ? new byte[0] : after;
        if (isBinary(oldBytes) || isBinary(newBytes)) {
            return out.append("Binary files differ\n").toString();
        }
        if (oldBytes.length > MAX_TEXT_BYTES || newBytes.length > MAX_TEXT_BYTES) {
            return out.append("File is too large for a text diff\n").toString();
        }

        List<String> a = LineDiff.splitLines(new String(oldBytes, StandardCharsets.UTF_8));
        List<String> b = LineDiff.splitLines(new String(newBytes, StandardCharsets.UTF_8));
        Optional<List<LineDiff.Edit>> edits = LineDiff.diff(a, b, MAX_EDITS);
        if (edits.isEmpty()) {
            return out.append("Too many changes for a text diff\n").toString();
        }

        out.append("--- ").append(before == null ? "/dev/null" : "a/" + path).append('\n');
        out.append("+++ ").append(after == null ? "/dev/null" : "b/" + path).append('\n');
        appendHunks(out, edits.get());
        return out.toString();
    }

    public static boolean isBinary(byte[] data) {
        int limit = Math.min(data.length, 8000);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static void appendHunks(StringBuilder out, List<LineDiff.Edit> edits) {
        int size = edits.size();
        int[] oldLine = new int[size + 1];
        int[] newLine = new int[size + 1];
        for (int i = 0; i < size; i++) {
            LineDiff.Op op = edits.get(i).op();
            oldLine[i + 1] = oldLine[i] + (op == LineDiff.Op.INSERT ? 0 : 1);
            newLine[i + 1] = newLine[i] + (op == LineDiff.Op.DELETE ? 0 : 1);
        }

        int i = 0;
        int previousEnd = 0;
        while (true) {
            while (i < size && edits.get(i).op() == LineDiff.Op.EQUAL) {
                i++;
            }
            if (i == size) {
                return;
            }
            int start = Math.max(previousEnd, i - CONTEXT);
            int lastChange = i;
            int j = i;
            while (j < size) {
                if (edits.get(j).op() != LineDiff.Op.EQUAL) {
                    lastChange = j;
                } else if (j - lastChange > 2 * CONTEXT) {
                    break;
                }
                j++;
            }
            int end = Math.min(size, lastChange + CONTEXT + 1);

            int oldCount = oldLine[end] - oldLine[start];
            int newCount = newLine[end] - newLine[start];
            out.append("@@ -").append(range(oldLine[start], oldCount))
                    .append(" +").append(range(newLine[start], newCount)).append(" @@\n");
            for (int e = start; e < end; e++) {
                LineDiff.Edit edit = edits.get(e);
                char prefix = switch (edit.op()) {
                    case EQUAL -> ' ';
                    case DELETE -> '-';
                    case INSERT -> '+';
                };
                String line = edit.line();
                if (line.endsWith("\n")) {
                    out.append(prefix).append(line);
                } else {
                    out.append(prefix).append(line).append("\n\\ No newline at end of file\n");
                }
            }
            previousEnd = end;
            i = end;
        }
    }

    private static String range(int before, int count) {
        return (count == 0 ? before : before + 1) + "," + count;
    }
}
