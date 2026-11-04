import { useEffect, useState } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { useToast } from "../../../shared/ui";

/** "Also email me" switch. In-app notifications are always on. */
export default function EmailToggle() {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [saving, setSaving] = useState(false);
  const toast = useToast();

  useEffect(() => {
    axiosInstance.get<{ emailEnabled: boolean }>("/api/notifications/settings").then(r => setEnabled(r.data.emailEnabled)).catch(() => {});
  }, []);

  if (enabled === null) return null;

  const change = async (value: boolean) => {
    setSaving(true);
    try {
      const r = await axiosInstance.put<{ emailEnabled: boolean }>("/api/notifications/settings", { emailEnabled: value });
      setEnabled(r.data.emailEnabled);
    } catch (e) { toast.fail(e, "We couldn't change that setting."); }
    finally { setSaving(false); }
  };

  return (
    <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12, color: "var(--text-2)", cursor: "pointer" }}>
      <input type="checkbox" checked={enabled} disabled={saving} onChange={e => change(e.target.checked)} />
      Also email me these notifications
    </label>
  );
}
