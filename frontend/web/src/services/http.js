import { API_BASE_URL, MOCK_LATENCY_MS } from './config.js'

async function request(method, path, body) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: { Accept: 'application/json', ...(body && { 'Content-Type': 'application/json' }) },
    body: body && JSON.stringify(body),
  })
  if (!response.ok) {
    throw new Error(`${method} ${path} failed with ${response.status}`)
  }
  return response.status === 204 ? null : response.json()
}

/** GET a JSON resource from the API Gateway. Throws on non-2xx responses. */
export function getJson(path) {
  return request('GET', path)
}

/** POST JSON to the API Gateway. Throws on non-2xx responses. */
export function postJson(path, body) {
  return request('POST', path, body)
}

/** Resolve a mock value after a short delay, as a copy (callers may mutate it). */
export async function fromMock(value) {
  await new Promise((resolve) => setTimeout(resolve, MOCK_LATENCY_MS))
  return structuredClone(value)
}
