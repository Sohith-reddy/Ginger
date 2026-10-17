package dev.ginger.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class LineDiff {

    public enum Op { EQUAL, DELETE, INSERT }

    public record Edit(Op op, String line) {
    }

    private LineDiff() {
    }

    public static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    public static Optional<List<Edit>> diff(List<String> a, List<String> b, int maxEdits) {
        int n = a.size();
        int m = b.size();
        int max = n + m;
        int offset = max + 1;
        int[] v = new int[2 * max + 3];
        List<int[]> trace = new ArrayList<>();

        for (int d = 0; d <= max; d++) {
            if (d > maxEdits) {
                return Optional.empty();
            }
            trace.add(Arrays.copyOfRange(v, offset - d - 1, offset + d + 2));
            for (int k = -d; k <= d; k += 2) {
                int x = (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1]))
                        ? v[offset + k + 1]
                        : v[offset + k - 1] + 1;
                int y = x - k;
                while (x < n && y < m && a.get(x).equals(b.get(y))) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {
                    return Optional.of(backtrack(trace, a, b));
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static List<Edit> backtrack(List<int[]> trace, List<String> a, List<String> b) {
        List<Edit> edits = new ArrayList<>();
        int x = a.size();
        int y = b.size();
        for (int d = trace.size() - 1; d >= 0; d--) {
            int[] v = trace.get(d);
            int k = x - y;
            int prevK = (k == -d || (k != d && v[k - 1 + d + 1] < v[k + 1 + d + 1])) ? k + 1 : k - 1;
            int prevX = v[prevK + d + 1];
            int prevY = prevX - prevK;
            while (x > prevX && y > prevY) {
                edits.add(new Edit(Op.EQUAL, a.get(x - 1)));
                x--;
                y--;
            }
            if (d > 0) {
                if (x == prevX) {
                    edits.add(new Edit(Op.INSERT, b.get(y - 1)));
                } else {
                    edits.add(new Edit(Op.DELETE, a.get(x - 1)));
                }
            }
            x = prevX;
            y = prevY;
        }
        Collections.reverse(edits);
        return edits;
    }
}
