import { useCallback, useEffect, useState } from "react";
import { askHrAssistant, getAiStatus } from "../services/aiService";
import type { AiStatus } from "../../../shared/types/models";
import AiChat, { type ChatAnswer } from "../components/AiChat";

const SUGGESTIONS = [
  "How many days of leave have I had approved this year?",
  "Do I have any leave requests still pending?",
  "How many days was I present in the last two months?",
  "When was my most recent leave?",
];

/** Only the text the person should see: the answer and which of their records it was based on. */
function facts(details: unknown) {
  const list = (details as { factsUsed?: string[] } | null)?.factsUsed;
  return list && list.length > 0 ? (
    <div className="ai-extra"><details><summary>Based on {list.length} of your records</summary><div className="ai-snippet">{list.join("\n")}</div></details></div>
  ) : null;
}

export default function AssistantPage() {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const loadStatus = useCallback(() => { getAiStatus().then(setStatus).catch(() => {}); }, []);
  useEffect(() => { loadStatus(); }, [loadStatus]);

  const ask = async (question: string, conversationId: number | null): Promise<ChatAnswer> => {
    const res = await askHrAssistant(question, conversationId);
    return { conversationId: res.conversationId, answer: res.answer, details: { factsUsed: res.factsUsed } };
  };

  const blocked = status && !status.enabled
    ? { tone: "error" as const, text: status.companyEnabled ? "Tenemsa AI isn't available right now. Please contact your administrator." : "Tenemsa AI is switched off for your company." }
    : null;

  return (
    <AiChat kind="HR_ASSISTANT" title="AI Assistant"
      subtitle="Ask about your own leave and attendance. It only sees your records, can't change anything, and doesn't know company policy."
      welcomeTitle="How can I help with your leave and attendance?"
      welcomeText="Ask in your own words. Your chats are saved, so you can come back to one and keep going."
      placeholder="Ask about your leave or attendance" suggestions={SUGGESTIONS}
      ask={ask} blocked={blocked} renderDetails={facts} onAnswered={loadStatus}
      note={<>Tenemsa AI can make mistakes. For leave decisions, check the Leave page or ask HR.{status?.enabled && ` ${status.requestsLeftThisHour} question${status.requestsLeftThisHour === 1 ? "" : "s"} left this hour.`}</>} />
  );
}
