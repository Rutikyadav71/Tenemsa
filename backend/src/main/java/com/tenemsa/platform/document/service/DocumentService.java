package com.tenemsa.platform.document.service;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.common.ResourceNotFoundException;
import com.tenemsa.platform.document.dto.DocumentResponse;
import com.tenemsa.platform.document.model.DocumentCategory;
import com.tenemsa.platform.document.model.DocumentVisibility;
import com.tenemsa.platform.document.model.StoredDocument;
import com.tenemsa.platform.document.model.StoredDocumentFile;
import com.tenemsa.platform.document.repository.StoredDocumentFileRepository;
import com.tenemsa.platform.document.repository.StoredDocumentRepository;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.employee.service.EmployeeNameLookup;
import com.tenemsa.platform.knowledge.dto.KbDocumentResponse;
import com.tenemsa.platform.knowledge.model.DocumentAudience;
import com.tenemsa.platform.knowledge.service.KnowledgeIngestionService;
import com.tenemsa.platform.notification.service.NotificationService;
import com.tenemsa.platform.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Company document storage. Who can read what is decided HERE, from the caller's identity, never from anything the
 * client sends: ALL_STAFF for everyone, MANAGEMENT for people holding kb:read:management, PRIVATE for the one employee
 * it is for plus whoever holds document:upload (HR, owner). A document the caller may not read looks exactly like one
 * that does not exist (404).
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final StoredDocumentRepository documents;
    private final StoredDocumentFileRepository files;
    private final UserRepository users;
    private final EmployeeNameLookup names;
    private final AuthContext authContext;
    private final AuditService audit;
    private final NotificationService notifications;
    private final KnowledgeIngestionService knowledge;
    private final TransactionTemplate tx;
    private final long maxCompanyBytes;

    public DocumentService(StoredDocumentRepository documents, StoredDocumentFileRepository files, UserRepository users,
                           EmployeeNameLookup names, AuthContext authContext, AuditService audit, NotificationService notifications,
                           KnowledgeIngestionService knowledge, PlatformTransactionManager txManager,
                           @Value("${documents.max-company-bytes:209715200}") long maxCompanyBytes) {
        this.documents = documents;
        this.files = files;
        this.users = users;
        this.names = names;
        this.authContext = authContext;
        this.audit = audit;
        this.notifications = notifications;
        this.knowledge = knowledge;
        this.tx = new TransactionTemplate(txManager);
        this.maxCompanyBytes = maxCompanyBytes;
    }

    private Long cid() { return authContext.currentCompanyId(); }

    private User caller() {
        return users.findByEmailAndCompanyId(authContext.currentEmail(), cid()).orElseThrow(() -> new AccessDeniedException("Unknown user."));
    }

    private boolean manager() { return authContext.hasAuthority("document:upload"); }

    private boolean canRead(StoredDocument d, User me) {
        return switch (d.getVisibility()) {
            case ALL_STAFF -> true;
            case MANAGEMENT -> authContext.hasAuthority("kb:read:management");
            case PRIVATE -> manager() || (d.getOwnerEmpId() != null && d.getOwnerEmpId().equals(me.getEmpId()));
        };
    }

    private StoredDocument readable(Long id, User me) {
        StoredDocument d = documents.findByIdAndCompanyId(id, cid()).orElseThrow(() -> new ResourceNotFoundException("Document not found."));
        if (!canRead(d, me)) throw new ResourceNotFoundException("Document not found.");
        return d;
    }

    // ---- reading -----------------------------------------------------------------

    public List<DocumentResponse> list(DocumentCategory category) {
        User me = caller();
        List<StoredDocument> visible = documents.findByCompanyIdOrderByCreatedAtDescIdDesc(cid()).stream()
                .filter(d -> category == null || d.getCategory() == category)
                .filter(d -> canRead(d, me)).toList();
        Set<Integer> ids = new HashSet<>();
        for (StoredDocument d : visible) { ids.add(d.getOwnerEmpId()); ids.add(d.getUploadedByEmpId()); }
        Map<Integer, String> who = names.namesFor(cid(), ids);
        List<DocumentResponse> out = new ArrayList<>();
        for (StoredDocument d : visible) out.add(toResponse(d, who, null));
        return out;
    }

    public DocumentResponse get(Long id) {
        StoredDocument d = readable(id, caller());
        return toResponse(d, names.namesFor(cid(), java.util.Arrays.asList(d.getOwnerEmpId(), d.getUploadedByEmpId())), null);
    }

    public record Download(String fileName, String contentType, byte[] bytes) {}

    public Download download(Long id) {
        StoredDocument d = readable(id, caller());
        byte[] bytes = files.findById(d.getId()).map(StoredDocumentFile::getContent)
                .orElseThrow(() -> new ResourceNotFoundException("The file is missing."));
        return new Download(d.getFileName(), d.getContentType(), bytes);
    }

    private DocumentResponse toResponse(StoredDocument d, Map<Integer, String> who, String indexNote) {
        return new DocumentResponse(d.getId(), d.getTitle(), d.getCategory(), d.getVisibility(), d.getOwnerEmpId(),
                d.getOwnerEmpId() == null ? null : who.get(d.getOwnerEmpId()), d.getFileName(), d.getContentType(), d.getSizeBytes(),
                d.getUploadedByEmpId() == null ? null : who.get(d.getUploadedByEmpId()), d.getKbDocumentId() != null, indexNote,
                manager(), d.getCreatedAt());
    }

    // ---- uploading ---------------------------------------------------------------

    public DocumentResponse upload(MultipartFile file, String title, DocumentCategory category, DocumentVisibility visibility,
                                   Integer ownerEmpId, boolean indexForAi) {
        Long companyId = cid();
        User me = caller();
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a file to upload.");
        String fileName = FileTypes.cleanName(file.getOriginalFilename());
        String ext = FileTypes.extension(fileName);
        if (!FileTypes.isAllowed(ext)) {
            throw new IllegalArgumentException("This file type is not allowed. Use pdf, docx, xlsx, txt, md, csv, png or jpg.");
        }
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new IllegalArgumentException("The file could not be read."); }
        if (!FileTypes.looksLike(ext, bytes)) throw new IllegalArgumentException("The file content does not match its ." + ext + " type.");

        if (visibility == DocumentVisibility.PRIVATE) {
            if (ownerEmpId == null) throw new IllegalArgumentException("Choose the employee this document is for.");
            names.requireExistsIfGiven(companyId, ownerEmpId, "Employee");
        } else {
            ownerEmpId = null;
        }
        if (indexForAi) {
            if (visibility == DocumentVisibility.PRIVATE) throw new IllegalArgumentException("Private documents cannot be used for AI answers.");
            if (!FileTypes.extractable(ext)) throw new IllegalArgumentException("Only pdf, docx, txt, md and csv files can be used for AI answers.");
            if (!authContext.hasAuthority("kb:manage")) throw new AccessDeniedException("You are not allowed to add documents to the knowledge base.");
        }
        if (documents.totalBytes(companyId) + bytes.length > maxCompanyBytes) {
            throw new IllegalArgumentException("Your company's document storage is full (" + (maxCompanyBytes / 1_048_576) + " MB). Delete old documents first.");
        }
        String cleanTitle = title == null || title.isBlank()
                ? (fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName) : title.strip();
        if (cleanTitle.length() > 200) cleanTitle = cleanTitle.substring(0, 200);

        final String t = cleanTitle;
        final Integer owner = ownerEmpId;
        final byte[] data = bytes;
        Long id = tx.execute(s -> {
            StoredDocument d = new StoredDocument();
            d.setCompanyId(companyId);
            d.setTitle(t);
            d.setCategory(category == null ? DocumentCategory.OTHER : category);
            d.setVisibility(visibility);
            d.setOwnerEmpId(owner);
            d.setFileName(fileName);
            d.setContentType(FileTypes.contentType(ext));
            d.setSizeBytes(data.length);
            d.setUploadedByEmpId(me.getEmpId());
            d = documents.save(d);
            files.save(new StoredDocumentFile(d.getId(), data));
            return d.getId();
        });
        audit.recordCurrent(AuditAction.DOCUMENT_UPLOADED, "DOCUMENT", String.valueOf(id), "Uploaded \"" + t + "\" (" + visibility + ")");

        String indexNote = null;
        if (indexForAi) {
            try {
                KbDocumentResponse kb = knowledge.addFile(file, t, visibility == DocumentVisibility.MANAGEMENT ? DocumentAudience.MANAGEMENT : DocumentAudience.ALL_STAFF);
                Long kbId = kb.id();
                tx.executeWithoutResult(s -> documents.findByIdAndCompanyId(id, companyId).ifPresent(d -> { d.setKbDocumentId(kbId); documents.save(d); }));
            } catch (RuntimeException e) {
                // The upload itself succeeded; say why it is not searchable instead of failing the whole request.
                indexNote = e.getMessage() == null || e.getMessage().length() > 200 ? "It could not be added to the knowledge base." : e.getMessage();
                log.info("Document {} saved but not indexed: {}", id, e.toString());
            }
        }
        if (visibility == DocumentVisibility.PRIVATE && !owner.equals(me.getEmpId())) {
            notifications.send(companyId, owner, "DOCUMENT_SHARED", "A document was added for you: " + t, null);
        }
        StoredDocument saved = documents.findByIdAndCompanyId(id, companyId).orElseThrow();
        return toResponse(saved, names.namesFor(companyId, java.util.Arrays.asList(saved.getOwnerEmpId(), saved.getUploadedByEmpId())), indexNote);
    }

    // ---- deleting ----------------------------------------------------------------

    public void delete(Long id) {
        StoredDocument d = documents.findByIdAndCompanyId(id, cid()).orElseThrow(() -> new ResourceNotFoundException("Document not found."));
        Long kbId = d.getKbDocumentId();
        String title = d.getTitle();
        tx.executeWithoutResult(s -> {
            files.deleteById(d.getId());
            documents.delete(d);
        });
        if (kbId != null) {
            try { knowledge.delete(kbId); } catch (RuntimeException e) { log.info("Knowledge copy of document {} not removed: {}", id, e.toString()); }
        }
        audit.recordCurrent(AuditAction.DOCUMENT_DELETED, "DOCUMENT", String.valueOf(id), "Deleted \"" + title + "\"");
    }
}
