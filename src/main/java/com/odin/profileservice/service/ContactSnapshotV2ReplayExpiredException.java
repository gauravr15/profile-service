package com.odin.profileservice.service;

public class ContactSnapshotV2ReplayExpiredException extends RuntimeException {
    public ContactSnapshotV2ReplayExpiredException() {
        super("Snapshot replay metadata has expired");
    }
}
