package com.induztek.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.induztek.security.ApiError;
import com.induztek.security.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/imagenes");

    private static void assertError(ResponseEntity<ApiError> response, int status, String code, String path) {
        assertEquals(status, response.getStatusCode().value());
        ApiError body = response.getBody();
        assertEquals(status, body.status());
        assertEquals(code, body.code());
        assertEquals(path, body.path());
    }

    @Test
    void uploadLargerThanTheLimitIs413NotAGeneric500() {
        assertError(handler.handleUploadTooLarge(new MaxUploadSizeExceededException(10L), request), 413, "PAYLOAD_TOO_LARGE", "/api/imagenes");
    }

    @Test
    void missingMultipartPartIs400() {
        assertError(handler.handleMissingPart(new MissingServletRequestPartException("file"), request), 400, "MISSING_PART", "/api/imagenes");
    }

    @Test
    void unsupportedAndNotAcceptableMediaTypesHaveTheirOwnCodes() {
        assertError(handler.handleUnsupportedMediaType(new HttpMediaTypeNotSupportedException("text/plain"), request),
                415, "UNSUPPORTED_MEDIA_TYPE", "/api/imagenes");
        assertError(handler.handleNotAcceptable(new HttpMediaTypeNotAcceptableException("no"), request),
                406, "NOT_ACCEPTABLE", "/api/imagenes");
    }

    @Test
    void responseStatusExceptionKeepsItsStatusNameAsCodeAndReason() {
        ResponseEntity<ApiError> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again later"), request);

        assertError(response, 429, "TOO_MANY_REQUESTS", "/api/imagenes");
        assertEquals("Too many failed attempts. Try again later", response.getBody().message());
    }

    @Test
    void unexpectedErrorsNeverLeakInternalDetails() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("password=secret"), request);

        assertError(response, 500, "INTERNAL_ERROR", "/api/imagenes");
        assertFalse(response.getBody().message().contains("secret"));
    }
}
