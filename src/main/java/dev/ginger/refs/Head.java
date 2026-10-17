package dev.ginger.refs;

import dev.ginger.objects.ObjectId;

public record Head(String branch, ObjectId detached) {

    public static Head onBranch(String branch) {
        return new Head(branch, null);
    }

    public static Head detachedAt(ObjectId id) {
        return new Head(null, id);
    }

    public boolean isDetached() {
        return branch == null;
    }

    public String describe() {
        return isDetached() ? "HEAD detached at " + detached.abbreviate() : "On branch " + branch;
    }
}
