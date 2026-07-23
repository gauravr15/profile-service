package com.odin.profileservice.controller;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ContactSnapshotUploadChunkV2Request;
import com.odin.profileservice.dto.ContactSnapshotUploadChunkV2Response;
import com.odin.profileservice.dto.ContactSnapshotUploadSessionV2Request;
import com.odin.profileservice.dto.ContactSnapshotUploadSessionV2Response;
import com.odin.profileservice.dto.ContactSnapshotV2Response;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.ContactSnapshotUploadV2Exception;
import com.odin.profileservice.service.ContactSnapshotUploadV2Service;
import com.odin.profileservice.service.ContactSnapshotV2ConflictException;
import com.odin.profileservice.service.ContactSnapshotV2ReplayExpiredException;
import com.odin.profileservice.service.ContactSnapshotV2RevisionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v2/contacts/sync/sessions")
@RequiredArgsConstructor
public class ContactSnapshotUploadV2Controller {
    private static final int STALE_REVISION_CODE = 1001;
    private static final int SNAPSHOT_ID_REUSE_CONFLICT_CODE = 1003;
    private static final int SNAPSHOT_REPLAY_EXPIRED_CODE = 1004;

    private final ContactSnapshotUploadV2Service uploadService;

    @PostMapping
    public ResponseEntity<ResponseDTO> create(
            @RequestBody ContactSnapshotUploadSessionV2Request request,
            @RequestHeader("customerId") String customerId) {
        try {
            ContactSnapshotUploadSessionV2Response response =
                    uploadService.create(customerId, request);
            return success(response);
        } catch (RuntimeException ex) {
            return failure(ex);
        }
    }

    @PutMapping("/{sessionId}/chunks/{chunkIndex}")
    public ResponseEntity<ResponseDTO> uploadChunk(
            @PathVariable String sessionId,
            @PathVariable int chunkIndex,
            @RequestBody ContactSnapshotUploadChunkV2Request request,
            @RequestHeader("customerId") String customerId) {
        try {
            ContactSnapshotUploadChunkV2Response response =
                    uploadService.uploadChunk(customerId, sessionId, chunkIndex, request);
            return success(response);
        } catch (RuntimeException ex) {
            return failure(ex);
        }
    }

    @PostMapping("/{sessionId}/commit")
    public ResponseEntity<ResponseDTO> commit(
            @PathVariable String sessionId,
            @RequestHeader("customerId") String customerId) {
        try {
            ContactSnapshotV2Response response = uploadService.commit(customerId, sessionId);
            log.info("Chunked V2 contact snapshot committed revision={} submittedCount={} "
                            + "canonicalCount={} addedCount={} removedCount={} retainedCount={}",
                    response.getRevision(), response.getSubmittedCount(),
                    response.getCanonicalCount(), response.getAddedCount(),
                    response.getRemovedCount(), response.getRetainedCount());
            return success(response);
        } catch (RuntimeException ex) {
            return failure(ex);
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ResponseDTO> cancel(
            @PathVariable String sessionId,
            @RequestHeader("customerId") String customerId) {
        try {
            uploadService.cancel(customerId, sessionId);
            return success(Collections.singletonMap("cancelled", true));
        } catch (RuntimeException ex) {
            return failure(ex);
        }
    }

    private ResponseEntity<ResponseDTO> success(Object data) {
        return ResponseEntity.ok(ResponseDTO.builder()
                .statusCode(ResponseCodes.SUCCESS_CODE)
                .status(ResponseCodes.SUCCESS)
                .data(data)
                .build());
    }

    private ResponseEntity<ResponseDTO> failure(RuntimeException exception) {
        if (exception instanceof ContactSnapshotUploadV2Exception) {
            ContactSnapshotUploadV2Exception uploadException =
                    (ContactSnapshotUploadV2Exception) exception;
            ContactSnapshotUploadV2Exception.Reason reason = uploadException.getReason();
            return ResponseEntity.status(reason.getHttpStatus()).body(error(
                    reason.getApplicationCode(), reason.getErrorCode(), null));
        }
        if (exception instanceof ContactSnapshotV2RevisionException) {
            ContactSnapshotV2RevisionException revision =
                    (ContactSnapshotV2RevisionException) exception;
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
                    STALE_REVISION_CODE, "STALE_REVISION",
                    Collections.singletonMap(
                            "current_revision", revision.getCurrentRevision())));
        }
        if (exception instanceof ContactSnapshotV2ConflictException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
                    SNAPSHOT_ID_REUSE_CONFLICT_CODE,
                    "SNAPSHOT_ID_REUSE_CONFLICT", null));
        }
        if (exception instanceof ContactSnapshotV2ReplayExpiredException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
                    SNAPSHOT_REPLAY_EXPIRED_CODE, "SNAPSHOT_REPLAY_EXPIRED", null));
        }
        if (exception instanceof DataAccessException) {
            log.error("Chunked contact snapshot persistence failed category={}",
                    exception.getClass().getSimpleName());
        } else {
            log.warn("Chunked contact snapshot request failed category={}",
                    exception.getClass().getSimpleName());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(
                ResponseCodes.INTERNAL_SERVER_ERROR, "SNAPSHOT_COMMIT_FAILED", null));
    }

    private ResponseDTO error(
            int statusCode,
            String errorCode,
            Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>();
        data.put("error_code", errorCode);
        if (additionalData != null) {
            data.putAll(additionalData);
        }
        return ResponseDTO.builder()
                .statusCode(statusCode)
                .status(ResponseCodes.FAILURE)
                .message(errorCode)
                .data(data)
                .build();
    }
}
