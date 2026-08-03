package ai.riskvision.graveyard.util;

/**
 * Lightweight, zero-dependency User-Agent string parser.
 *
 * <p>Extracts human-readable browser and operating-system identifiers from raw
 * HTTP {@code User-Agent} header values.  Pattern matching uses ordered checks
 * (most-specific first) to avoid mis-classifications such as confusing Chrome
 * for Safari.
 *
 * <p>Designed to be called synchronously at request-time with negligible overhead.
 * No external libraries are required.
 */
public final class UserAgentParser {

    private UserAgentParser() { /* utility class */ }

    // ─── Browser Detection ────────────────────────────────────────────────────

    /**
     * Returns a human-readable browser label, e.g. {@code "Chrome"}, {@code "Firefox"},
     * {@code "Safari"}, {@code "Edge"}, {@code "Opera"}, or {@code "Unknown Browser"}.
     *
     * @param userAgent the raw {@code User-Agent} header value (may be {@code null})
     * @return non-null browser label
     */
    public static String parseBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown Browser";
        }
        // Order matters: Edge, Chrome-based Opera, and Chrome must be checked
        // before generic "Safari" and "Mozilla" matches.
        if (userAgent.contains("Edg/") || userAgent.contains("Edge/")) {
            return "Microsoft Edge";
        }
        if (userAgent.contains("OPR/") || userAgent.contains("Opera")) {
            return "Opera";
        }
        if (userAgent.contains("SamsungBrowser")) {
            return "Samsung Internet";
        }
        if (userAgent.contains("CriOS")) {
            return "Chrome (iOS)";
        }
        if (userAgent.contains("FxiOS")) {
            return "Firefox (iOS)";
        }
        if (userAgent.contains("Firefox")) {
            return "Firefox";
        }
        if (userAgent.contains("Chrome")) {
            return "Chrome";
        }
        if (userAgent.contains("Safari")) {
            return "Safari";
        }
        if (userAgent.contains("MSIE") || userAgent.contains("Trident")) {
            return "Internet Explorer";
        }
        if (userAgent.contains("curl")) {
            return "cURL";
        }
        if (userAgent.contains("PostmanRuntime")) {
            return "Postman";
        }
        return "Unknown Browser";
    }

    // ─── OS Detection ─────────────────────────────────────────────────────────

    /**
     * Returns a human-readable OS label, e.g. {@code "Windows 10/11"}, {@code "macOS"},
     * {@code "Linux"}, {@code "Android"}, {@code "iOS"}, or {@code "Unknown OS"}.
     *
     * @param userAgent the raw {@code User-Agent} header value (may be {@code null})
     * @return non-null OS label
     */
    public static String parseOS(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown OS";
        }
        // Mobile platforms first (most specific)
        if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iPod")) {
            return "iOS";
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        // Desktop platforms
        if (userAgent.contains("Windows NT 10.0")) {
            return "Windows 10/11";
        }
        if (userAgent.contains("Windows NT 6.3")) {
            return "Windows 8.1";
        }
        if (userAgent.contains("Windows NT 6.2")) {
            return "Windows 8";
        }
        if (userAgent.contains("Windows NT 6.1")) {
            return "Windows 7";
        }
        if (userAgent.contains("Windows")) {
            return "Windows";
        }
        if (userAgent.contains("Mac OS X") || userAgent.contains("Macintosh")) {
            return "macOS";
        }
        if (userAgent.contains("CrOS")) {
            return "ChromeOS";
        }
        if (userAgent.contains("Linux")) {
            return "Linux";
        }
        return "Unknown OS";
    }

    /**
     * Sanitize a User-Agent string for safe inclusion in HTML email bodies.
     * Strips angle brackets and script-related characters to prevent injection.
     *
     * @param userAgent raw User-Agent (may be {@code null})
     * @return sanitized string, max 200 characters
     */
    public static String sanitize(String userAgent) {
        if (userAgent == null) return "Unknown";
        String sanitized = userAgent
                .replaceAll("[<>\"'&]", "")
                .trim();
        return sanitized.length() > 200 ? sanitized.substring(0, 200) + "\u2026" : sanitized;
    }
}
