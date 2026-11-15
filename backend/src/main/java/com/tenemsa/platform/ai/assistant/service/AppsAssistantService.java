package com.tenemsa.platform.ai.assistant.service;

import com.tenemsa.platform.ai.assistant.dto.AppsAiOutput;
import com.tenemsa.platform.ai.assistant.dto.AppsAnswerResponse;
import com.tenemsa.platform.ai.assistant.dto.HrAskRequest;
import com.tenemsa.platform.ai.conversation.service.AiConversationService;
import com.tenemsa.platform.ai.model.AiFeature;
import com.tenemsa.platform.ai.service.AiGateway;
import com.tenemsa.platform.ai.service.AiResult;
import com.tenemsa.platform.ai.service.PromptCatalog;
import com.tenemsa.platform.ai.service.PromptInput;
import com.tenemsa.platform.connections.model.Connection;
import com.tenemsa.platform.connections.provider.ConnectorProvider;
import com.tenemsa.platform.connections.service.ConnectionException;
import com.tenemsa.platform.connections.service.ConnectionService;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Answers questions from the caller's OWN connected apps. Privacy rules enforced here, not by the prompt:
 * only the caller's connections are read (identity comes from the login, never the request); the model has no
 * tools and cannot act in any app; everything read from an app is fenced as untrusted text; only the question and
 * the answer are saved in the chat, not the app data. The call goes through AiGateway, so it counts against the
 * company's monthly token budget like every other AI feature.
 */
@Service
public class AppsAssistantService {

    private static final int ITEMS_PER_APP = 25;
    private static final int LINE_CHARS = 220;

    private final AiGateway gateway;
    private final PromptCatalog prompts;
    private final ConnectionService connections;
    private final UserRepository users;
    private final AuthContext authContext;
    private final AiConversationService conversations;

    public AppsAssistantService(AiGateway gateway, PromptCatalog prompts, ConnectionService connections,
                                UserRepository users, AuthContext authContext, AiConversationService conversations) {
        this.gateway = gateway; this.prompts = prompts; this.connections = connections;
        this.users = users; this.authContext = authContext; this.conversations = conversations;
    }

    public AppsAnswerResponse ask(HrAskRequest req) {
        Long companyId = authContext.currentCompanyId();
        User me = users.findByEmailAndCompanyId(authContext.currentEmail(), companyId)
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
        List<Connection> usable = connections.usableFor(companyId, me.getId());
        if (usable.isEmpty()) {
            throw new IllegalArgumentException("Connect an app first. Open Connected apps, and ask an owner or admin to switch the app on if it is off.");
        }

        List<String> read = new ArrayList<>(), unavailable = new ArrayList<>();
        StringBuilder data = new StringBuilder();
        for (Connection c : usable) {
            ConnectorProvider p = connections.providerOf(c.getProvider()).orElse(null);
            if (p == null) continue;
            try {
                List<ConnectorProvider.Item> items = connections.fetchForAi(c, p);
                data.append("== ").append(p.label()).append(" (").append(items.size()).append(" item(s)) ==\n");
                for (ConnectorProvider.Item i : items.stream().limit(ITEMS_PER_APP).toList()) {
                    data.append("- ").append(PromptInput.oneLine(i.title(), LINE_CHARS));
                    if (i.subtitle() != null && !i.subtitle().isBlank()) data.append(" | ").append(PromptInput.oneLine(i.subtitle(), LINE_CHARS));
                    data.append('\n');
                }
                read.add(p.label());
            } catch (ConnectionException e) {
                unavailable.add(p.label() + ": " + e.getMessage());
                data.append("== ").append(p.label()).append(" ==\nThis app could not be read right now.\n");
            }
        }
        if (read.isEmpty()) throw new IllegalArgumentException(unavailable.isEmpty() ? "None of your connected apps could be read." : String.join(" ", unavailable));

        List<AiConversationService.Turn> earlier = conversations.history(AiConversationService.APPS, req.conversationId());
        String history = AiConversationService.promptBlock(earlier);
        String system = prompts.text(PromptCatalog.APPS_ASSISTANT, PromptCatalog.V1);
        String user = "Now: " + OffsetDateTime.now() + " (today is " + LocalDate.now() + ")\n\n"
                + (history.isEmpty() ? "" : "Earlier in this chat:\n" + PromptInput.fence("HISTORY", history) + "\n\n")
                + "Question:\n" + PromptInput.fence("QUESTION", req.question()) + "\n\n"
                + "Data from the person's connected apps:\n" + PromptInput.fence("DATA", data.toString());
        AiResult<AppsAiOutput> result = gateway.generate(new AiGateway.AiCall<>(AiFeature.CONNECTED_APPS,
                PromptCatalog.versionLabel(PromptCatalog.APPS_ASSISTANT, PromptCatalog.V1), system, user, AppsAiOutput.class));

        String answer = result.data().answer().strip();
        Long chatId = conversations.record(AiConversationService.APPS, req.conversationId(), req.question(), answer, Map.of("appsRead", read));
        return new AppsAnswerResponse(answer, read, unavailable, result.model(), result.promptVersion(), chatId);
    }
}
