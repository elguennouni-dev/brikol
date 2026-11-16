package ma.sanai3i.common;

import org.springframework.http.HttpStatus;

/** Business error. The client translates {@code code} (e.g. PHONE_TAKEN) into Darija or French. */
public class ApiException extends RuntimeException {
    public final HttpStatus status;
    public final String code;

    public ApiException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}
