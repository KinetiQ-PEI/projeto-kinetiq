import StatCard from '../StatCard.jsx'
import { percent } from '../../utils/format.js'
import './KpiGrid.css'

// The three live KPI tiles at the top of the Overview (RF09).
// Availability and utilization are computed over the active fleet (everything except RETIRED).
// CO₂ is a monthly figure and lives on the Reports page (RF13, RF18).
export default function KpiGrid({ fleet, kpis }) {
  const { AVAILABLE, IN_USE, IN_MAINTENANCE } = fleet.byStatus
  const activeFleet = AVAILABLE + IN_USE + IN_MAINTENANCE
  const delta = kpis.activeTripsDeltaVsYesterday
  const up = delta >= 0

  return (
    <section aria-label="Key indicators" className="kpi-grid">
      <StatCard
        label="Availability"
        value={`${percent(AVAILABLE, activeFleet)}%`}
        hint={`${AVAILABLE} of ${activeFleet} active bikes`}
      />
      <StatCard
        label="Active trips"
        value={kpis.activeTrips}
        hint={
          <>
            {percent(IN_USE, activeFleet)}% utilization ·{' '}
            <span className={up ? 'stat-card__delta--up' : 'stat-card__delta--down'}>
              <span aria-hidden="true">{up ? '▲' : '▼'}</span>
              <span className="sr-only">{up ? 'Up' : 'Down'}</span> {Math.abs(delta)} vs. yesterday
            </span>
          </>
        }
      />
      <StatCard label="Avg. trip duration" value={`${kpis.avgTripMinutes} min`} hint="Last 7 days" />
    </section>
  )
}
