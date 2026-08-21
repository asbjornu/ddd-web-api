// Technician key-switch session, held in an HttpOnly cookie.
//
// The shared secret itself stays server-side (runtimeConfig.technicianKey,
// not runtimeConfig.public). The browser proves it knows the secret once,
// via POST /api/key, and gets back an opaque cookie. Privileged BFF routes
// check that cookie and attach the real Bearer token to elevator-api
// themselves -- the standard BFF token-handler split: cookie between
// browser and BFF, bearer token between BFF and service.
//
// The cookie value is an HMAC of a fixed label under the secret, so it
// cannot be forged without knowing the secret, and it carries no useful
// information if leaked. It is deliberately not a real session store --
// this represents physical key-switch access, not a user account.

import { createHmac, timingSafeEqual } from 'node:crypto'
import type { H3Event } from 'h3'

export const TECHNICIAN_COOKIE = 'technician_key'

const COOKIE_LABEL = 'technician-key-inserted'

function sign(secret: string): string {
  return createHmac('sha256', secret).update(COOKIE_LABEL).digest('hex')
}

export function issueTechnicianCookie(event: H3Event, secret: string): void {
  setCookie(event, TECHNICIAN_COOKIE, sign(secret), {
    httpOnly: true,
    sameSite: 'strict',
    path: '/',
    secure: process.env.NODE_ENV === 'production',
    maxAge: 60 * 60 * 8
  })
}

export function clearTechnicianCookie(event: H3Event): void {
  deleteCookie(event, TECHNICIAN_COOKIE, { path: '/' })
}

export function hasTechnicianKey(event: H3Event, secret: string): boolean {
  const presented = getCookie(event, TECHNICIAN_COOKIE)
  if (!presented) return false

  const expected = sign(secret)
  const a = Buffer.from(presented, 'utf8')
  const b = Buffer.from(expected, 'utf8')

  return a.length === b.length && timingSafeEqual(a, b)
}

export function requireTechnicianKey(event: H3Event, secret: string): void {
  if (!hasTechnicianKey(event, secret)) {
    throw createError({
      statusCode: 401,
      statusMessage: 'Technician key not inserted'
    })
  }
}
