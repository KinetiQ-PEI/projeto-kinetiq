import { useState } from 'react'
import { Link } from 'react-router-dom'
import Card from '../Card.jsx'
import ConfirmDialog from '../ConfirmDialog.jsx'
import SegmentedControl from '../SegmentedControl.jsx'
import AlertRow from './AlertRow.jsx'
import { SEVERITY_ORDER } from '../../data/alerts.js'
import { can } from '../../data/permissions.js'
import './OpenAlertsCard.css'

const FILTER_LABELS = { ALL: 'All', CRITICAL: 'Critical', WARNING: 'Warning', INFO: 'Info' }

// Most serious first, then most recent first.
function byPriority(a, b) {
  const severity = SEVERITY_ORDER.indexOf(a.severity) - SEVERITY_ORDER.indexOf(b.severity)
  return severity !== 0 ? severity : b.timestamp - a.timestamp
}

// Open alerts with severity filter and quick actions (RF10, RF11, RF12).
// "Create maintenance ticket" moves the bike to IN_MAINTENANCE at once, so it asks first (RNF05).
// Actions depend on the user's role (RNF08).
export default function OpenAlertsCard({ alerts, zones, user, onAcknowledge, onCreateTicket }) {
  const [severity, setSeverity] = useState('ALL')
  const [expandedId, setExpandedId] = useState(null)
  const [ticketFor, setTicketFor] = useState(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  const zoneName = Object.fromEntries(zones.map((z) => [z.zoneId, z.name]))
  const count = (sev) => alerts.filter((a) => sev === 'ALL' || a.severity === sev).length
  const visible = alerts.filter((a) => severity === 'ALL' || a.severity === severity).sort(byPriority)

  async function acknowledge(alert) {
    await onAcknowledge(alert)
    setMessage(`Alert for ${alert.bikeId} acknowledged.`)
  }

  async function confirmTicket() {
    setBusy(true)
    try {
      await onCreateTicket(ticketFor)
      setMessage(`Maintenance ticket created for ${ticketFor.bikeId}. The bike is now in maintenance.`)
      setTicketFor(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card
      title="Open alerts"
      className="open-alerts"
      action={
        <Link to="/maintenance" className="card__link">
          View all
        </Link>
      }
    >
      <SegmentedControl
        size="sm"
        label="Filter alerts by severity"
        value={severity}
        onChange={setSeverity}
        options={['ALL', ...SEVERITY_ORDER].map((sev) => ({
          value: sev,
          label: `${FILTER_LABELS[sev]} (${count(sev)})`,
        }))}
      />

      {visible.length === 0 ? (
        <p className="open-alerts__empty">No open alerts for this filter.</p>
      ) : (
        <ul className="open-alerts__list">
          {visible.map((alert) => (
            <AlertRow
              key={alert.alertId}
              alert={alert}
              zoneName={zoneName[alert.zoneId]}
              expanded={expandedId === alert.alertId}
              onToggle={() => setExpandedId((id) => (id === alert.alertId ? null : alert.alertId))}
              canAcknowledge={can(user, 'alerts:acknowledge')}
              canCreateTicket={can(user, 'tickets:create')}
              onAcknowledge={() => acknowledge(alert)}
              onCreateTicket={() => setTicketFor(alert)}
            />
          ))}
        </ul>
      )}

      <p role="status" className="sr-only">
        {message}
      </p>

      <ConfirmDialog
        open={Boolean(ticketFor)}
        title="Create maintenance ticket?"
        confirmLabel="Create ticket"
        busy={busy}
        onConfirm={confirmTicket}
        onCancel={() => setTicketFor(null)}
      >
        {ticketFor && (
          <>
            <p>
              Bike <strong>{ticketFor.bikeId}</strong>: {ticketFor.details}.
            </p>
            <p>The bike will be taken out of service (In maintenance) right away and a technician will be notified.</p>
          </>
        )}
      </ConfirmDialog>
    </Card>
  )
}
