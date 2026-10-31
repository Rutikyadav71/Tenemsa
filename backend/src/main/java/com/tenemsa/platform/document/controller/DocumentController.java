package com.tenemsa.platform.document.controller;

import com.tenemsa.platform.document.dto.DocumentResponse;
import com.tenemsa.platform.document.model.DocumentCategory;
import com.tenemsa.platform.document.model.DocumentVisibility;
import com.tenemsa.platform.document.service.DocumentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Everyone with document:view sees what is visible to them; uploading and deleting need document:upload (HR, owner). */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) { this.service = service; }

    @PreAuthorize("hasAuthority('document:view')")
    @GetMapping
    public List<DocumentResponse> list(@RequestParam(value = "category", required = false) DocumentCategory category) {
        return service.list(category);
    }

    @PreAuthorize("hasAuthority('document:view')")
    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id) { return service.get(id); }

    @PreAuthorize("hasAuthority('document:view')")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        DocumentService.Download d = service.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(d.bytes());
    }

    @PreAuthorize("hasAuthority('document:upload')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(@RequestPart("file") MultipartFile file,
                                   @RequestParam(value = "title", required = false) String title,
                                   @RequestParam(value = "category", defaultValue = "OTHER") DocumentCategory category,
                                   @RequestParam(value = "visibility", defaultValue = "ALL_STAFF") DocumentVisibility visibility,
                                   @RequestParam(value = "ownerEmpId", required = false) Integer ownerEmpId,
                                   @RequestParam(value = "indexForAi", defaultValue = "false") boolean indexForAi) {
        return service.upload(file, title, category, visibility, ownerEmpId, indexForAi);
    }

    @PreAuthorize("hasAuthority('document:upload')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
