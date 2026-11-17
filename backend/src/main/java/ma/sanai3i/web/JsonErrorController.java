package ma.sanai3i.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Replaces Spring's whitelabel page: errors outside the controllers answer a short JSON code. */
@RestController
public class JsonErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<Map<String, String>> error(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = raw instanceof Integer i && i >= 400 && i < 600 ? i : 404;
        String code = switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 413 -> "FILE_TOO_BIG";
            default -> status >= 500 ? "SERVER_ERROR" : "ERROR";
        };
        return ResponseEntity.status(status).body(Map.of("code", code));
    }
}
