package com.tenemsa.platform.ai.service;

/**
 * Helpers for putting user-written text into a prompt safely. User text is always wrapped in
 * <<<TAG ... TAG>>> markers that the system prompt declares to be untrusted data, and any marker-like
 * text inside the user's own words is defused so it cannot close the fence early.
 */
public final class PromptInput {

    private PromptInput() {}

    public static String fence(String tag, String text) {
        return "<<<" + tag + "\n" + neutralise(text == null ? "" : text.strip()) + "\n" + tag + ">>>";
    }

    public static String neutralise(String text) {
        return text.replace("<<<", "< < <").replace(">>>", "> > >");
    }

    /** Single line, collapsed whitespace, length-limited. For titles and short labels inside the prompt. */
    public static String oneLine(String text, int max) {
        if (text == null) return "";
        String t = neutralise(text.replaceAll("\\s+", " ").strip());
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
