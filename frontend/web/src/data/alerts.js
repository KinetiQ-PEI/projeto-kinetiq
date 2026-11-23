// Alert metadata. Types and severities follow docs/schemas/alert-schema.json.
export const ALERT_TYPE_LABELS = {
  LOW_BATTERY: 'Low battery',
  UNAUTHORIZED_ZONE: 'Unauthorized zone',
  MAINTENANCE_REQUIRED: 'Maintenance required',
  CONNECTION_LOST: 'Connection lost',
  // TODO (to confirm): not in alert-schema.json yet. Needed for RF12 (ML anomaly detection).
  ML_ANOMALY: 'ML anomaly',
}

// Severity order, most serious first.
export const SEVERITY_ORDER = ['CRITICAL', 'WARNING', 'INFO']
