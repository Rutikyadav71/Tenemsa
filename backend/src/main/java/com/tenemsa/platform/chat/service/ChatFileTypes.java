package com.tenemsa.platform.chat.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What may be sent in chat and what it is served as. The client's content type is never trusted: the type
 * comes from the extension, and for the common binary types the bytes must match. Anything that a browser
 * could run (html, svg, js ...) or that is executable is simply not on the list.
 */
public final class ChatFileTypes {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("md", "text/plain; charset=utf-8"),
            Map.entry("csv", "text/csv; charset=utf-8"),
            Map.entry("zip", "application/zip"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("mp4", "video/mp4"));
    private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private ChatFileTypes() {}

    public static boolean isAllowed(String ext) { return TYPES.containsKey(ext); }
    public static String contentType(String ext) { return TYPES.get(ext); }
    public static boolean isImage(String ext) { return IMAGES.contains(ext); }

    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Last path segment only, no control characters, at most 255 characters. */
    public static String cleanName(String raw) {
        String n = raw == null ? "file" : raw;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (n.isEmpty()) n = "file";
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    public static boolean looksLike(String ext, byte[] b) {
        switch (ext) {
            case "pdf": return startsWith(b, '%', 'P', 'D', 'F');
            case "docx": case "xlsx": case "pptx": case "zip": return startsWith(b, 'P', 'K');
            case "doc": case "xls": case "ppt": return startsWith(b, 0xD0, 0xCF, 0x11, 0xE0);
            case "png": return startsWith(b, 0x89, 'P', 'N', 'G');
            case "jpg": case "jpeg": return startsWith(b, 0xFF, 0xD8, 0xFF);
            case "gif": return startsWith(b, 'G', 'I', 'F', '8');
            case "webp": return startsWith(b, 'R', 'I', 'F', 'F');
            case "wav": return startsWith(b, 'R', 'I', 'F', 'F');
            case "mp3": return startsWith(b, 'I', 'D', '3') || (b.length > 1 && (b[0] & 0xFF) == 0xFF);
            case "mp4": return b.length > 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
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
