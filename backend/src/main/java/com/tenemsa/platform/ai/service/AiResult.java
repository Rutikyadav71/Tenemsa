package com.tenemsa.platform.ai.service;

/** A validated structured answer plus how it was produced. */
public record AiResult<T>(T data, String model, String promptVersion, Integer inputTokens, Integer outputTokens,
                          long latencyMs, Long logId) {}
