import { Link } from 'react-router-dom'
import { Brain, Check, ChevronDown, Info, MapPin, OctagonAlert, TriangleAlert, Wrench } from 'lucide-react'
import { timeAgo, timeAgoShort } from '../../utils/format.js'

// Severity = colour + a different icon shape + hidden text, never colour alone (WCAG 1.4.1).
const SEVERITY = {
  CRITICAL: { label: 'Critical', Icon: OctagonAlert },
  WARNING: { label: 'Warning', Icon: TriangleAlert },
  INFO: { label: 'Info', Icon: Info },
}

const HANDLING_LABEL = {
  ACKNOWLEDGED: 'Acknowledged',
  TICKET_OPEN: 'Ticket open',
}

// One alert: severity, 1-line title, bike pill, zone, time. Clicking the title shows the actions.
export default function AlertRow({ alert, zoneName, expanded, onToggle, canAcknowledge, canCreateTicket, onAcknowledge, onCreateTicket }) {
  const { label, Icon } = SEVERITY[alert.severity] ?? SEVERITY.INFO
  const sev = alert.severity.toLowerCase()
  const handled = alert.handling !== 'OPEN'
  const actionsId = `alert-actions-${alert.alertId}`
  const iso = new Date(alert.timestamp).toISOString()

  return (
    <li className={`alert-row alert-row--${sev} ${handled ? 'is-handled' : ''} ${expanded ? 'is-expanded' : ''}`}>
      <span className="alert-row__icon" title={label}>
        <Icon size={16} aria-hidden="true" />
      </span>

      <div className="alert-row__main">
        <button
          type="button"
          className="alert-row__title"
          aria-expanded={expanded}
          aria-controls={actionsId}
          onClick={onToggle}
        >
          <span className="sr-only">{label}: </span>
          <span className="alert-row__title-text">{alert.details}</span>
          <ChevronDown size={15} className="alert-row__chevron" aria-hidden="true" />
        </button>

        <div className="alert-row__meta">
          <Link to={`/bikes/${alert.bikeId}`} className="bike-pill" aria-label={`Bike ${alert.bikeId}`}>
            {alert.bikeId}
          </Link>
          {zoneName && (
            <span className="alert-row__meta-item">
              <MapPin size={13} aria-hidden="true" />
              {zoneName}
            </span>
          )}
          {alert.alertType === 'ML_ANOMALY' && (
            <span className="alert-row__tag">
              <Brain size={13} aria-hidden="true" />
              ML anomaly
            </span>
          )}
          {handled && (
            <span className="alert-row__status">
              <Check size={13} aria-hidden="true" />
              {HANDLING_LABEL[alert.handling]}
            </span>
          )}
        </div>

        <div id={actionsId} className="alert-row__actions" hidden={!expanded}>
          <Link to={`/live-map?bike=${alert.bikeId}`} className="button button--ghost">
            <MapPin size={14} aria-hidden="true" />
            Locate on map
          </Link>
          {canCreateTicket && alert.handling !== 'TICKET_OPEN' && (
            <button type="button" className="button button--ghost" onClick={onCreateTicket}>
              <Wrench size={14} aria-hidden="true" />
              Create maintenance ticket
            </button>
          )}
          {canAcknowledge && alert.handling === 'OPEN' && (
            <button type="button" className="button button--ghost" onClick={onAcknowledge}>
              <Check size={14} aria-hidden="true" />
              Acknowledge
            </button>
          )}
        </div>
      </div>

      <time className="alert-row__time" dateTime={iso} title={timeAgo(alert.timestamp)}>
        {timeAgoShort(alert.timestamp)}
      </time>
    </li>
  )
}
