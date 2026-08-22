// Technician key-switch session.
//
// The BFF holds no credential of its own. The technician types the
// key-switch secret, this module exchanges it at elevator-auth's token
// endpoint for a scoped access token, and the token itself goes into an
// HttpOnly cookie. Privileged routes read it back out and forward it to
// elevator-api as a Bearer token.
//
// The cookie is therefore self-contained: there is no session store, and
// nothing to invalidate when the key is withdrawn beyond clearing it. That
// trade is deliberate -- a token stays valid until it expires, which is why
// the lifetime is short -- and it keeps the cookie on the right side of the
// line RFC-conscious reviewers draw between a session key and a credential.
//
// Note what is hard-coded here and should not be: the token endpoint's
// location. A client that discovered it from the resource server's own
// challenge would not need this constant at all. That is the point of the
// refactoring, not an oversight in it.

import type { H3Event } from 'h3'

export const TECHNICIAN_COOKIE = 'technician_token'

// Scoped to the BFF's own routes. Every route that issues or reads this
// cookie lives under /api, so it is never attached to anything else.
const COOKIE_PATH = '/api'

interface TokenResponse {
  access_token: string
  token_type: string
  expires_in: number
  scope: string
}

/**
 * Exchanges the typed key-switch secret for a scoped access token.
 * Returns the token, or null if the credential was refused.
 */
export async function exchangeKeyForToken(secret: string): Promise<TokenResponse | null> {
  const config = useRuntimeConfig()
  const credentials = Buffer.from(`${config.oauthClientId}:${secret}`).toString('base64')

  try {
    return await $fetch<TokenResponse>(`${config.oauthIssuer}/oauth2/token`, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${credentials}`,
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: new URLSearchParams({
        grant_type: 'client_credentials',
        scope: 'elevator:maintenance elevator:recall'
      }).toString()
    })
  } catch {
    // A refused credential and an unreachable authorization server are
    // indistinguishable from here, and both mean the same thing to the
    // technician: the key did not turn.
    return null
  }
}

export function storeToken(event: H3Event, token: TokenResponse): void {
  setCookie(event, TECHNICIAN_COOKIE, token.access_token, {
    httpOnly: true,
    sameSite: 'strict',
    path: COOKIE_PATH,
    secure: process.env.NODE_ENV === 'production',
    // Expire the cookie with the token, so the two cannot disagree.
    maxAge: token.expires_in
  })
}

export function clearToken(event: H3Event): void {
  deleteCookie(event, TECHNICIAN_COOKIE, { path: COOKIE_PATH })
}

export function readToken(event: H3Event): string | undefined {
  return getCookie(event, TECHNICIAN_COOKIE)
}

export function requireToken(event: H3Event): string {
  const token = readToken(event)
  if (!token) {
    throw createError({
      statusCode: 401,
      statusMessage: 'Technician key not inserted'
    })
  }
  return token
}
