package com.tenemsa.platform.ai.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.ai.conversation.dto.ConversationDtos;
import com.tenemsa.platform.ai.conversation.model.AiConversation;
import com.tenemsa.platform.ai.conversation.model.AiConversationMessage;
import com.tenemsa.platform.ai.conversation.repository.AiConversationMessageRepository;
import com.tenemsa.platform.ai.conversation.repository.AiConversationRepository;
import com.tenemsa.platform.ai.service.PromptInput;
import com.tenemsa.platform.common.ResourceNotFoundException;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Saved chats for the two conversational AI features. Chats are private to the person who had them (company + user
 * are part of every lookup), capped in number and length, and deleting a chat deletes its messages. The model's
 * "memory" is simply the last few turns of the saved chat, fenced as untrusted data in the next prompt.
 */
@Service
public class AiConversationService {

    public static final String HR = "HR_ASSISTANT";
    public static final String KB = "KB_ASK";

    static final int MAX_CHATS_PER_KIND = 50;
    static final int MAX_MESSAGES = 100;
    static final int HISTORY_MESSAGES = 8;
    static final int HISTORY_CHARS = 600;
    static final int TITLE_CHARS = 60;

    /** A turn handed back to the caller so it can build the next prompt. */
    public record Turn(String role, String content) {}

    private final AiConversationRepository chats;
    private final AiConversationMessageRepository messages;
    private final UserRepository users;
    private final AuthContext authContext;
    private final ObjectMapper json;

    public AiConversationService(AiConversationRepository chats, AiConversationMessageRepository messages,
                                 UserRepository users, AuthContext authContext, ObjectMapper json) {
        this.chats = chats;
        this.messages = messages;
        this.users = users;
        this.authContext = authContext;
        this.json = json;
    }

    private record Who(Long companyId, Long userId) {}

    private Who me() {
        Long companyId = authContext.currentCompanyId();
        User u = users.findByEmailAndCompanyId(authContext.currentEmail(), companyId)
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
        return new Who(companyId, u.getId());
    }

    private void requireKind(String kind) {
        String needed = HR.equals(kind) ? "ai:assistant:self" : KB.equals(kind) ? "kb:ask" : null;
        if (needed == null) throw new IllegalArgumentException("Unknown chat type.");
        if (!authContext.hasAuthority(needed)) throw new AccessDeniedException("You can't use this feature.");
    }

    private AiConversation owned(Long id) {
        Who w = me();
        return chats.findByIdAndCompanyIdAndUserId(id, w.companyId(), w.userId())
                .orElseThrow(() -> new ResourceNotFoundException("That chat doesn't exist."));
    }

    // ---- used by the two AI services -------------------------------------------------------

    /** The last few turns of an existing chat, oldest first. Empty for a new chat. 404 if it isn't the caller's. */
    @Transactional(readOnly = true)
    public List<Turn> history(String kind, Long conversationId) {
        if (conversationId == null) return List.of();
        requireKind(kind);
        AiConversation c = owned(conversationId);
        if (!c.getKind().equals(kind)) throw new ResourceNotFoundException("That chat doesn't exist.");
        if (messages.countByConversationId(c.getId()) >= MAX_MESSAGES) {
            throw new IllegalArgumentException("This chat is full. Start a new chat to keep going.");
        }
        List<AiConversationMessage> all = messages.findByConversationIdOrderByIdAsc(c.getId());
        return all.subList(Math.max(0, all.size() - HISTORY_MESSAGES), all.size()).stream()
                .map(m -> new Turn(m.getRole(), m.getContent())).toList();
    }

    /** History as a prompt block ("Person: ... / Assistant: ..."), or empty text when there is none. */
    public static String promptBlock(List<Turn> turns) {
        if (turns.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Turn t : turns) {
            sb.append("USER".equals(t.role()) ? "Person: " : "Assistant: ").append(PromptInput.oneLine(t.content(), HISTORY_CHARS)).append('\n');
        }
        return sb.toString().strip();
    }

