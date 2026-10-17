package dev.ginger.diff;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedDiffTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void lineDiffFindsMinimalEdits() {
        List<LineDiff.Edit> edits = LineDiff.diff(List.of("a", "b", "c"), List.of("a", "x", "c"), 100).orElseThrow();

        assertEquals(List.of(
                new LineDiff.Edit(LineDiff.Op.EQUAL, "a"),
                new LineDiff.Edit(LineDiff.Op.DELETE, "b"),
                new LineDiff.Edit(LineDiff.Op.INSERT, "x"),
                new LineDiff.Edit(LineDiff.Op.EQUAL, "c")), edits);
    }

    @Test
    void lineDiffHandlesEmptyInputs() {
        assertEquals(List.of(), LineDiff.diff(List.of(), List.of(), 10).orElseThrow());
        assertEquals(2, LineDiff.diff(List.of(), List.of("a", "b"), 10).orElseThrow().size());
        assertTrue(LineDiff.diff(List.of("a", "b", "c"), List.of("x", "y", "z"), 2).isEmpty());
    }

    @Test
    void formatsModification() {
        String diff = UnifiedDiff.format("f.txt", b("one\ntwo\nthree\n"), b("one\n2\nthree\n"));

        assertEquals("""
                diff --ginger a/f.txt b/f.txt
                --- a/f.txt
                +++ b/f.txt
                @@ -1,3 +1,3 @@
                 one
                -two
                +2
                 three
                """, diff);
    }

    @Test
    void separatesDistantHunks() {
        StringBuilder before = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            before.append(i).append('\n');
        }
        String after = before.toString().replace("2\n3\n", "2\nX\n").replace("18\n", "Y\n");

        String diff = UnifiedDiff.format("n", b(before.toString()), b(after));

        assertTrue(diff.contains("@@ -1,6 +1,6 @@"), diff);
        assertTrue(diff.contains("@@ -15,6 +15,6 @@"), diff);
    }

    @Test
    void formatsAdditionDeletionAndMissingNewline() {
        assertTrue(UnifiedDiff.format("n", null, b("x\n")).contains("new file\n--- /dev/null\n+++ b/n\n@@ -0,0 +1,1 @@\n+x\n"));
        assertTrue(UnifiedDiff.format("d", b("x\n"), null).contains("deleted file\n--- a/d\n+++ /dev/null\n@@ -1,1 +0,0 @@\n-x\n"));
        assertTrue(UnifiedDiff.format("e", b("x\n"), b("x")).contains("-x\n+x\n\\ No newline at end of file\n"));
    }

    @Test
    void binaryFilesAreNotDiffedAsText() {
        String diff = UnifiedDiff.format("bin", new byte[]{1, 0, 2}, new byte[]{1, 0, 3});
        assertTrue(diff.endsWith("Binary files differ\n"));
    }
}
