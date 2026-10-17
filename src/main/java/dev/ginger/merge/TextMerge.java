package dev.ginger.merge;

import dev.ginger.diff.LineDiff;
import dev.ginger.diff.UnifiedDiff;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class TextMerge {

    public record Result(String text, boolean conflicted) {
    }

    private TextMerge() {
    }

    public static Optional<Result> merge(String base, String ours, String theirs,
                                         String oursLabel, String theirsLabel) {
        List<String> b = LineDiff.splitLines(base);
        List<String> o = LineDiff.splitLines(ours);
        List<String> t = LineDiff.splitLines(theirs);
        Optional<int[]> oursMatch = matches(b, o);
        Optional<int[]> theirsMatch = matches(b, t);
        if (oursMatch.isEmpty() || theirsMatch.isEmpty()) {
            return Optional.empty();
        }
        int[] om = oursMatch.get();
        int[] tm = theirsMatch.get();

        StringBuilder out = new StringBuilder();
        boolean conflicted = false;
        int i = 0;
        int oi = 0;
        int ti = 0;
        int n = b.size();
        while (true) {
            while (i < n && om[i] == oi && tm[i] == ti) {
                out.append(b.get(i));
                i++;
                oi++;
                ti++;
            }
            if (i == n && oi == o.size() && ti == t.size()) {
                break;
            }
            int j = i;
            while (j < n && (om[j] < 0 || tm[j] < 0)) {
                j++;
            }
            int oEnd = j < n ? om[j] : o.size();
            int tEnd = j < n ? tm[j] : t.size();
            List<String> baseChunk = b.subList(i, j);
            List<String> oursChunk = o.subList(oi, oEnd);
            List<String> theirsChunk = t.subList(ti, tEnd);

            if (oursChunk.equals(theirsChunk) || theirsChunk.equals(baseChunk)) {
                oursChunk.forEach(out::append);
            } else if (oursChunk.equals(baseChunk)) {
                theirsChunk.forEach(out::append);
            } else {
                conflicted = true;
                appendMarker(out, "<<<<<<< " + oursLabel);
                appendLines(out, oursChunk);
                appendMarker(out, "=======");
                appendLines(out, theirsChunk);
                appendMarker(out, ">>>>>>> " + theirsLabel);
            }
            i = j;
            oi = oEnd;
            ti = tEnd;
        }
        return Optional.of(new Result(out.toString(), conflicted));
    }

    private static Optional<int[]> matches(List<String> base, List<String> other) {
        Optional<List<LineDiff.Edit>> edits = LineDiff.diff(base, other, UnifiedDiff.MAX_EDITS);
        if (edits.isEmpty()) {
            return Optional.empty();
        }
        int[] map = new int[base.size()];
        Arrays.fill(map, -1);
        int bi = 0;
        int oi = 0;
        for (LineDiff.Edit edit : edits.get()) {
            switch (edit.op()) {
                case EQUAL -> map[bi++] = oi++;
                case DELETE -> bi++;
                case INSERT -> oi++;
            }
        }
        return Optional.of(map);
    }

    private static void appendMarker(StringBuilder out, String marker) {
        if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        out.append(marker).append('\n');
    }

    private static void appendLines(StringBuilder out, List<String> lines) {
        lines.forEach(out::append);
    }
}
