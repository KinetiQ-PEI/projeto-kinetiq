import { useCallback, useEffect, useState } from 'react'

/**
 * Runs an async loader and tracks its state.
 * `loader` must be stable (defined outside the component or wrapped in useCallback).
 * While reloading, the previous data is kept, so the page does not flicker.
 */
export default function useAsync(loader) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    loader().then(
      (data) => {
        if (!cancelled) setState({ status: 'success', data, error: null })
      },
      (error) => {
        if (!cancelled) setState((previous) => ({ ...previous, status: 'error', error }))
      },
    )
    return () => {
      cancelled = true
    }
  }, [loader, attempt])

  const reload = useCallback(() => {
    setState((previous) => ({ ...previous, status: 'loading', error: null }))
    setAttempt((n) => n + 1)
  }, [])

  return { ...state, reload }
}
