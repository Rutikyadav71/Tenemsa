package com.tenemsa.platform;

import com.tenemsa.platform.ai.assistant.service.QueryHintParser;
import com.tenemsa.platform.connections.provider.QueryHints;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** The plain-rule reading of "tomorrow", "last week" and quoted words. No AI involved. */
class QueryHintParserTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate WED = LocalDate.of(2026, 11, 18);     // a Wednesday

    private QueryHints parse(String q) { return QueryHintParser.parse(q, WED, UTC); }

    @Test
    void readsCommonWindows() {
        assertThat(parse("what is on tomorrow?").from().toString()).startsWith("2026-11-19");
        assertThat(parse("what is on tomorrow?").to().toString()).startsWith("2026-11-20");
        assertThat(parse("anything today").label()).isEqualTo("today");
        assertThat(parse("my meetings this week").from().toString()).startsWith("2026-11-16");      // Monday
        assertThat(parse("my meetings this week").to().toString()).startsWith("2026-11-23");
        assertThat(parse("what did I do last week").from().toString()).startsWith("2026-11-09");
        assertThat(parse("what did I do last week").to().toString()).startsWith("2026-11-16");
        assertThat(parse("next week").from().toString()).startsWith("2026-11-23");
        assertThat(parse("events this month").from().toString()).startsWith("2026-11-01");
        assertThat(parse("next 3 days").to().toString()).startsWith("2026-11-22");
        assertThat(parse("the day after tomorrow").from().toString()).startsWith("2026-11-20");
    }

    @Test
    void readsQuotedSearchWordsAndCapsThem() {
        QueryHints h = parse("find files about \"Q3 budget\" and \u201Croadmap\u201D and \"a\" and \"one\" and \"two\" and \"three\"");
        assertThat(h.terms()).containsExactly("Q3 budget", "roadmap", "one");
        assertThat(h.hasRange()).isFalse();
    }

    @Test
    void aQuestionWithNeitherGetsTheDefaultFetch() {
        assertThat(parse("which issues are assigned to me?").isEmpty()).isTrue();
        assertThat(QueryHintParser.parse(null, WED, UTC).isEmpty()).isTrue();
    }
}
