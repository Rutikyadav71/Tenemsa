package com.tenemsa.platform.knowledge.dto;

public record KbStatusResponse(boolean available, boolean companyEnabled, String vectorStore, String embeddingModel, int readyDocuments) {}
