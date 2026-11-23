import Card from '../Card.jsx'
import { BIKE_STATUS, BIKE_STATUS_ORDER } from '../../data/bikeStatus.js'
import './FleetStateCard.css'

// "Fleet by state": stacked bar + legend with counts (RF06).
export default function FleetStateCard({ byStatus }) {
  const summary = BIKE_STATUS_ORDER.map((key) => `${BIKE_STATUS[key].label} ${byStatus[key]}`).join(', ')

  return (
    <Card title="Fleet by state" className="fleet-state">
      <div role="img" aria-label={summary} className="fleet-state__bar">
        {BIKE_STATUS_ORDER.map((key) => (
          <div key={key} style={{ flex: byStatus[key], background: BIKE_STATUS[key].color }} />
        ))}
      </div>

      <ul className="fleet-state__legend">
        {BIKE_STATUS_ORDER.map((key) => (
          <li key={key} className="fleet-state__item">
            <span className="fleet-state__label">
              <span
                className="fleet-state__swatch"
                style={{ background: BIKE_STATUS[key].color }}
                aria-hidden="true"
              />
              {BIKE_STATUS[key].label}
            </span>
            <span className="fleet-state__count">{byStatus[key]}</span>
          </li>
        ))}
      </ul>
    </Card>
  )
}
