package com.odin.profileservice.controller;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ContactSnapshotV2Request;
import com.odin.profileservice.dto.ContactSnapshotV2Response;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.ContactSnapshotV2RevisionException;
import com.odin.profileservice.service.ContactSnapshotV2Service;
import com.odin.profileservice.service.ContactSnapshotV2ValidationException;
import com.odin.profileservice.service.ContactSnapshotV2ConflictException;
import com.odin.profileservice.service.ContactSnapshotV2ReplayExpiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@Slf4j
@RestController
@RequestMapping("/v2/contacts")
@RequiredArgsConstructor
public class ContactSnapshotV2Controller {
    private static final int STALE_REVISION_CODE = 1001;
    private static final int DEPENDENCY_FAILURE_CODE = 1002;
    private static final int SNAPSHOT_ID_REUSE_CONFLICT_CODE = 1003;
    private static final int SNAPSHOT_REPLAY_EXPIRED_CODE = 1004;

    private final ContactSnapshotV2Service snapshotService;

    @PostMapping("/sync")
    public ResponseEntity<ResponseDTO> synchronize(
            @RequestBody ContactSnapshotV2Request request,
            @RequestHeader("customerId") String customerId) {
        try {
            ContactSnapshotV2Response result = snapshotService.replaceSnapshot(customerId, request);
            log.info("V2 contact snapshot applied revision={} submittedCount={} canonicalCount={} "
                            + "addedCount={} removedCount={} retainedCount={}",
                    result.getRevision(),
                    result.getSubmittedCount(),
                    result.getCanonicalCount(),
                    result.getAddedCount(),
                    result.getRemovedCount(),
                    result.getRetainedCount());
            return ResponseEntity.ok(ResponseDTO.builder()
                    .statusCode(ResponseCodes.SUCCESS_CODE)
                    .status(ResponseCodes.SUCCESS)
                    .data(result)
                    .build());
        } catch (ContactSnapshotV2ValidationException ex) {
            return ResponseEntity.badRequest().body(failure(
                    ResponseCodes.INVALID_REQUEST, "V2 snapshot validation failed", null));
        } catch (ContactSnapshotV2RevisionException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(failure(
                    STALE_REVISION_CODE,
                    "V2 snapshot revision is stale",
                    Collections.singletonMap("current_revision", ex.getCurrentRevision())));
        } catch (ContactSnapshotV2ConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(failure(
                    SNAPSHOT_ID_REUSE_CONFLICT_CODE,
                    "V2 snapshot identifier reuse conflict",
                    null));
        } catch (ContactSnapshotV2ReplayExpiredException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(failure(
                    SNAPSHOT_REPLAY_EXPIRED_CODE,
                    "V2 snapshot replay window expired",
                    null));
        } catch (DataAccessException ex) {
            log.error("V2 contact snapshot persistence failed category={}",
                    ex.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(failure(
                    ResponseCodes.INTERNAL_SERVER_ERROR, "V2 snapshot persistence failed", null));
        } catch (IllegalStateException ex) {
            log.warn("V2 contact snapshot dependency failed category={}",
                    ex.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(failure(
                    DEPENDENCY_FAILURE_CODE, "V2 snapshot dependency unavailable", null));
        } catch (RuntimeException ex) {
            log.error("V2 contact snapshot failed category={}", ex.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(failure(
                    ResponseCodes.INTERNAL_SERVER_ERROR, "V2 snapshot failed", null));
        }
    }

    private ResponseDTO failure(int statusCode, String message, Object data) {
        return ResponseDTO.builder()
                .statusCode(statusCode)
                .status(ResponseCodes.FAILURE)
                .message(message)
                .data(data)
                .build();
    }
}
