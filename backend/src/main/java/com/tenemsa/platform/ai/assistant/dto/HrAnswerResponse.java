package com.tenemsa.platform.ai.assistant.dto;

import java.util.List;

public record HrAnswerResponse(String answer, List<String> factsUsed, String model, String promptVersion) {}
