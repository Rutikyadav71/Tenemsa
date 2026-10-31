package com.tenemsa.platform.document.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** What may be stored, what it is served as, and a check that the bytes really are that type. The client's content type is never trusted. */
final class FileTypes {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("md", "text/plain; charset=utf-8"),
            Map.entry("csv", "text/csv; charset=utf-8"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"));
    private static final Set<String> EXTRACTABLE = Set.of("pdf", "docx", "txt", "md", "csv");

    private FileTypes() {}

    static boolean isAllowed(String ext) { return TYPES.containsKey(ext); }

    static boolean extractable(String ext) { return EXTRACTABLE.contains(ext); }

    static String contentType(String ext) { return TYPES.get(ext); }

    static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Last path segment only, no control characters, at most 255 characters. */
    static String cleanName(String raw) {
        String n = raw == null ? "document" : raw;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (n.isEmpty()) n = "document";
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    /** Magic-number check for the binary types; text types must not contain NUL bytes. */
    static boolean looksLike(String ext, byte[] b) {
        switch (ext) {
            case "pdf": return startsWith(b, '%', 'P', 'D', 'F');
            case "docx":
            case "xlsx": return startsWith(b, 'P', 'K', 3, 4);
            case "png": return startsWith(b, 0x89, 'P', 'N', 'G');
            case "jpg":
            case "jpeg": return startsWith(b, 0xFF, 0xD8, 0xFF);
            default:
                for (int i = 0; i < Math.min(b.length, 4096); i++) if (b[i] == 0) return false;
                return true;
        }
    }

    private static boolean startsWith(byte[] b, int... expected) {
        if (b.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((b[i] & 0xFF) != expected[i]) return false;
        return true;
    }
}
