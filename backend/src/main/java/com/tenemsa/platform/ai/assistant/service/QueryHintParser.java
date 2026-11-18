package com.tenemsa.platform.ai.assistant.service;

import com.tenemsa.platform.connections.provider.QueryHints;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a time window and quoted search words out of a question with plain rules (no AI call, so it costs no tokens
 * and cannot be talked into anything). English only; a question it does not understand simply gets the default fetch.
 */
public final class QueryHintParser {

    private static final Pattern NEXT_DAYS = Pattern.compile("\\bnext (\\d{1,2}) days\\b");
    private static final Pattern QUOTED = Pattern.compile("(?<!\\w)[\"\u201C]([^\"\u201C\u201D]{2,60}?)[\"\u201D](?!\\w)");

    private QueryHintParser() {}

    public static QueryHints parse(String question, LocalDate today, ZoneId zone) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        LocalDate from = null, to = null;   // inclusive start, exclusive end
        String label = "";
        Matcher n = NEXT_DAYS.matcher(q);
        if (n.find()) {
            from = today; to = today.plusDays(Integer.parseInt(n.group(1)) + 1L); label = "the next " + n.group(1) + " days";
        } else if (q.contains("day after tomorrow")) {
            from = today.plusDays(2); to = from.plusDays(1); label = "the day after tomorrow";
        } else if (q.contains("tomorrow")) {
            from = today.plusDays(1); to = from.plusDays(1); label = "tomorrow";
        } else if (q.contains("yesterday")) {
            from = today.minusDays(1); to = today; label = "yesterday";
        } else if (q.contains("today") || q.contains("tonight")) {
            from = today; to = today.plusDays(1); label = "today";
        } else if (q.contains("next week")) {
            from = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)); to = from.plusDays(7); label = "next week";
        } else if (q.contains("last week")) {
            to = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); from = to.minusDays(7); label = "last week";
        } else if (q.contains("this week")) {
            from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); to = from.plusDays(7); label = "this week";
        } else if (q.contains("next month")) {
            from = today.withDayOfMonth(1).plusMonths(1); to = from.plusMonths(1); label = "next month";
        } else if (q.contains("last month")) {
            from = today.withDayOfMonth(1).minusMonths(1); to = from.plusMonths(1); label = "last month";
        } else if (q.contains("this month")) {
            from = today.withDayOfMonth(1); to = from.plusMonths(1); label = "this month";
        }
        List<String> terms = new ArrayList<>();
        Matcher m = QUOTED.matcher(question == null ? "" : question);
        while (m.find() && terms.size() < 3) {
            String t = m.group(1).strip();
            if (t.length() >= 2 && !terms.contains(t)) terms.add(t);
        }
        return new QueryHints(from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.atStartOfDay(zone).toInstant(), terms, label);
    }
}
