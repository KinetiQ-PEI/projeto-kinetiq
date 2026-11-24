import { useCallback, useEffect, useState } from 'react'
import PageHeader from '../components/PageHeader.jsx'
import LiveIndicator from '../components/LiveIndicator.jsx'
import SelectField from '../components/SelectField.jsx'
import DataState from '../components/DataState.jsx'
import KpiGrid from '../components/overview/KpiGrid.jsx'
import FleetStateCard from '../components/overview/FleetStateCard.jsx'
import OpenAlertsCard from '../components/overview/OpenAlertsCard.jsx'
import ZonesTable from '../components/overview/ZonesTable.jsx'
import HourlyUsageChart from '../components/overview/HourlyUsageChart.jsx'
import useAsync from '../hooks/useAsync.js'
import {
  getFleetSummary,
  getHourlyUsage,
  getKpis,
  getShifts,
  getZoneStats,
  getZones,
} from '../services/overviewService.js'
import { acknowledgeAlert, createMaintenanceTicket, getOpenAlerts } from '../services/alertsService.js'
import { currentUser } from '../services/session.js'
import './OverviewPage.css'

// Until the WebSocket Service exists (RF04), the page polls every 30 s.
const REFRESH_MS = 30000
const ALL_ZONES = 'all'

async function loadOverview() {
  const [fleet, kpis, hourlyUsage, openAlerts, zones, zoneStats, shifts] = await Promise.all([
    getFleetSummary(),
    getKpis(),
    getHourlyUsage(),
    getOpenAlerts(),
    getZones(),
    getZoneStats(),
    getShifts(),
  ])
  return { fleet, kpis, hourlyUsage, openAlerts, zones, zoneStats, shifts, loadedAt: Date.now() }
}

// Mockup: index.html · Requirements: RF09, RF10, RF11, RF18, RNF01, RNF02, RNF04, RNF05, RNF08
export default function OverviewPage() {
  const { status, data, reload } = useAsync(loadOverview)
  const [zoneId, setZoneId] = useState(ALL_ZONES)
  const [shift, setShift] = useState('')

  useEffect(() => {
    const id = setInterval(reload, REFRESH_MS)
    return () => clearInterval(id)
  }, [reload])

  const handleAcknowledge = useCallback(
    async (alert) => {
      await acknowledgeAlert(alert.alertId)
      reload()
    },
    [reload],
  )

  const handleCreateTicket = useCallback(
    async (alert) => {
      await createMaintenanceTicket({ alertId: alert.alertId, bikeId: alert.bikeId, reason: alert.details })
      reload()
    },
    [reload],
  )

  const inZone = (item) => zoneId === ALL_ZONES || item.zoneId === zoneId

  return (
    <>
      <PageHeader
        title="Fleet overview"
        subtitle={data && `${data.fleet.name} · ${data.fleet.registeredBikes} bikes registered`}
        actions={
          data && (
            <>
              <LiveIndicator updatedAt={data.loadedAt} />
              <SelectField
                label="Zone"
                value={zoneId}
                onChange={setZoneId}
                minWidth={160}
                options={[
                  { value: ALL_ZONES, label: 'All zones' },
                  ...data.zones.map((z) => ({ value: z.zoneId, label: z.name })),
                ]}
              />
              {/* TODO: the shift filter has no data behind it yet (needs trips per shift from the API). */}
              <SelectField
                label="Shift"
                value={shift || data.shifts[0]}
                onChange={setShift}
                options={data.shifts.map((s) => ({ value: s, label: s }))}
              />
            </>
          )
        }
      />

      <DataState status={status} hasData={Boolean(data)} onRetry={reload}>
        {data && (
          <>
            {/* KPIs and fleet state are fleet-wide: the API has no per-zone breakdown yet. */}
            <KpiGrid fleet={data.fleet} kpis={data.kpis} />

            <div className="overview__row">
              <div className="overview__column">
                <FleetStateCard byStatus={data.fleet.byStatus} />
                <HourlyUsageChart {...data.hourlyUsage} />
              </div>
              <OpenAlertsCard
                alerts={data.openAlerts.filter(inZone)}
                zones={data.zones}
                user={currentUser}
                onAcknowledge={handleAcknowledge}
                onCreateTicket={handleCreateTicket}
              />
            </div>

            <ZonesTable zones={data.zones} zoneStats={data.zoneStats.filter(inZone)} />
          </>
        )}
      </DataState>
    </>
  )
}
