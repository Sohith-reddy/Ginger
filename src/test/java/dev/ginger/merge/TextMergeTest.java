package dev.ginger.merge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextMergeTest {

    private static TextMerge.Result merge(String base, String ours, String theirs) {
        return TextMerge.merge(base, ours, theirs, "HEAD", "feature").orElseThrow();
    }

    @Test
    void combinesNonOverlappingChanges() {
        TextMerge.Result result = merge("a\nb\nc\nd\ne\n", "A\nb\nc\nd\ne\n", "a\nb\nc\nd\nE\n");
        assertFalse(result.conflicted());
        assertEquals("A\nb\nc\nd\nE\n", result.text());
    }

    @Test
    void keepsInsertionsAndDeletionsFromBothSides() {
        TextMerge.Result result = merge("a\nb\nc\nd\n", "a\nnew\nb\nc\nd\n", "a\nb\nc\n");
        assertFalse(result.conflicted());
        assertEquals("a\nnew\nb\nc\n", result.text());
    }

    @Test
    void identicalChangesDoNotConflict() {
        TextMerge.Result result = merge("a\nb\n", "a\nX\n", "a\nX\n");
        assertFalse(result.conflicted());
        assertEquals("a\nX\n", result.text());
    }

    @Test
    void overlappingChangesConflictWithMarkers() {
        TextMerge.Result result = merge("a\nb\nc\n", "a\nours\nc\n", "a\ntheirs\nc\n");
        assertTrue(result.conflicted());
        assertEquals("a\n<<<<<<< HEAD\nours\n=======\ntheirs\n>>>>>>> feature\nc\n", result.text());
    }

    @Test
    void addAddWithEmptyBaseConflicts() {
        TextMerge.Result result = merge("", "mine", "yours\n");
        assertTrue(result.conflicted());
        assertEquals("<<<<<<< HEAD\nmine\n=======\nyours\n>>>>>>> feature\n", result.text());
    }
}
