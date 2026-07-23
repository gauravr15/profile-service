package com.odin.profileservice.service;

public class ContactSnapshotV2ConflictException extends RuntimeException {
    public ContactSnapshotV2ConflictException() {
        super("Snapshot identifier is already bound to different canonical content");
    }
}
