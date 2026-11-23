import { useParams, useSearchParams } from 'react-router-dom'
import PageHeader from '../components/PageHeader.jsx'
import { BIKE_STATUS } from '../data/bikeStatus.js'

// Mockup: bikes.html · Requirements: RF05, RF06, RF07
// Routes: /bikes, /bikes?state=IN_MAINTENANCE, /bikes/:bikeId
export default function BikesPage() {
  const { bikeId } = useParams()
  const [params] = useSearchParams()
  const state = params.get('state')

  return (
    <>
      <PageHeader title={bikeId ? `Bike ${bikeId}` : 'Bikes'} />
      <section className="card">
        <p>
          Work in progress: converting <code>bikes.html</code> to React.
          {state && BIKE_STATUS[state] && ` Filter: ${BIKE_STATUS[state].label}.`}
        </p>
      </section>
    </>
  )
}
