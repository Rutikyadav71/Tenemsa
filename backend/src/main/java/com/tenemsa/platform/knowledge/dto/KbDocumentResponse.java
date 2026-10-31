package com.tenemsa.platform.knowledge.dto;

import com.tenemsa.platform.knowledge.model.DocumentAudience;
import com.tenemsa.platform.knowledge.model.DocumentStatus;
import com.tenemsa.platform.knowledge.model.KbDocument;

import java.time.LocalDateTime;

/** stale = the document was embedded with an older model than the one now configured; reindex it to make it searchable again. */
public record KbDocumentResponse(Long id, String title, String fileName, DocumentAudience audience, DocumentStatus status,
                                 String errorMessage, int charCount, int chunkCount, String embeddingModel, boolean stale,
                                 String uploadedByName, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static KbDocumentResponse from(KbDocument d, String uploadedBy, String currentModel) {
        boolean stale = d.getStatus() == DocumentStatus.READY && d.getEmbeddingModel() != null && !d.getEmbeddingModel().equals(currentModel);
        return new KbDocumentResponse(d.getId(), d.getTitle(), d.getFileName(), d.getAudience(), d.getStatus(), d.getErrorMessage(),
                d.getCharCount(), d.getChunkCount(), d.getEmbeddingModel(), stale, uploadedBy, d.getCreatedAt(), d.getUpdatedAt());
    }
}
