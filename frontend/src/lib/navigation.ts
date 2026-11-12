export function safeReturnTo(value: unknown, fallback = '/'): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u001f]/.test(value)) {
    return fallback
  }
  try {
    const target = new URL(value, window.location.origin)
    if (target.origin !== window.location.origin || ['/login', '/register'].includes(target.pathname)) return fallback
    return `${target.pathname}${target.search}${target.hash}`
  } catch {
    return fallback
  }
}
