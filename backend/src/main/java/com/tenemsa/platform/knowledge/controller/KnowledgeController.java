package com.tenemsa.platform.knowledge.controller;

import com.tenemsa.platform.knowledge.dto.KbAskRequest;
import com.tenemsa.platform.knowledge.dto.KbAnswerResponse;
import com.tenemsa.platform.knowledge.dto.KbDocumentResponse;
import com.tenemsa.platform.knowledge.dto.KbStatusResponse;
import com.tenemsa.platform.knowledge.dto.KbTextRequest;
import com.tenemsa.platform.knowledge.model.DocumentAudience;
import com.tenemsa.platform.knowledge.service.KnowledgeAnswerService;
import com.tenemsa.platform.knowledge.service.KnowledgeIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Company knowledge base. Managing documents needs kb:manage; asking needs kb:ask (every role). */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

    private final KnowledgeIngestionService ingestion;
    private final KnowledgeAnswerService answers;

    public KnowledgeController(KnowledgeIngestionService ingestion, KnowledgeAnswerService answers) {
        this.ingestion = ingestion;
        this.answers = answers;
    }

    @PreAuthorize("hasAuthority('kb:ask')")
    @GetMapping("/status")
    public KbStatusResponse status() { return answers.status(); }

    @PreAuthorize("hasAuthority('kb:ask')")
    @PostMapping("/ask")
    public KbAnswerResponse ask(@Valid @RequestBody KbAskRequest req) { return answers.ask(req.question()); }

    @PreAuthorize("hasAuthority('kb:manage')")
    @GetMapping("/documents")
    public List<KbDocumentResponse> list() { return ingestion.list(); }

    @PreAuthorize("hasAuthority('kb:manage')")
    @GetMapping("/documents/{id}")
    public KbDocumentResponse get(@PathVariable Long id) { return ingestion.get(id); }

    @PreAuthorize("hasAuthority('kb:manage')")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public KbDocumentResponse upload(@RequestPart("file") MultipartFile file,
                                     @RequestParam(value = "title", required = false) String title,
                                     @RequestParam(value = "audience", defaultValue = "ALL_STAFF") DocumentAudience audience) {
        return ingestion.addFile(file, title, audience);
    }

    @PreAuthorize("hasAuthority('kb:manage')")
    @PostMapping("/documents/text")
    @ResponseStatus(HttpStatus.CREATED)
    public KbDocumentResponse addText(@Valid @RequestBody KbTextRequest req) {
        return ingestion.addText(req.title(), req.audience(), req.text());
    }

    @PreAuthorize("hasAuthority('kb:manage')")
    @PostMapping("/documents/{id}/reindex")
    public KbDocumentResponse reindex(@PathVariable Long id) { return ingestion.reindex(id); }

    @PreAuthorize("hasAuthority('kb:manage')")
    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { ingestion.delete(id); }
}
