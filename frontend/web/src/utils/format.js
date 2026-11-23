/** Whole-number percentage of part in total (0 when total is 0). */
export function percent(part, total) {
  return total > 0 ? Math.round((part / total) * 100) : 0
}

/** "just now", "6 min ago", "2 h ago", "3 d ago". */
export function timeAgo(timestamp, now = Date.now()) {
  const minutes = Math.floor((now - timestamp) / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  return `${Math.floor(hours / 24)} d ago`
}

/** 9 -> "09" */
export function twoDigits(n) {
  return String(n).padStart(2, '0')
}

/** Compact form for tight rows: "now", "2m ago", "3h ago", "2d ago". */
export function timeAgoShort(timestamp, now = Date.now()) {
  const minutes = Math.floor((now - timestamp) / 60000)
  if (minutes < 1) return 'now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return `${Math.floor(hours / 24)}d ago`
}

/** +4 -> "+4", -2 -> "−2" (real minus sign), 0 -> "0". */
export function signed(n) {
  if (n > 0) return `+${n}`
  if (n < 0) return `−${Math.abs(n)}`
  return '0'
}
