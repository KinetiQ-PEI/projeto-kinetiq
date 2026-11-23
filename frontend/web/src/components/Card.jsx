import { useId } from 'react'
import './Card.css'

// White panel with a title row (title + optional meta text or action link).
export default function Card({ title, meta, action, className = '', children }) {
  const titleId = useId()
  return (
    <section className={`card card--panel ${className}`} aria-labelledby={titleId}>
      <div className="card__header">
        <h2 id={titleId} className="card__title">
          {title}
        </h2>
        {meta && <span className="card__meta">{meta}</span>}
        {action}
      </div>
      {children}
    </section>
  )
}
