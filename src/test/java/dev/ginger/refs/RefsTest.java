package dev.ginger.refs;

import dev.ginger.objects.CorruptObjectException;
import dev.ginger.objects.HashUtil;
import dev.ginger.objects.ObjectId;
import dev.ginger.repository.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefsTest {

    @TempDir
    Path tmp;

    private Repository repo;
    private Refs refs;

    @BeforeEach
    void setUp() throws IOException {
        repo = Repository.init(tmp);
        refs = repo.refs();
    }

    @Test
    void newRepositoryIsOnUnbornMain() throws IOException {
        assertEquals(Head.onBranch("main"), refs.head());
        assertTrue(refs.headCommit().isEmpty());
        assertEquals(List.of(), refs.branches());
    }

    @Test
    void branchUpdatesAndDetachedHead() throws IOException {
        ObjectId id = HashUtil.sha256(new byte[]{1});
        refs.updateBranch("main", id);
        assertEquals(id, refs.headCommit().orElseThrow());
        assertEquals(id.hex() + "\n", Files.readString(repo.headsDir().resolve("main")));

        refs.setHead(Head.detachedAt(id));
        assertEquals(Head.detachedAt(id), refs.head());
        assertEquals(id, refs.headCommit().orElseThrow());
    }

    @Test
    void validatesBranchNames() {
        for (String good : List.of("main", "feature-login", "v1.2", "fix_bug")) {
            assertDoesNotThrow(() -> Refs.validateBranchName(good));
        }
        for (String bad : List.of("", "-x", ".x", "a..b", "a/b", "a b", "x.lock", "x.", "HEAD", "../x")) {
            assertThrows(IllegalArgumentException.class, () -> Refs.validateBranchName(bad), bad);
        }
    }

    @Test
    void malformedRefsAreReported() throws IOException {
        Files.writeString(repo.headsDir().resolve("main"), "not-a-hash\n");
        assertThrows(CorruptObjectException.class, () -> refs.headCommit());

        Files.writeString(repo.headFile(), "ref: refs/heads/../../x\n");
        assertThrows(CorruptObjectException.class, () -> refs.head());
    }
}
