// Shows loading or error while there is no data yet; otherwise renders children.
export default function DataState({ status, hasData, onRetry, children }) {
  if (hasData) return children

  if (status === 'error') {
    return (
      <div className="card" role="alert">
        <p>Could not load the data.</p>
        <button type="button" className="button" onClick={onRetry}>
          Try again
        </button>
      </div>
    )
  }

  return (
    <p role="status" className="loading-text">
      Loading…
    </p>
  )
}
