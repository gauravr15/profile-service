package com.odin.profileservice.config;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.TypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.odin.profileservice.utility.BulkCustomerDetailsDiagnostics;

@ControllerAdvice
public class BulkCustomerDetailsExceptionAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BulkCustomerDetailsExceptionAdvice.class);

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {
        logBadRequest("http-message-not-readable", ex, request, HttpStatus.BAD_REQUEST);
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {
        logBadRequest("method-argument-not-valid", ex, request, HttpStatus.BAD_REQUEST);
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {
        logBadRequest("missing-servlet-request-parameter", ex, request, HttpStatus.BAD_REQUEST);
        return super.handleMissingServletRequestParameter(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {
        logBadRequest("servlet-request-binding", ex, request, HttpStatus.BAD_REQUEST);
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleBindException(
            BindException ex,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {
        logBadRequest("bind-exception", ex, request, HttpStatus.BAD_REQUEST);
        return super.handleBindException(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {
        logBadRequest("type-mismatch", ex, request, HttpStatus.BAD_REQUEST);
        return super.handleTypeMismatch(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException ex,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {
        logBadRequest("missing-servlet-request-part", ex, request, HttpStatus.BAD_REQUEST);
        return super.handleMissingServletRequestPart(ex, headers, status, request);
    }

    private void logBadRequest(String stage, Exception ex, WebRequest request, HttpStatus status) {
        HttpServletRequest servletRequest = extractRequest(request);
        if (servletRequest == null || !BulkCustomerDetailsDiagnostics.isTargetRequest(servletRequest)) {
            return;
        }

        log.error(
                "[BULK-CUSTOMER-DETAILS][REACHABILITY] stage={} traceId={} method={} path={} httpStatus={} exceptionClass={} safeErrorCategory={} requestBytes={}",
                stage,
                BulkCustomerDetailsDiagnostics.traceId(servletRequest),
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                status.value(),
                ex.getClass().getName(),
                BulkCustomerDetailsDiagnostics.safeCategory(ex.getClass().getName()),
                BulkCustomerDetailsDiagnostics.requestByteLength(servletRequest),
                ex);
    }

    private HttpServletRequest extractRequest(WebRequest request) {
        if (request instanceof ServletWebRequest) {
            return ((ServletWebRequest) request).getRequest();
        }
        return null;
    }
}