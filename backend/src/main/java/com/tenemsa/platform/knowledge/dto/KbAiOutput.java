package com.tenemsa.platform.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The JSON the model must return. citations are source numbers ([1], [2]...) from the prompt. */
public record KbAiOutput(boolean answerable, @NotBlank @Size(max = 4000) String answer, @Size(max = 12) List<Integer> citations) {}
