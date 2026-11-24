// Alerts (RF10, RF11, RF12).
// TODO (to confirm with the backend team): endpoint paths are a proposal.
//
// Every alert gets a UI-only `handling` field: OPEN, ACKNOWLEDGED or TICKET_OPEN.

import { USE_MOCKS } from './config.js'
import { fromMock, getJson, postJson } from './http.js'
import * as mock from '../data/mock/overview.js'

// Mock-only memory of what the operator did, so it survives page refreshes (polling).
const mockHandling = new Map()

/** Open alerts, shaped like FleetAlertEvent (docs/schemas/alert-schema.json). */
export function getOpenAlerts() {
  if (!USE_MOCKS) return getJson('/alerts?status=open')
  return fromMock(
    mock.openAlerts.map((alert) => ({ ...alert, handling: mockHandling.get(alert.alertId) ?? 'OPEN' })),
  )
}

/** Mark an alert as seen. It stays in the history. */
export function acknowledgeAlert(alertId) {
  if (!USE_MOCKS) return postJson(`/alerts/${alertId}/acknowledge`)
  mockHandling.set(alertId, 'ACKNOWLEDGED')
  return fromMock(null)
}

/**
 * Open a maintenance ticket from an alert. The bike moves to IN_MAINTENANCE right away
 * (Fleet Management decision), so the UI must ask for confirmation first (RNF05).
 */
export function createMaintenanceTicket({ alertId, bikeId, reason }) {
  if (!USE_MOCKS) return postJson('/maintenance-tickets', { alertId, bikeId, reason })
  mockHandling.set(alertId, 'TICKET_OPEN')
  return fromMock(null)
}
