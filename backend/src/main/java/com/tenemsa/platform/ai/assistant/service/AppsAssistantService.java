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

import com.tenemsa.platform.connections.provider.QueryHints;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
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

    /** The fixed question behind the daily brief. It names "today", so the calendar is fetched for today. */
    static final String BRIEF_QUESTION = "Give me a short brief of my day today: my meetings in time order (point out any that overlap), "
            + "pull requests waiting for my review, and issues assigned to me. Mention files only if they changed today.";

    private final Map<String, AppsAnswerResponse> briefs = new ConcurrentHashMap<>();

    public AppsAnswerResponse ask(HrAskRequest req) {
        return run(req.question(), req.conversationId(), true);
    }

    /** One answer per person per day, reused until they ask for a refresh. Counts against the AI budget when it is made. */
    public AppsAnswerResponse brief(boolean refresh) {
        Long companyId = authContext.currentCompanyId();
        String key = companyId + ":" + authContext.currentEmail() + ":" + LocalDate.now();
        if (!refresh) {
            AppsAnswerResponse cached = briefs.get(key);
            if (cached != null) return cached;
        }
        AppsAnswerResponse fresh = run(BRIEF_QUESTION, null, false);
        briefs.keySet().removeIf(k -> !k.endsWith(":" + LocalDate.now()));     // yesterday's briefs are of no use
        briefs.put(key, fresh);
        return fresh;
    }

    private AppsAnswerResponse run(String question, Long conversationId, boolean saveChat) {
        Long companyId = authContext.currentCompanyId();
        User me = users.findByEmailAndCompanyId(authContext.currentEmail(), companyId)
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
        List<Connection> usable = connections.usableFor(companyId, me.getId());
        if (usable.isEmpty()) {
            throw new IllegalArgumentException("Connect an app first. Open Connected apps, and ask an owner or admin to switch the app on if it is off.");
        }

        QueryHints hints = QueryHintParser.parse(question, LocalDate.now(), ZoneId.systemDefault());
        List<String> read = new ArrayList<>(), unavailable = new ArrayList<>();
        List<AppsAnswerResponse.AppLink> numbered = new ArrayList<>();      // index + 1 is the [n] the model sees
        StringBuilder data = new StringBuilder();
        for (Connection c : usable) {
            ConnectorProvider p = connections.providerOf(c.getProvider()).orElse(null);
            if (p == null) continue;
            try {
                List<ConnectorProvider.Item> items = connections.fetchForAi(c, p, hints);
                data.append("== ").append(p.label()).append(" (").append(items.size()).append(" item(s)) ==\n");
                for (ConnectorProvider.Item i : items.stream().limit(ITEMS_PER_APP).toList()) {
                    numbered.add(new AppsAnswerResponse.AppLink(p.label(), PromptInput.oneLine(i.title(), 120), safeUrl(i.url())));
                    data.append('[').append(numbered.size()).append("] ").append(PromptInput.oneLine(i.title(), LINE_CHARS));
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

        String history = saveChat ? AiConversationService.promptBlock(conversations.history(AiConversationService.APPS, conversationId)) : "";
        String system = prompts.text(PromptCatalog.APPS_ASSISTANT, PromptCatalog.V1);
        String scope = hints.isEmpty() ? "" : "The data was fetched for this question"
                + (hints.hasRange() ? " for " + (hints.label().isBlank() ? "the period it names" : hints.label()) + " (" + hints.from() + " to " + hints.to() + ")" : "")
                + (hints.terms().isEmpty() ? "" : ", searching for " + String.join(", ", hints.terms().stream().map(t -> PromptInput.oneLine(t, 60)).toList())) + ".\n";
        String user = "Now: " + OffsetDateTime.now() + " (today is " + LocalDate.now() + ")\n" + scope + "\n"
                + (history.isEmpty() ? "" : "Earlier in this chat:\n" + PromptInput.fence("HISTORY", history) + "\n\n")
                + "Question:\n" + PromptInput.fence("QUESTION", question) + "\n\n"
                + "Data from the person's connected apps:\n" + PromptInput.fence("DATA", data.toString());
        AiResult<AppsAiOutput> result = gateway.generate(new AiGateway.AiCall<>(AiFeature.CONNECTED_APPS,
                PromptCatalog.versionLabel(PromptCatalog.APPS_ASSISTANT, PromptCatalog.V1), system, user, AppsAiOutput.class));

        String answer = result.data().answer().strip();
        List<AppsAnswerResponse.AppLink> links = new ArrayList<>();
        if (result.data().itemsUsed() != null) {
            for (Integer n : result.data().itemsUsed()) {
                if (n == null || n < 1 || n > numbered.size()) continue;            // a number we never showed is ignored
                AppsAnswerResponse.AppLink l = numbered.get(n - 1);
                if (!l.url().isBlank() && !links.contains(l)) links.add(l);
            }
        }
        String range = hints.hasRange() ? hints.label() : "";
        Long chatId = saveChat ? conversations.record(AiConversationService.APPS, conversationId, question, answer,
                Map.of("appsRead", read, "links", links, "range", range)) : null;
        return new AppsAnswerResponse(answer, read, unavailable, result.model(), result.promptVersion(), chatId, links, range);
    }

    /** Only plain https links from the app are ever shown as links. */
    private static String safeUrl(String url) {
        return url != null && url.startsWith("https://") && url.length() < 600 ? url : "";
    }
}
