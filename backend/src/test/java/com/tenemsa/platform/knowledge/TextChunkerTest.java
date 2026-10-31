package com.tenemsa.platform.knowledge;

import com.tenemsa.platform.knowledge.service.TextChunker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void shortTextIsOneChunk() {
        assertThat(TextChunker.chunk("Employees get 20 days of leave.", 900, 150)).containsExactly("Employees get 20 days of leave.");
    }

    @Test
    void longTextIsSplitWithinTheLimitAndNothingIsLost() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) sb.append("Sentence number ").append(i).append(" explains one rule of the company. ");
        List<String> chunks = TextChunker.chunk(sb.toString().strip(), 500, 80);
        assertThat(chunks.size()).isGreaterThan(5);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(520));
        String all = String.join(" ", chunks);
        for (int i = 0; i < 120; i++) assertThat(all).contains("Sentence number " + i + " ");
    }

    @Test
    void textWithoutSpacesStillSplits() {
        List<String> chunks = TextChunker.chunk("x".repeat(2500), 900, 100);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void blankTextGivesNoChunks() {
        assertThat(TextChunker.chunk("   \n  ", 900, 100)).isEmpty();
    }
}
