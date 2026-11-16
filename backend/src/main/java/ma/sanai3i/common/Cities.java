package ma.sanai3i.common;

import java.text.Normalizer;
import java.util.Locale;

/** City names come from users and from the map (e.g. "Fès" / "Fes", "Marrakesh" / "Marrakech"). */
public final class Cities {
    private Cities() {
    }

    public static String key(String city) {
        if (city == null) {
            return "";
        }
        String s = Normalizer.normalize(city.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z\\u0600-\\u06ff]", "");
        return switch (s) {
            case "marrakesh", "marrakech" -> "marrakech";
            case "fez", "fes" -> "fes";
            case "tangier", "tanger" -> "tanger";
            case "dardelbeida", "casa" -> "casablanca";
            default -> s;
        };
    }

    public static boolean same(String a, String b) {
        return !key(a).isEmpty() && key(a).equals(key(b));
    }
}
