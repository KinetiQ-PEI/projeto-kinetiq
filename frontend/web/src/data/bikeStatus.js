// Bike operational states (RF06). Keys match `status` in docs/schemas/telemetry-schema.json.
// Colours come from the mockups ("Fleet by state").
export const BIKE_STATUS = {
  AVAILABLE: { label: 'Available', color: '#22a05a' },
  IN_USE: { label: 'In use', color: '#2563eb' },
  IN_MAINTENANCE: { label: 'In maintenance', color: '#e39b2e' },
  RETIRED: { label: 'Retired', color: '#98a2b3' },
}

// Display order used in charts and legends.
export const BIKE_STATUS_ORDER = ['AVAILABLE', 'IN_USE', 'IN_MAINTENANCE', 'RETIRED']
