package com.tenemsa.platform.chat.controller;

import com.tenemsa.platform.chat.service.ChatAttachmentService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Chat files: upload before sending, download after. Replaces the old Cloudinary-based /api/upload/chat. */
@RestController
@RequestMapping("/api/chat/attachments")
public class ChatAttachmentController {

    private final ChatAttachmentService service;

    public ChatAttachmentController(ChatAttachmentService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) { return service.store(file); }

    /** Images are shown inline in the conversation; everything else downloads with its original name. */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @RequestParam(value = "download", defaultValue = "false") boolean forceDownload) {
        ChatAttachmentService.Download d = service.download(id);
        boolean inline = d.image() && !forceDownload;
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(d.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(d.bytes());
    }
}
