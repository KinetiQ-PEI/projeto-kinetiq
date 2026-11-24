import { useSearchParams } from 'react-router-dom'
import PageHeader from '../components/PageHeader.jsx'

// Mockup: live-map.html · Requirements: RF17, RNF02, RNF06 (list alternative), RNF12
// Route: /live-map?bike=MB-2026-042 centres the map on that bike.
export default function LiveMapPage() {
  const [params] = useSearchParams()
  const bike = params.get('bike')

  return (
    <>
      <PageHeader title="Live map" />
      <section className="card">
        <p>
          Work in progress: converting <code>live-map.html</code> to React.
          {bike && ` Locating bike ${bike}.`}
        </p>
      </section>
    </>
  )
}
