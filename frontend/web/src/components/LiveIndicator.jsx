import { useEffect, useState } from 'react'
import './LiveIndicator.css'

// "Live · updated 2 s ago". After `staleAfterMs` without new data it turns amber and says
// the data may be outdated (RNF04). It is not a live region: it would be read every second.
export default function LiveIndicator({ updatedAt, staleAfterMs = 60000 }) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  if (!updatedAt) return null

  const seconds = Math.max(0, Math.round((now - updatedAt) / 1000))
  const stale = now - updatedAt > staleAfterMs
  const age = seconds < 60 ? `${seconds} s` : `${Math.floor(seconds / 60)} min`

  return (
    <div className={`live-indicator ${stale ? 'live-indicator--stale' : ''}`}>
      <span className="live-indicator__dot" aria-hidden="true" />
      {stale ? `Data may be outdated · updated ${age} ago` : `Live · updated ${age} ago`}
    </div>
  )
}
