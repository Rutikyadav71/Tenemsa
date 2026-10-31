package com.tenemsa.platform.ai.assistant.service;

import com.tenemsa.platform.ai.assistant.dto.CommunicationAiOutput;
import com.tenemsa.platform.ai.assistant.dto.CommunicationRequest;
import com.tenemsa.platform.ai.assistant.dto.CommunicationResponse;
import com.tenemsa.platform.ai.model.AiFeature;
import com.tenemsa.platform.ai.service.AiGateway;
import com.tenemsa.platform.ai.service.AiResult;
import com.tenemsa.platform.ai.service.PromptCatalog;
import com.tenemsa.platform.ai.service.PromptInput;
import org.springframework.stereotype.Service;

/**
 * Drafts an announcement, reminder, feedback note and so on. Nothing is stored and nothing is sent:
 * the person reads the draft, edits it and sends it themselves from their own mail or chat.
 */
@Service
public class CommunicationWriterService {

    private final AiGateway gateway;
    private final PromptCatalog prompts;

    public CommunicationWriterService(AiGateway gateway, PromptCatalog prompts) {
        this.gateway = gateway;
        this.prompts = prompts;
    }

    public CommunicationResponse draft(CommunicationRequest req) {
        String system = prompts.text(PromptCatalog.COMMUNICATION_WRITER, PromptCatalog.V1);
        String user = "Kind of message: " + req.kind() + "\n"
                + "Audience: " + PromptInput.oneLine(req.audience(), 200) + "\n"
                + "Tone: " + req.tone() + "\n\n"
                + "Key points:\n" + PromptInput.fence("POINTS", req.points());
        AiResult<CommunicationAiOutput> result = gateway.generate(new AiGateway.AiCall<>(AiFeature.COMMUNICATION_DRAFT,
                PromptCatalog.versionLabel(PromptCatalog.COMMUNICATION_WRITER, PromptCatalog.V1), system, user,
                CommunicationAiOutput.class));
        return new CommunicationResponse(result.data().subject().strip(), result.data().body().strip(),
                result.model(), result.promptVersion());
    }
}
