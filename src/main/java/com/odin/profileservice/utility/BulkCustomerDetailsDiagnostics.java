package com.odin.profileservice.utility;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.MDC;

public final class BulkCustomerDetailsDiagnostics {

    public static final String TARGET_PATH = "/v1/bulk/customer/details";
    public static final String TARGET_METHOD = "POST";

    private BulkCustomerDetailsDiagnostics() {
    }

    public static boolean isTargetRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String method = request.getMethod();
        String path = request.getRequestURI();
        return TARGET_METHOD.equalsIgnoreCase(method)
                && path != null
                && path.endsWith(TARGET_PATH);
    }

    public static String traceId(HttpServletRequest request) {
        String traceId = MDC.get("correlationId");
        if (traceId == null || traceId.isBlank()) {
            traceId = request != null ? request.getHeader("X-Correlation-ID") : null;
        }
        return traceId == null || traceId.isBlank() ? "unknown" : traceId;
    }

    public static long requestByteLength(HttpServletRequest request) {
        if (request == null) {
            return -1L;
        }
        long byteLength = request.getContentLengthLong();
        return byteLength >= 0 ? byteLength : -1L;
    }

    public static String safeCategory(String exceptionClassName) {
        if (exceptionClassName == null) {
            return "unknown";
        }
        if (exceptionClassName.endsWith("HttpMessageNotReadableException")) {
            return "request-body-not-readable";
        }
        if (exceptionClassName.endsWith("MethodArgumentNotValidException")) {
            return "validation-failed";
        }
        if (exceptionClassName.endsWith("BindException")) {
            return "binding-failed";
        }
        if (exceptionClassName.endsWith("MissingServletRequestParameterException")) {
            return "missing-request-parameter";
        }
        if (exceptionClassName.endsWith("MissingServletRequestPartException")) {
            return "missing-request-part";
        }
        if (exceptionClassName.endsWith("ServletRequestBindingException")) {
            return "request-binding-failed";
        }
        if (exceptionClassName.endsWith("MethodArgumentTypeMismatchException")
                || exceptionClassName.endsWith("TypeMismatchException")) {
            return "type-mismatch";
        }
        if (exceptionClassName.endsWith("ConstraintViolationException")) {
            return "constraint-violation";
        }
        return "bad-request";
    }
}