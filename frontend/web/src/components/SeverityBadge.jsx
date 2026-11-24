import './SeverityBadge.css'

// CRITICAL / WARNING / INFO pill. The text is always shown (WCAG 1.4.1: not colour only).
export default function SeverityBadge({ severity }) {
  return <span className={`severity-badge severity-badge--${severity.toLowerCase()}`}>{severity}</span>
}
