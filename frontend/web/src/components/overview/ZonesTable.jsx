import { Link } from 'react-router-dom'
import Card from '../Card.jsx'
import { percent } from '../../utils/format.js'
import './ZonesTable.css'

// Bikes per zone and state (RF26). Availability = available ÷ (available + in use + in maintenance).
export default function ZonesTable({ zones, zoneStats }) {
  const zoneName = Object.fromEntries(zones.map((z) => [z.zoneId, z.name]))

  return (
    <Card
      title="Zones"
      action={
        <Link to="/live-map" className="card__link">
          Open live map
        </Link>
      }
    >
      <div className="zones-table__scroll">
        <table className="zones-table">
          <caption className="sr-only">Bikes per zone and state</caption>
          <thead>
            <tr>
              <th scope="col">Zone</th>
              <th scope="col" className="is-number">
                Available
              </th>
              <th scope="col" className="is-number">
                In use
              </th>
              <th scope="col" className="is-number">
                In maintenance
              </th>
              <th scope="col" className="zones-table__availability">
                Availability
              </th>
            </tr>
          </thead>
          <tbody>
            {zoneStats.map((row) => {
              const availability = percent(row.AVAILABLE, row.AVAILABLE + row.IN_USE + row.IN_MAINTENANCE)
              return (
                <tr key={row.zoneId}>
                  <th scope="row">{zoneName[row.zoneId] ?? row.zoneId}</th>
                  <td className="is-number">{row.AVAILABLE}</td>
                  <td className="is-number">{row.IN_USE}</td>
                  <td className="is-number">{row.IN_MAINTENANCE}</td>
                  <td>
                    <div className="zones-table__meter">
                      <div className="zones-table__track" aria-hidden="true">
                        <div className="zones-table__fill" style={{ width: `${availability}%` }} />
                      </div>
                      <span className="zones-table__value">{availability}%</span>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      {zoneStats.length === 0 && <p className="zones-table__empty">No zones match the filter.</p>}
    </Card>
  )
}
