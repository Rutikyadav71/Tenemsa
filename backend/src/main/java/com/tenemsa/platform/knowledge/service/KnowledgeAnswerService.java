package com.tenemsa.platform.knowledge.service;

import com.tenemsa.platform.ai.model.AiFeature;
import com.tenemsa.platform.ai.service.AiGateway;
import com.tenemsa.platform.ai.service.AiResult;
import com.tenemsa.platform.ai.service.PromptCatalog;
import com.tenemsa.platform.ai.service.PromptInput;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.knowledge.config.KbProperties;
import com.tenemsa.platform.knowledge.dto.KbAiOutput;
import com.tenemsa.platform.knowledge.dto.KbAnswerResponse;
import com.tenemsa.platform.knowledge.dto.KbStatusResponse;
import com.tenemsa.platform.knowledge.embedding.EmbeddingClient;
import com.tenemsa.platform.knowledge.embedding.EmbeddingGateway;
import com.tenemsa.platform.knowledge.model.DocumentStatus;
import com.tenemsa.platform.knowledge.repository.KbDocumentRepository;
import com.tenemsa.platform.knowledge.vector.VectorStore;
import com.tenemsa.platform.company.repository.CompanyRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Answers questions from the company's own documents (retrieval-augmented generation):
 *   1. embed the question, 2. find the closest passages the CALLER is allowed to read (company + audience are part
 *   of the search itself), 3. if nothing is close enough say so without calling the model, 4. otherwise give the model
 *   only those passages and require it to cite them, 5. keep only citations that point at passages it was given.
 * An answer that cites nothing valid is reported as "not found" rather than shown as if it were grounded.
 */
@Service
public class KnowledgeAnswerService {

    static final String NOT_FOUND = "I couldn't find that in your company's documents. Try rephrasing, or ask HR or your manager.";
    private static final int SNIPPET_CHARS = 320;

    private final KbProperties props;
    private final EmbeddingGateway embeddings;
    private final VectorStore vectors;
    private final AiGateway gateway;
    private final PromptCatalog prompts;
    private final UserRepository users;
    private final KbDocumentRepository documents;
    private final CompanyRepository companies;
    private final AuthContext authContext;

    public KnowledgeAnswerService(KbProperties props, EmbeddingGateway embeddings, VectorStore vectors, AiGateway gateway,
                                  PromptCatalog prompts, UserRepository users, KbDocumentRepository documents,
                                  CompanyRepository companies, AuthContext authContext) {
        this.props = props;
        this.embeddings = embeddings;
        this.vectors = vectors;
        this.gateway = gateway;
        this.prompts = prompts;
        this.users = users;
        this.documents = documents;
        this.companies = companies;
        this.authContext = authContext;
    }

    public KbStatusResponse status() {
        Long companyId = authContext.currentCompanyId();
        boolean companyEnabled = companies.findById(companyId).map(c -> c.isAiEnabled()).orElse(false);
        int ready = (int) documents.findByCompanyIdOrderByCreatedAtDescIdDesc(companyId).stream()
                .filter(d -> d.getStatus() == DocumentStatus.READY)
                .filter(d -> d.getAudience() == com.tenemsa.platform.knowledge.model.DocumentAudience.ALL_STAFF || canReadManagement())
                .count();
        return new KbStatusResponse(embeddings.isAvailable() && gateway.isAvailable() && companyEnabled, companyEnabled,
                vectors.name(), embeddings.modelName(), ready);
    }

    private boolean canReadManagement() { return authContext.hasAuthority("kb:read:management"); }

    /** Not transactional on purpose: embedding and the model call can take seconds. */
    public KbAnswerResponse ask(String rawQuestion) {
        Long companyId = authContext.currentCompanyId();
        User me = users.findByEmailAndCompanyId(authContext.currentEmail(), companyId)
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
        String question = rawQuestion.strip();

        float[] q = embeddings.embed(companyId, me.getEmpId(), List.of(question), EmbeddingClient.Task.QUERY).get(0);
        List<VectorStore.Hit> hits = vectors.search(companyId, q, embeddings.modelName(), props.getTopK(), canReadManagement())
                .stream().filter(h -> h.score() >= props.getMinScore()).toList();
        if (hits.isEmpty()) return new KbAnswerResponse(false, NOT_FOUND, List.of(), null, null);

        StringBuilder sources = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            VectorStore.Hit h = hits.get(i);
            sources.append('[').append(i + 1).append("] ").append(PromptInput.oneLine(h.title(), 120)).append(":\n")
                    .append(PromptInput.neutralise(h.content())).append("\n\n");
        }
        String system = prompts.text(PromptCatalog.KB_ANSWER, PromptCatalog.V1);
        String user = "Question:\n" + PromptInput.fence("QUESTION", question) + "\n\n"
                + "Sources:\n" + PromptInput.fence("SOURCES", sources.toString());
        AiResult<KbAiOutput> result = gateway.generate(new AiGateway.AiCall<>(AiFeature.KB_ANSWER,
                PromptCatalog.versionLabel(PromptCatalog.KB_ANSWER, PromptCatalog.V1), system, user, KbAiOutput.class));

        Set<Integer> cited = new LinkedHashSet<>();
        if (result.data().citations() != null) {
            for (Integer n : result.data().citations()) if (n != null && n >= 1 && n <= hits.size()) cited.add(n);
        }
        if (!result.data().answerable() || cited.isEmpty()) {
            return new KbAnswerResponse(false, NOT_FOUND, List.of(), result.model(), result.promptVersion());
        }
        List<KbAnswerResponse.Source> out = new ArrayList<>();
        for (int n : cited) {
            VectorStore.Hit h = hits.get(n - 1);
            String c = h.content().strip();
            out.add(new KbAnswerResponse.Source(n, h.documentId(), h.title(),
                    c.length() <= SNIPPET_CHARS ? c : c.substring(0, SNIPPET_CHARS) + "…", Math.round(h.score() * 1000) / 1000.0));
        }
        return new KbAnswerResponse(true, result.data().answer().strip(), out, result.model(), result.promptVersion());
    }
}
