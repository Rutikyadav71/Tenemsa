import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Card, CardHeader, EmptyState, ErrorState, SkeletonList } from "../../../shared/ui";

interface Props<T> {
  title: string;
  subtitle?: string;
  items: T[] | null;
  loading: boolean;
  failed: boolean;
  onRetry: () => void;
  empty: { title: string; text: string };
  render: (item: T) => ReactNode;
  keyOf: (item: T) => string | number;
  more?: { to: string; label: string };
  actions?: ReactNode;
}

/** A titled list with its own loading, empty and error states. */
export default function ListCard<T>({ title, subtitle, items, loading, failed, onRetry, empty, render, keyOf, more, actions }: Props<T>) {
  return (
    <Card pad={false}>
      <CardHeader title={title} subtitle={subtitle} actions={actions} />
      {loading ? <div className="db-pad"><SkeletonList rows={3} /></div>
        : failed ? <ErrorState message={`${title} couldn't be loaded.`} onRetry={onRetry} />
        : !items?.length ? <EmptyState title={empty.title} text={empty.text} />
        : <ul className="db-list">{items.map(i => <li key={keyOf(i)}>{render(i)}</li>)}</ul>}
      {more && !loading && !failed && !!items?.length && <div className="db-foot"><Link to={more.to}>{more.label}</Link></div>}
    </Card>
  );
}
