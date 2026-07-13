package com.odin.profileservice.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.odin.profileservice.dto.*;
import com.odin.profileservice.service.StatusReadinessService;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/v1/status/readiness") @RequiredArgsConstructor
public class StatusReadinessController {
    private final StatusReadinessService service;
    @GetMapping public ResponseEntity<StatusReadinessResponse> get(@RequestHeader("customerId") String userId) {
        return ResponseEntity.ok(service.assess(userId));
    }
    @PostMapping("/repair") public ResponseEntity<StatusReadinessResponse> repair(@RequestHeader("customerId") String userId) {
        return ResponseEntity.ok(service.repair(userId));
    }
}
