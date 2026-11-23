import './StatCard.css'

// One KPI tile: label, big value, small hint below.
// tone="positive" colours the hint green (it never carries meaning by colour alone).
export default function StatCard({ label, value, hint, tone = 'muted' }) {
  return (
    <div className="card stat-card">
      <span className="stat-card__label">{label}</span>
      <span className="stat-card__value">{value}</span>
      {hint && <span className={`stat-card__hint stat-card__hint--${tone}`}>{hint}</span>}
    </div>
  )
}
