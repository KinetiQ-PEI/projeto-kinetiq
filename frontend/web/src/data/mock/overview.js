// Fake data for the Overview page, copied from the mockup (index.html).
// Replace with calls to the API Gateway when the backend exists (RNF10).

const MINUTE = 60 * 1000
const now = Date.now()

export const fleet = {
  name: 'Campus Aveiro corporate fleet',
  registeredBikes: 120,
  // Counts per state (RF06). Active fleet = everything except RETIRED.
  byStatus: {
    AVAILABLE: 68,
    IN_USE: 31,
    IN_MAINTENANCE: 14,
    RETIRED: 7,
  },
}

export const zones = [
  { zoneId: 'north-campus', name: 'North Campus' },
  { zoneId: 'library-hub', name: 'Library Hub' },
  { zoneId: 'station-hub', name: 'Station Hub' },
  { zoneId: 'sports-hall', name: 'Sports Hall' },
]

export const shifts = ['All day', 'Morning', 'Afternoon']

// KPIs that cannot be derived from the counts above (RF09, RF13, RF18).
export const kpis = {
  activeTrips: 31,
  activeTripsDeltaVsYesterday: 6,
  avgTripMinutes: 18,
  co2SavedKg: 412,
  co2Period: 'October',
}

// Bikes in use per hour, 06:00 to 20:00.
export const hourlyUsage = {
  dateLabel: 'Mon 19 Oct',
  points: [
    { hour: 6, bikes: 3 },
    { hour: 7, bikes: 9 },
    { hour: 8, bikes: 24 },
    { hour: 9, bikes: 28 },
    { hour: 10, bikes: 17 },
    { hour: 11, bikes: 12 },
    { hour: 12, bikes: 14 },
    { hour: 13, bikes: 19 },
    { hour: 14, bikes: 15 },
    { hour: 15, bikes: 11 },
    { hour: 16, bikes: 13 },
    { hour: 17, bikes: 22 },
    { hour: 18, bikes: 27 },
    { hour: 19, bikes: 16 },
    { hour: 20, bikes: 8 },
  ],
}

// Open alerts, shaped like FleetAlertEvent (docs/schemas/alert-schema.json).
// `title` is what the UI shows; in the schema this text lives in `details`.
export const openAlerts = [
  {
    schemaVersion: '1.0',
    alertId: '6f1c2a9e-0b1d-4c55-9a51-2d0f3b7e8a01',
    bikeId: 'MB-2026-042',
    timestamp: now - 2 * MINUTE,
    alertType: 'ML_ANOMALY', // TODO (to confirm): not in the schema enum
    severity: 'CRITICAL',
    zoneId: 'library-hub',
    details: 'Battery draining 3× faster than model average',
  },
  {
    schemaVersion: '1.0',
    alertId: '6f1c2a9e-0b1d-4c55-9a51-2d0f3b7e8a02',
    bikeId: 'MB-2026-088',
    timestamp: now - 9 * MINUTE,
    alertType: 'UNAUTHORIZED_ZONE',
    severity: 'WARNING',
    zoneId: 'north-campus',
    details: 'Left the authorized perimeter',
  },
  {
    schemaVersion: '1.0',
    alertId: '6f1c2a9e-0b1d-4c55-9a51-2d0f3b7e8a03',
    bikeId: 'MB-2026-017',
    timestamp: now - 6 * MINUTE,
    alertType: 'LOW_BATTERY',
    severity: 'WARNING',
    zoneId: 'station-hub', // schema says zoneId is only for UNAUTHORIZED_ZONE; kept to show the zone in the UI
    details: 'Battery at 12%',
  },
  {
    schemaVersion: '1.0',
    alertId: '6f1c2a9e-0b1d-4c55-9a51-2d0f3b7e8a04',
    bikeId: 'MB-2026-103',
    timestamp: now - 14 * MINUTE,
    alertType: 'CONNECTION_LOST',
    severity: 'INFO',
    zoneId: 'sports-hall',
    details: 'No signal for 4 minutes',
  },
]

// Bikes per zone and state. Availability % is computed in the UI.
export const zoneStats = [
  { zoneId: 'north-campus', AVAILABLE: 24, IN_USE: 9, IN_MAINTENANCE: 3 },
  { zoneId: 'library-hub', AVAILABLE: 18, IN_USE: 11, IN_MAINTENANCE: 4 },
  { zoneId: 'station-hub', AVAILABLE: 15, IN_USE: 8, IN_MAINTENANCE: 5 },
  { zoneId: 'sports-hall', AVAILABLE: 11, IN_USE: 3, IN_MAINTENANCE: 2 },
]
