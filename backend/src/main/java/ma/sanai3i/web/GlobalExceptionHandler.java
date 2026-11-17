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

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorBody> tooBig(Exception e) {
        return ResponseEntity.status(413).body(new ErrorBody("FILE_TOO_BIG", Map.of()));
    }

    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<ErrorBody> badUpload(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorBody("FILE_EMPTY", Map.of()));
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

    /** Bad enum / number in a query or path ("?status=XYZ"): a client error, not a 500. */
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class, jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<ErrorBody> badArgument(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION", Map.of()));
    }

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<ErrorBody> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("NOT_FOUND", Map.of()));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorBody> method(Exception e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new ErrorBody("METHOD_NOT_ALLOWED", Map.of()));
    }

    /** Anything unexpected: logged on the server, a bare code for the client (no stack trace, no message). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> unexpected(Exception e) {
        if (e instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException) {
            return null;                                  // client closed a live stream
        }
        if (e instanceof org.springframework.web.ErrorResponse er) {          // 415, 406... keep Spring's status
            return ResponseEntity.status(er.getStatusCode()).body(new ErrorBody("BAD_REQUEST", Map.of()));
        }
        if (e instanceof org.springframework.security.access.AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorBody("FORBIDDEN", Map.of()));
        }
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorBody("SERVER_ERROR", Map.of()));
    }
}
