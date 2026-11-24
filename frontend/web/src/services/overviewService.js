// Data for the Overview page (RF09, RF18).
// Pages import from here, never from data/mock directly: when the backend exists,
// only this file changes.
//
// TODO (to confirm with the backend team): the endpoint paths below are a proposal.

import { USE_MOCKS } from './config.js'
import { fromMock, getJson } from './http.js'
import * as mock from '../data/mock/overview.js'

/** Fleet name, registered bikes and counts per state (RF06). */
export function getFleetSummary() {
  return USE_MOCKS ? fromMock(mock.fleet) : getJson('/fleet/summary')
}

/** KPIs not derivable from the state counts: trips, trip duration, CO₂ (RF09, RF13). */
export function getKpis() {
  return USE_MOCKS ? fromMock(mock.kpis) : getJson('/fleet/kpis')
}

/** Bikes in use per hour for one day. */
export function getHourlyUsage() {
  return USE_MOCKS ? fromMock(mock.hourlyUsage) : getJson('/fleet/usage/hourly')
}

/** Geographic zones (RF26). */
export function getZones() {
  return USE_MOCKS ? fromMock(mock.zones) : getJson('/zones')
}

/** Bikes per zone and state. */
export function getZoneStats() {
  return USE_MOCKS ? fromMock(mock.zoneStats) : getJson('/zones/stats')
}

/** Shift filter options. */
export function getShifts() {
  return USE_MOCKS ? fromMock(mock.shifts) : getJson('/fleet/shifts')
}
