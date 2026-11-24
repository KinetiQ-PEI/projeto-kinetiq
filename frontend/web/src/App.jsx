import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout.jsx'
import OverviewPage from './pages/OverviewPage.jsx'
import LiveMapPage from './pages/LiveMapPage.jsx'
import BikesPage from './pages/BikesPage.jsx'
import MaintenancePage from './pages/MaintenancePage.jsx'
import ReportsPage from './pages/ReportsPage.jsx'
import UsersModulesPage from './pages/UsersModulesPage.jsx'

// One route per mockup (kinetiq-dashboard-mockups-a11y/*.html).
export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<OverviewPage />} />
        <Route path="live-map" element={<LiveMapPage />} />
        <Route path="bikes" element={<BikesPage />} />
        <Route path="bikes/:bikeId" element={<BikesPage />} />
        <Route path="maintenance" element={<MaintenancePage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="users-modules" element={<UsersModulesPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
