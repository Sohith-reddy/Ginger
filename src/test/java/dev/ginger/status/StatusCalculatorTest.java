package dev.ginger.status;

import dev.ginger.TestRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusCalculatorTest {

    @TempDir
    Path tmp;

    private TestRepo t;

    @BeforeEach
    void setUp() throws IOException {
        t = TestRepo.init(tmp);
    }

    private Status status() throws IOException {
        return StatusCalculator.compute(t.repo());
    }

    @Test
    void emptyRepositoryIsClean() throws IOException {
        Status status = status();
        assertTrue(status.isClean());
        assertFalse(status.hasCommits());
    }

    @Test
    void newFileIsUntracked() throws IOException {
        t.write("a.txt", "a");
        assertEquals(List.of("a.txt"), status().untracked());
    }

    @Test
    void stagedFileIsAddition() throws IOException {
        t.write("a.txt", "a").add("a.txt");
        Status status = status();
        assertEquals(List.of("a.txt"), status.stagedAdded());
        assertEquals(List.of(), status.untracked());
    }

    @Test
    void editAfterStagingIsUnstagedModification() throws IOException {
        t.write("a.txt", "a").add("a.txt").write("a.txt", "b");
        Status status = status();
        assertEquals(List.of("a.txt"), status.stagedAdded());
        assertEquals(List.of("a.txt"), status.modified());
    }

    @Test
    void committedUnchangedFilesAreClean() throws IOException {
        t.write("a.txt", "a").add("a.txt").commit("c");
        assertTrue(status().isClean());
    }

    @Test
    void reportsModificationsAndDeletions() throws IOException {
        t.write("a.txt", "a").write("b.txt", "b").write("c.txt", "c").add(".").commit("c");
        t.write("a.txt", "changed").add("a.txt");
        t.delete("b.txt").add("b.txt");
        t.write("c.txt", "edited");
        t.write("d.txt", "d").add("d.txt").delete("d.txt");

        Status status = status();
        assertEquals(List.of("d.txt"), status.stagedAdded());
        assertEquals(List.of("a.txt"), status.stagedModified());
        assertEquals(List.of("b.txt"), status.stagedDeleted());
        assertEquals(List.of("c.txt"), status.modified());
        assertEquals(List.of("d.txt"), status.deleted());
        assertEquals(List.of(), status.untracked());
    }
}
