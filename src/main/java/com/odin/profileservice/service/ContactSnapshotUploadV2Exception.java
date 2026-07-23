package com.odin.profileservice.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ContactSnapshotUploadV2Exception extends RuntimeException {
    @Getter
    public enum Reason {
        SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, 1101, "UPLOAD_SESSION_NOT_FOUND"),
        SESSION_EXPIRED(HttpStatus.GONE, 1102, "UPLOAD_SESSION_EXPIRED"),
        SESSION_ALREADY_COMMITTED(HttpStatus.CONFLICT, 1103,
                "UPLOAD_SESSION_ALREADY_COMMITTED"),
        SESSION_LIMIT(HttpStatus.TOO_MANY_REQUESTS, 1104, "UPLOAD_SESSION_LIMIT_EXCEEDED"),
        SESSION_CONFLICT(HttpStatus.CONFLICT, 1105, "UPLOAD_SESSION_CONFLICT"),
        CHUNK_INDEX_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, 1110,
                "CHUNK_INDEX_OUT_OF_RANGE"),
        CHUNK_INDEX_REUSE_CONFLICT(HttpStatus.CONFLICT, 1111,
                "CHUNK_INDEX_REUSE_CONFLICT"),
        CHUNK_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, 1112,
                "CHUNK_VALIDATION_FAILED"),
        CHUNK_COUNT_INCOMPLETE(HttpStatus.CONFLICT, 1120, "CHUNK_COUNT_INCOMPLETE"),
        DECLARED_TOTAL_MISMATCH(HttpStatus.CONFLICT, 1121, "DECLARED_TOTAL_MISMATCH"),
        SNAPSHOT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, 1122, "SNAPSHOT_TOO_LARGE");

        private final HttpStatus httpStatus;
        private final int applicationCode;
        private final String errorCode;

        Reason(HttpStatus httpStatus, int applicationCode, String errorCode) {
            this.httpStatus = httpStatus;
            this.applicationCode = applicationCode;
            this.errorCode = errorCode;
        }
    }

    private final Reason reason;

    public ContactSnapshotUploadV2Exception(Reason reason) {
        super(reason.errorCode);
        this.reason = reason;
    }
}
