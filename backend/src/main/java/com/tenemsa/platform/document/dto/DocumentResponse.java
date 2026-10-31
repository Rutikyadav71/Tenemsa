package com.tenemsa.platform.document.dto;

import com.tenemsa.platform.document.model.DocumentCategory;
import com.tenemsa.platform.document.model.DocumentVisibility;

import java.time.LocalDateTime;

/** indexedForAi = also searchable in the knowledge base. indexNote explains why not, when it was asked for and failed. */
public record DocumentResponse(Long id, String title, DocumentCategory category, DocumentVisibility visibility, Integer ownerEmpId,
                               String ownerName, String fileName, String contentType, long sizeBytes, String uploadedByName,
                               boolean indexedForAi, String indexNote, boolean canDelete, LocalDateTime createdAt) {}
