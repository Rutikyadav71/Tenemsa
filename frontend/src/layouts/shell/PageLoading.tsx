import { SkeletonList, Skeleton } from "../../shared/ui";

/** Shown for a moment while a page's code downloads. Inside the app it keeps the sidebar and top bar in place. */
export default function PageLoading({ full = false }: { full?: boolean }) {
  return (
    <div role="status" aria-live="polite" aria-label="Loading" style={full ? { minHeight: "60vh", display: "grid", alignContent: "center", maxWidth: 520, margin: "0 auto", padding: 24, gap: 12 } : { display: "grid", gap: 12, paddingTop: 8 }}>
      <Skeleton width="40%" height={26} />
      <SkeletonList rows={4} />
    </div>
  );
}
