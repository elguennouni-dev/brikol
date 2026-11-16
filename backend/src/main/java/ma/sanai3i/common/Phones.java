package ma.sanai3i.common;

import org.springframework.http.HttpStatus;

public final class Phones {
    private Phones() {
    }

    /**
     * Normalizes a Moroccan mobile/landline number to +212XXXXXXXXX.
     * Accepts 0612345678, 06 12 34 56 78, +212612345678, 00212612345678 and 212612345678.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE");
        }
        String s = raw.replaceAll("[\\s\\-().]", "");
        if (s.startsWith("00212")) {
            s = "+212" + s.substring(5);
        } else if (s.startsWith("212")) {
            s = "+" + s;
        } else if (s.startsWith("0")) {
            s = "+212" + s.substring(1);
        }
        if (!s.matches("^\\+212[5-7]\\d{8}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE");
        }
        return s;
    }
}
