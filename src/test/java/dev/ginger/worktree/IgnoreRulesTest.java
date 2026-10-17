package dev.ginger.worktree;

import dev.ginger.TestRepo;
import dev.ginger.status.StatusCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgnoreRulesTest {

    @TempDir
    Path tmp;

    @Test
    void matchesDocumentedSyntax() throws IOException {
        IgnoreRules rules = IgnoreRules.parse(List.of(
                "# comment", "", "*.log", "build/", "/secret.txt", "docs/draft?.md", "tmp"));

        assertTrue(rules.isIgnored("app.log"));
        assertTrue(rules.isIgnored("deep/dir/app.log"));
        assertFalse(rules.isIgnored("app.log.txt"));

        assertTrue(rules.isIgnored("build/out.jar"));
        assertTrue(rules.isIgnored("sub/build/out.jar"));
        assertFalse(rules.isIgnored("build"));

        assertTrue(rules.isIgnored("secret.txt"));
        assertFalse(rules.isIgnored("sub/secret.txt"));

        assertTrue(rules.isIgnored("docs/draft1.md"));
        assertFalse(rules.isIgnored("docs/draft10.md"));
        assertFalse(rules.isIgnored("other/docs/draft1.md"));

        assertTrue(rules.isIgnored("tmp"));
        assertTrue(rules.isIgnored("a/tmp/b.txt"));
        assertFalse(rules.isIgnored("tmpfile"));
    }

    @Test
    void rejectsUnsupportedPatterns() {
        for (String bad : List.of("!keep.log", "**/x", "[ab].txt", "/", "a\\b")) {
            IOException e = assertThrows(IOException.class, () -> IgnoreRules.parse(List.of(bad)), bad);
            assertTrue(e.getMessage().contains("line 1"));
        }
    }

    @Test
    void appliesToStatusAndRecursiveAddButNotExplicitPaths() throws IOException {
        TestRepo t = TestRepo.init(tmp);
        t.write(".gingerignore", "*.log\nout/\n").write("a.txt", "a").write("debug.log", "d").write("out/x.bin", "x");

        assertEquals(List.of(".gingerignore", "a.txt"), StatusCalculator.compute(t.repo()).untracked());

        t.add(".");
        assertEquals(List.of(".gingerignore", "a.txt"), List.copyOf(t.repo().readIndex().entries().keySet()));

        t.add("debug.log");
        assertTrue(t.repo().readIndex().entries().containsKey("debug.log"));

        t.write("debug.log", "changed").add(".");
        assertEquals("changed", new String(t.repo().objects().read(t.repo().readIndex().get("debug.log")).content()));
    }
}
