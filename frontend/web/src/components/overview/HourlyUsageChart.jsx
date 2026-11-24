import Card from '../Card.jsx'
import { twoDigits } from '../../utils/format.js'
import './HourlyUsageChart.css'

// Card with the bar chart of bikes in use per hour. Bars at 75%+ of the peak are highlighted.
// Screen readers get a data table instead of the drawing (WCAG 1.1.1).
export default function HourlyUsageChart({ dateLabel, points }) {
  if (points.length === 0) return null

  const peak = points.reduce((best, p) => (p.bikes > best.bikes ? p : best), points[0])
  const scaleMax = Math.max(10, Math.ceil(peak.bikes / 10) * 10)
  const first = twoDigits(points[0].hour)
  const last = twoDigits(points[points.length - 1].hour)

  return (
    <Card title="Bikes in use per hour" meta={`${dateLabel} · peak ${peak.bikes} at ${twoDigits(peak.hour)}:00`}>

      <div className="hourly-usage__scroll">
        <div className="hourly-usage__chart" aria-hidden="true">
          <div className="hourly-usage__bars">
            {points.map((p) => (
              <div
                key={p.hour}
                className={`hourly-usage__bar ${p.bikes >= peak.bikes * 0.75 ? 'is-peak' : ''}`}
                style={{ height: `${(p.bikes / scaleMax) * 100}%` }}
              />
            ))}
          </div>
          <div className="hourly-usage__axis">
            {points.map((p) => (
              <span key={p.hour}>{twoDigits(p.hour)}</span>
            ))}
          </div>
        </div>
      </div>

      <table className="sr-only">
        <caption>
          Bikes in use per hour, {first}:00 to {last}:00, {dateLabel}
        </caption>
        <thead>
          <tr>
            <th scope="col">Hour</th>
            <th scope="col">Bikes in use</th>
          </tr>
        </thead>
        <tbody>
          {points.map((p) => (
            <tr key={p.hour}>
              <td>{twoDigits(p.hour)}:00</td>
              <td>{p.bikes}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  )
}
