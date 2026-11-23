// What each role can do in the UI (RF24, RNF08).
// TODO (to confirm): align with the Keycloak roles and the Fleet Management state machine.
const ROLE_PERMISSIONS = {
  FLEET_OPERATOR: ['alerts:acknowledge', 'tickets:create'],
  MAINTENANCE_TECHNICIAN: ['alerts:acknowledge', 'tickets:create'],
  FLEET_ANALYST: [],
  RIDER: [],
}

export function can(user, permission) {
  return (ROLE_PERMISSIONS[user?.role] ?? []).includes(permission)
}
