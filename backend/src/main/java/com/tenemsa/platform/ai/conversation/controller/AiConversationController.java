package com.tenemsa.platform.ai.conversation.controller;

import com.tenemsa.platform.ai.conversation.dto.ConversationDtos;
import com.tenemsa.platform.ai.conversation.service.AiConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Saved AI chats. Which kinds a person may touch is checked in the service (HR_ASSISTANT needs ai:assistant:self,
 * KB_ASK needs kb:ask); a chat is only ever visible to the person who had it.
 */
@RestController
@RequestMapping("/api/ai/conversations")
@PreAuthorize("isAuthenticated()")
public class AiConversationController {

    private final AiConversationService service;

    public AiConversationController(AiConversationService service) { this.service = service; }

    @GetMapping
    public List<ConversationDtos.Summary> list(@RequestParam String kind) { return service.list(kind); }

    @GetMapping("/{id}")
    public ConversationDtos.Detail get(@PathVariable Long id) { return service.get(id); }

    @PatchMapping("/{id}")
    public ConversationDtos.Summary rename(@PathVariable Long id, @Valid @RequestBody ConversationDtos.RenameRequest req) {
        return service.rename(id, req.title());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    @DeleteMapping
    public Map<String, Integer> deleteAll(@RequestParam String kind) { return Map.of("deleted", service.deleteAll(kind)); }
}
