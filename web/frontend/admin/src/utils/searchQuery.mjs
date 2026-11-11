export function searchKeyword(value) {
  return typeof value === 'string' ? value.trim().slice(0, 80) : ''
}

export function searchRecordId(value) {
  if (typeof value !== 'string' || !/^#?\d{1,19}$/.test(value)) return undefined
  const id = BigInt(value.replace(/^#/, ''))
  return id > 0n && id <= 9223372036854775807n ? String(id) : undefined
}
