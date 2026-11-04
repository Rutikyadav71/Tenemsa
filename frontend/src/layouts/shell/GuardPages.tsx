import { Link } from "react-router-dom";
import { LuCompass, LuLock } from "react-icons/lu";
import { EmptyState } from "../../shared/ui";

export function AccessDenied({ home }: { home: string }) {
  return (
    <EmptyState icon={<LuLock size={26} />} title="You don't have access to this page"
      text="Your role doesn't include this area. If you think that's a mistake, ask a workspace admin to review your access."
      action={<Link className="tn-btn tn-btn-primary" to={home}>Go to my dashboard</Link>} />
  );
}

export function NotFound({ home }: { home: string }) {
  return (
    <EmptyState icon={<LuCompass size={26} />} title="We couldn't find that page"
      text="The link may be out of date, or the page may have moved."
      action={<Link className="tn-btn tn-btn-primary" to={home}>Back to dashboard</Link>} />
  );
}
