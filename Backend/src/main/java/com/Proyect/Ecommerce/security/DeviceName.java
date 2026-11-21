package com.Proyect.Ecommerce.security;

/**
 * HU-07 · "Chrome en Windows" from the browser's User-Agent, for emails a person has to recognize.
 * Only the common browsers and systems; anything else is "un dispositivo desconocido".
 */
public final class DeviceName {

    private DeviceName() {
    }

    public static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "un dispositivo desconocido";
        }
        String browser = browser(userAgent);
        String system = system(userAgent);
        if (browser == null && system == null) {
            return "un dispositivo desconocido";
        }
        if (browser == null) {
            return "un dispositivo " + system;
        }
        return system == null ? browser : browser + " en " + system;
    }

    // Order matters: Edge and Opera also say "Chrome", and Chrome also says "Safari".
    private static String browser(String ua) {
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("SamsungBrowser/")) return "Samsung Internet";
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) return "Firefox";
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) return "Chrome";
        if (ua.contains("Safari/")) return "Safari";
        return null;
    }

    // Android and iOS before Linux and Mac: their User-Agents mention those too.
    private static String system(String ua) {
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone")) return "iPhone";
        if (ua.contains("iPad")) return "iPad";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "Mac";
        if (ua.contains("CrOS")) return "Chromebook";
        if (ua.contains("Linux")) return "Linux";
        return null;
    }
}
