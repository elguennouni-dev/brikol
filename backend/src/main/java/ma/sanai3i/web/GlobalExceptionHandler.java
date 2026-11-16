package ma.sanai3i.web;

import ma.sanai3i.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorBody(String code, Map<String, String> fields) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> api(ApiException e) {
        return ResponseEntity.status(e.status).body(new ErrorBody(e.code, Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                fields.putIfAbsent(fe.getField(), fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> badJson(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorBody("BAD_REQUEST", Map.of()));
    }

    /** Unique constraints (duplicate phone, one offer per artisan per request...) raced past the service checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorBody> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody("CONFLICT", Map.of()));
    }
}
