package com.tenemsa.platform.controller;
import com.tenemsa.platform.model.ChatMessage;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.security.ChatIdentity;
import org.springframework.security.access.AccessDeniedException;
import com.tenemsa.platform.repository.ChatMessageRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatRestController {
    @Autowired ChatMessageRepository chatRepo;
    @Autowired AuthContext authContext;
    @Autowired UserRepository userRepository;

    private Long cid() { return authContext.currentCompanyId(); }

    // Phase 4d: every endpoint here used to trust the ids in the request, so any
    // signed-in user could read another pair's conversation or act on messages
    // that were not theirs. The caller is now derived from the JWT (never the
    // request) and must be a participant. Chat identity follows ChatIdentity:
    // admin-tier users chat as ("ADMIN", database id), everyone else as
    // ("USER", empId).
    private record Me(String tag, Integer chatId) {}

    private Me me() {
        User u = userRepository.findByEmailAndCompanyId(authContext.currentEmail(), cid())
                .orElseThrow(() -> new AccessDeniedException("Not authenticated"));
        String tag = ChatIdentity.tagFor(u.getRole());
        Integer chatId = "ADMIN".equals(tag) ? Integer.valueOf(u.getId().intValue()) : u.getEmpId();
        return new Me(tag, chatId);
    }

    private static boolean is(Me me, Integer id, String role) {
        return me.chatId() != null && me.chatId().equals(id) && me.tag().equals(role);
    }

    private static void requireParticipant(Me me, Integer id1, String role1, Integer id2, String role2) {
        if (!is(me, id1, role1) && !is(me, id2, role2)) {
            throw new AccessDeniedException("You can only access your own conversations.");
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getHistory(
            @RequestParam Integer id1,@RequestParam String role1,
            @RequestParam Integer id2,@RequestParam String role2) {
        requireParticipant(me(), id1, role1, id2, role2);
        return ResponseEntity.ok(chatRepo.getChatBetween(cid(),id1,role1,id2,role2));
    }

    @GetMapping("/unread")
    public ResponseEntity<Long> getUnread(
            @RequestParam Integer senderId,@RequestParam String senderRole,
            @RequestParam Integer receiverId,@RequestParam String receiverRole) {
        if (!is(me(), receiverId, receiverRole)) throw new AccessDeniedException("You can only read your own unread count.");
        return ResponseEntity.ok(chatRepo.countUnread(cid(),senderId,senderRole,receiverId,receiverRole));
    }

    @PutMapping("/read")
    public ResponseEntity<String> markRead(
            @RequestParam Integer senderId,@RequestParam String senderRole,
            @RequestParam Integer receiverId,@RequestParam String receiverRole) {
        if (!is(me(), receiverId, receiverRole)) throw new AccessDeniedException("You can only mark your own messages as read.");
        return ResponseEntity.ok(chatRepo.markAsRead(cid(),senderId,senderRole,receiverId,receiverRole)+" marked READ");
    }

    /** Soft-delete: sets message to "[deleted]" */
    @DeleteMapping("/message/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long id) {
        Me me = me();
        // Only the sender may delete a message.
        return chatRepo.findByIdAndCompanyId(id, cid()).filter(m -> is(me, m.getSenderId(), m.getSenderRole())).map(m -> {
            m.setMessage("[deleted]"); m.setStatus("DELETED");
            chatRepo.save(m);
            return ResponseEntity.ok(Map.of("id",id,"status","DELETED"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** React with emoji; same emoji toggles off */
    @PutMapping("/message/{id}/react")
    public ResponseEntity<?> react(@PathVariable Long id, @RequestBody Map<String,String> body) {
        Me me = me();
        return chatRepo.findByIdAndCompanyId(id, cid()).filter(m -> isParticipant(me, m)).map(m -> {
            String e = body.getOrDefault("reaction","");
            m.setReaction(e.equals(m.getReaction()) ? null : (e.isEmpty() ? null : e));
            chatRepo.save(m);
            return ResponseEntity.ok(Map.of("id",id,"reaction",m.getReaction()!=null?m.getReaction():""));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Toggle star on a message */
    @PutMapping("/message/{id}/star")
    public ResponseEntity<?> star(@PathVariable Long id) {
        Me me = me();
        return chatRepo.findByIdAndCompanyId(id, cid()).filter(m -> isParticipant(me, m)).map(m -> {
            m.setStarred(!Boolean.TRUE.equals(m.getStarred()));
            chatRepo.save(m);
            return ResponseEntity.ok(Map.of("id",id,"starred",m.getStarred()));
        }).orElse(ResponseEntity.notFound().build());
    }

    // A message is visible to its sender and its receiver only.
    private static boolean isParticipant(Me me, ChatMessage m) {
        return is(me, m.getSenderId(), m.getSenderRole()) || is(me, m.getReceiverId(), m.getReceiverRole());
    }

    /** All starred messages between two users */
    @GetMapping("/starred")
    public ResponseEntity<List<ChatMessage>> getStarred(
            @RequestParam Integer id1,@RequestParam String role1,
            @RequestParam Integer id2,@RequestParam String role2) {
        requireParticipant(me(), id1, role1, id2, role2);
        List<ChatMessage> all = chatRepo.getChatBetween(cid(),id1,role1,id2,role2);
        all.removeIf(m -> !Boolean.TRUE.equals(m.getStarred()));
        return ResponseEntity.ok(all);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteConversation(
            @RequestParam Integer id1,@RequestParam String role1,
            @RequestParam Integer id2,@RequestParam String role2) {
        requireParticipant(me(), id1, role1, id2, role2);
        return ResponseEntity.ok(chatRepo.deleteChatBetween(cid(),id1,role1,id2,role2)+" deleted");
    }
}