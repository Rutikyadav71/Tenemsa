package com.tenemsa.platform.controller;
import com.tenemsa.platform.model.ChatMessage;
import com.tenemsa.platform.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatRestController {
    @Autowired ChatMessageRepository chatRepo;

    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getHistory(
            @RequestParam Integer id1,@RequestParam String role1,
            @RequestParam Integer id2,@RequestParam String role2) {
        return ResponseEntity.ok(chatRepo.getChatBetween(id1,role1,id2,role2));
    }

    @GetMapping("/unread")
    public ResponseEntity<Long> getUnread(
            @RequestParam Integer senderId,@RequestParam String senderRole,
            @RequestParam Integer receiverId,@RequestParam String receiverRole) {
        return ResponseEntity.ok(chatRepo.countUnread(senderId,senderRole,receiverId,receiverRole));
    }

    @PutMapping("/read")
    public ResponseEntity<String> markRead(
            @RequestParam Integer senderId,@RequestParam String senderRole,
            @RequestParam Integer receiverId,@RequestParam String receiverRole) {
        return ResponseEntity.ok(chatRepo.markAsRead(senderId,senderRole,receiverId,receiverRole)+" marked READ");
    }

    /** Soft-delete: sets message to "[deleted]" */
    @DeleteMapping("/message/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long id) {
        return chatRepo.findById(id).map(m -> {
            m.setMessage("[deleted]"); m.setStatus("DELETED");
            chatRepo.save(m);
            return ResponseEntity.ok(Map.of("id",id,"status","DELETED"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** React with emoji; same emoji toggles off */
    @PutMapping("/message/{id}/react")
    public ResponseEntity<?> react(@PathVariable Long id, @RequestBody Map<String,String> body) {
        return chatRepo.findById(id).map(m -> {
            String e = body.getOrDefault("reaction","");
            m.setReaction(e.equals(m.getReaction()) ? null : (e.isEmpty() ? null : e));
            chatRepo.save(m);
            return ResponseEntity.ok(Map.of("id",id,"reaction",m.getReaction()!=null?m.getReaction():""));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Toggle star on a message */
    @PutMapping("/message/{id}/star")
    public ResponseEntity<?> star(@PathVariable Long id) {
        return chatRepo.findById(id).map(m -> {
            m.setStarred(!Boolean.TRUE.equals(m.getStarred()));
            chatRepo.save(m);
            return ResponseEntity.ok(Map.of("id",id,"starred",m.getStarred()));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** All starred messages between two users */
    @GetMapping("/starred")
    public ResponseEntity<List<ChatMessage>> getStarred(
            @RequestParam Integer id1,@RequestParam String role1,
            @RequestParam Integer id2,@RequestParam String role2) {
        List<ChatMessage> all = chatRepo.getChatBetween(id1,role1,id2,role2);
        all.removeIf(m -> !Boolean.TRUE.equals(m.getStarred()));
        return ResponseEntity.ok(all);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteConversation(
            @RequestParam Integer id1,@RequestParam String role1,
            @RequestParam Integer id2,@RequestParam String role2) {
        return ResponseEntity.ok(chatRepo.deleteChatBetween(id1,role1,id2,role2)+" deleted");
    }
}