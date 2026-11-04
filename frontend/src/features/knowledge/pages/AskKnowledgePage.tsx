import { useEffect, useState } from "react";
import { askKnowledge, kbStatus } from "../services/knowledgeService";
import type { KbServiceStatus, KbSource } from "../../../shared/types/models";
import AiChat, { type ChatAnswer } from "../../ai/components/AiChat";

const SUGGESTIONS = [
  "How many days of notice do I need for annual leave?",
  "What is the company policy on working from home?",
  "How do I claim travel expenses?",
];

function sources(details: unknown) {
  const d = details as { found?: boolean; sources?: KbSource[] } | null;
  if (!d?.found || !d.sources?.length) return null;
  return (
    <div className="ai-extra">
      <div style={{ marginBottom: 4 }}>Sources</div>
      {d.sources.map(s => (
        <details key={s.n}><summary>[{s.n}] {s.title}</summary><div className="ai-snippet">{s.snippet}</div></details>
      ))}
    </div>
  );
}

export default function AskKnowledgePage() {
  const [status, setStatus] = useState<KbServiceStatus | null>(null);
  useEffect(() => { kbStatus().then(setStatus).catch(() => {}); }, []);

  const ask = async (question: string, conversationId: number | null): Promise<ChatAnswer> => {
    const res = await askKnowledge(question, conversationId);
    return { conversationId: res.conversationId, answer: res.answer, details: { found: res.found, sources: res.sources } };
  };

  const blocked = !status ? null
    : !status.companyEnabled ? { tone: "error" as const, text: "Tenemsa AI is switched off for your company." }
    : !status.available ? { tone: "error" as const, text: "Document answers aren't available right now. Please contact your administrator." }
    : status.readyDocuments === 0 ? { tone: "info" as const, text: "No documents are available to you yet. Ask HR to add the company policies." }
    : null;

  return (
    <AiChat kind="KB_ASK" title="Ask documents"
      subtitle="Ask about policies and procedures. Answers come only from your company's documents and show where they were found."
      welcomeTitle="What would you like to know?"
      welcomeText="I'll search your company's documents and tell you where the answer came from. Your chats are saved, and follow-up questions work."
      placeholder="Ask a question about company policy" suggestions={SUGGESTIONS}
      ask={ask} blocked={blocked} renderDetails={sources}
      note="Tenemsa AI can make mistakes. Check the cited source, and ask HR about anything important." />
  );
}
