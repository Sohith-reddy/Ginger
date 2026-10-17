package dev.ginger.merge;

import dev.ginger.TestRepo;
import dev.ginger.objects.ObjectId;
import dev.ginger.ops.Checkout;
import dev.ginger.refs.Head;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeBaseTest {

    @TempDir
    Path tmp;

    @Test
    void findsBaseOfSharedAndDivergingHistory() throws IOException {
        TestRepo t = TestRepo.init(tmp);
        ObjectId root = t.write("a", "1").add("a").commit("root");
        ObjectId base = t.write("a", "2").add("a").commit("base");
        t.repo().refs().updateBranch("feature", base);
        ObjectId ours = t.write("a", "3").add("a").commit("ours");

        Checkout.moveTo(t.repo(), base, Head.onBranch("feature"), false);
        ObjectId theirs = t.write("b", "x").add("b").commit("theirs");

        var store = t.repo().objects();
        assertEquals(Optional.of(base), MergeBase.find(store, ours, theirs));
        assertEquals(Optional.of(base), MergeBase.find(store, theirs, ours));
        assertEquals(Optional.of(root), MergeBase.find(store, root, ours));
        assertEquals(Optional.of(ours), MergeBase.find(store, ours, ours));
        assertTrue(MergeBase.isAncestor(store, root, theirs));
        assertFalse(MergeBase.isAncestor(store, ours, theirs));
    }
}