    /** Saves one question and its answer, creating the chat when {@code conversationId} is null. Returns the chat id. */
    @Transactional
    public Long record(String kind, Long conversationId, String question, String answer, Object details) {
        requireKind(kind);
        Who w = me();
        AiConversation c;
        if (conversationId == null) {
            c = new AiConversation();
            c.setCompanyId(w.companyId());
            c.setUserId(w.userId());
            c.setKind(kind);
            c.setTitle(titleFrom(question));
            c = chats.save(c);
            trim(w, kind);
        } else {
            c = owned(conversationId);
            c.setUpdatedAt(LocalDateTime.now());
            chats.save(c);
        }
        add(c.getId(), "USER", question.strip(), null);
        add(c.getId(), "ASSISTANT", answer, details);
        return c.getId();
    }

    private void add(Long chatId, String role, String content, Object details) {
        AiConversationMessage m = new AiConversationMessage();
        m.setConversationId(chatId);
        m.setRole(role);
        m.setContent(content);
        if (details != null) {
            try { m.setMetadata(json.writeValueAsString(details)); } catch (JsonProcessingException ignored) { /* shown without extras */ }
        }
        messages.save(m);
    }

    private void trim(Who w, String kind) {
        List<AiConversation> mine = chats.findByCompanyIdAndUserIdAndKindOrderByUpdatedAtDescIdDesc(w.companyId(), w.userId(), kind);
        for (int i = MAX_CHATS_PER_KIND; i < mine.size(); i++) {
            messages.deleteByConversationId(mine.get(i).getId());
            chats.delete(mine.get(i));
        }
    }

    private static String titleFrom(String question) {
        String t = question.strip().replaceAll("\\s+", " ");
        return t.length() <= TITLE_CHARS ? t : t.substring(0, TITLE_CHARS - 1).strip() + "…";
    }

    // ---- used by the controller ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConversationDtos.Summary> list(String kind) {
        requireKind(kind);
        Who w = me();
        return chats.findByCompanyIdAndUserIdAndKindOrderByUpdatedAtDescIdDesc(w.companyId(), w.userId(), kind).stream()
                .map(c -> new ConversationDtos.Summary(c.getId(), c.getKind(), c.getTitle(), c.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public ConversationDtos.Detail get(Long id) {
        AiConversation c = owned(id);
        requireKind(c.getKind());
        List<ConversationDtos.Message> out = messages.findByConversationIdOrderByIdAsc(c.getId()).stream()
                .map(m -> new ConversationDtos.Message(m.getId(), m.getRole(), m.getContent(), parse(m.getMetadata()), m.getCreatedAt())).toList();
        return new ConversationDtos.Detail(c.getId(), c.getKind(), c.getTitle(), c.getUpdatedAt(), out);
    }

    @Transactional
    public ConversationDtos.Summary rename(Long id, String title) {
        AiConversation c = owned(id);
        requireKind(c.getKind());
        c.setTitle(title.strip());
        chats.save(c);
        return new ConversationDtos.Summary(c.getId(), c.getKind(), c.getTitle(), c.getUpdatedAt());
    }

    @Transactional
    public void delete(Long id) {
        AiConversation c = owned(id);
        messages.deleteByConversationId(c.getId());
        chats.delete(c);
    }

    @Transactional
    public int deleteAll(String kind) {
        requireKind(kind);
        Who w = me();
        List<AiConversation> mine = chats.findByCompanyIdAndUserIdAndKindOrderByUpdatedAtDescIdDesc(w.companyId(), w.userId(), kind);
        for (AiConversation c : mine) { messages.deleteByConversationId(c.getId()); chats.delete(c); }
        return mine.size();
    }

    private JsonNode parse(String text) {
        if (text == null || text.isBlank()) return null;
        try { return json.readTree(text); } catch (JsonProcessingException e) { return null; }
    }
}
