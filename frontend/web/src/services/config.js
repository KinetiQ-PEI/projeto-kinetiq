// Where the data comes from. Set in frontend/web/.env (see .env.example).
// VITE_USE_MOCKS=false switches every service to the real API Gateway (RNF10).
export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS !== 'false'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

// Fake network delay for mocks, so loading states are visible during development.
export const MOCK_LATENCY_MS = 300
