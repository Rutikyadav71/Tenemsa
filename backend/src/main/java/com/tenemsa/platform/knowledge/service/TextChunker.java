package com.tenemsa.platform.knowledge.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into passages of about {@code target} characters, preferring paragraph and sentence boundaries, and
 * starting each passage with the tail of the previous one ({@code overlap} characters) so an answer that straddles a
 * boundary is still found. Pure and deterministic.
 */
public final class TextChunker {

    private TextChunker() {}

    public static List<String> chunk(String text, int target, int overlap) {
        if (target < 100) throw new IllegalArgumentException("Chunk size must be at least 100 characters.");
        int tail = Math.max(0, Math.min(overlap, target / 3));
        List<String> pieces = new ArrayList<>();
        for (String paragraph : text.split("\\n{2,}")) {
            String p = paragraph.strip();
            if (p.isEmpty()) continue;
            if (p.length() <= target) { pieces.add(p); continue; }
            for (String sentence : p.split("(?<=[.!?])\\s+")) {
                String s = sentence.strip();
                while (s.length() > target) {               // a "sentence" longer than a chunk: cut at a space
                    int cut = s.lastIndexOf(' ', target);
                    if (cut < target / 2) cut = target;
                    pieces.add(s.substring(0, cut).strip());
                    s = s.substring(cut).strip();
                }
                if (!s.isEmpty()) pieces.add(s);
            }
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (current.length() > 0 && current.length() + 1 + piece.length() > target) {
                String done = current.toString().strip();
                chunks.add(done);
                current = new StringBuilder(overlapTail(done, tail));
            }
            if (current.length() > 0) current.append(current.charAt(current.length() - 1) == '\n' ? "" : "\n");
            current.append(piece);
        }
        if (current.toString().strip().length() > 0) {
            String last = current.toString().strip();
            // do not emit a final chunk that is only the overlap of the previous one
            if (chunks.isEmpty() || !chunks.get(chunks.size() - 1).endsWith(last)) chunks.add(last);
        }
        return chunks;
    }

    private static String overlapTail(String previous, int tail) {
        if (tail == 0 || previous.length() <= tail) return "";
        String t = previous.substring(previous.length() - tail);
        int space = t.indexOf(' ');
        return space >= 0 ? t.substring(space + 1) : t;      // start at a word boundary
    }
}
