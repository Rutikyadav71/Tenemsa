import { Component, type ErrorInfo, type ReactNode } from "react";
import { ErrorState } from "../../shared/ui";

/** Keeps one broken page from blanking the whole app: the menu stays usable and the person can retry. */
export default class ErrorBoundary extends Component<{ children: ReactNode; resetKey: string }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() { return { failed: true }; }

  componentDidCatch(error: Error, info: ErrorInfo) { console.error("Page crashed:", error, info.componentStack); }

  componentDidUpdate(prev: { resetKey: string }) {
    if (this.state.failed && prev.resetKey !== this.props.resetKey) this.setState({ failed: false });
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return <ErrorState title="This page ran into a problem" message="Something unexpected happened while showing this page. Your data is safe. Try again, or open another page from the menu."
      onRetry={() => this.setState({ failed: false })} />;
  }
}
