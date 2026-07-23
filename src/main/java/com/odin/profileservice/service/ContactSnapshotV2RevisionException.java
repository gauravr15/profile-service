package com.odin.profileservice.service;

public class ContactSnapshotV2RevisionException extends RuntimeException {
    private final long currentRevision;

    public ContactSnapshotV2RevisionException(long currentRevision) {
        super("Snapshot base revision does not match current revision");
        this.currentRevision = currentRevision;
    }

    public long getCurrentRevision() {
        return currentRevision;
    }
}
