interface Props {
  loading: boolean;
  error: string | null;
  empty?: boolean;
  emptyText?: string;
}

/** Shows loading, error, or empty states in place of content. Renders nothing when there is data to show. */
export function Status({ loading, error, empty, emptyText }: Props) {
  if (error) return <p className="error" role="alert">{error}</p>;
  if (loading) return <p className="muted">Loading...</p>;
  if (empty) return <p className="muted">{emptyText ?? "Nothing to show."}</p>;
  return null;
}
