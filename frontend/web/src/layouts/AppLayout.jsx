import { NavLink, Outlet } from 'react-router-dom'
import { NAV_ITEMS } from '../data/navigation.js'
import logo from '../assets/logo.png'
import './AppLayout.css'

// Shared shell: top bar + navigation + page content (<Outlet />).
export default function AppLayout() {
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>

      <header className="app-header">
        <NavLink to="/" aria-label="KinetiQ home" className="app-header__logo">
          <img src={logo} alt="KinetiQ" />
        </NavLink>

        <div className="app-header__right">
          <nav aria-label="Main" className="app-nav">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  isActive ? 'app-nav__link is-active' : 'app-nav__link'
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          {/* TODO: fleet selector and user come from Keycloak / API later (mock for now). */}
          <div className="app-header__user">
            <span className="app-header__fleet">Campus Aveiro fleet</span>
            <div className="app-header__avatar" title="Rita Almeida · Fleet Operator">
              RA
            </div>
          </div>
        </div>
      </header>

      <main id="main" tabIndex={-1} className="app-main">
        <Outlet />
      </main>
    </>
  )
}
