package com.tenemsa.platform.ai.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code itemsUsed} are the [numbers] of the data lines the answer relied on; the server turns them into links. */
public record AppsAiOutput(@NotBlank @Size(max = 3500) String answer, @Size(max = 6) List<String> sourcesUsed, @Size(max = 12) List<Integer> itemsUsed) {}
