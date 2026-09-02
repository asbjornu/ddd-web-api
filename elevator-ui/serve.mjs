// Zero-dependency static file server: this is the whole "front end
// build" now that there is no dynamic HTML left to generate (see
// docs/architecture.md's "elevator-ui: front-end only, no BFF"
// section) -- serving `public/` is the entire job, so a real web
// server (Node's own http module) does it directly, no Express, no
// bundler, nothing else installed to do it. Caddy plays the identical
// role in production (see the repo root's own Caddyfile) -- this
// exists only so `npm run dev` and Playwright's own webServer have
// something to point at without needing Docker.

import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { extname, join, normalize } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = fileURLToPath(new URL('./public/', import.meta.url))
const PORT = process.env.PORT ? Number(process.env.PORT) : 3000

const CONTENT_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8'
}

// Mirrors the repo root Caddyfile's own `try_files {path} {path}.html`:
// an extensionless route (e.g. /status) maps to <path>.html, the same
// file-based routing Nuxt's pages/ used to provide.
function candidatePaths(pathname) {
  if (pathname === '/') {
    return ['index.html']
  }
  const relative = normalize(pathname).replace(/^([/\\])+/, '')
  return extname(relative) ? [relative] : [`${relative}.html`]
}

createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://localhost:${PORT}`)

  // Mirrors the repo root Caddyfile's own @entryPoint matcher: "/"
  // is content-negotiated, not just path-routed. A real browser's own
  // top-level navigation sends an Accept header starting with
  // text/html; Datastar's own @get('/') (fired by every page's own
  // #entry-point data-init, including this one's own second fetch
  // after navigating here) sends "text/event-stream, text/html, ..."
  // instead -- text/event-stream first, so it does NOT start with
  // text/html. In production that distinction routes the second kind
  // to elevator-api instead of back to this same static page; there is
  // no elevator-api here, so the honest equivalent is a 404 rather
  // than silently handing Datastar a full page to morph into
  // #entry-point, which is meaningless without a real backend and
  // corrupts whatever page is currently loaded.
  if (url.pathname === '/' && !(req.headers.accept ?? '').startsWith('text/html')) {
    res.writeHead(404)
    res.end('Not found')
    return
  }

  for (const candidate of candidatePaths(url.pathname)) {
    try {
      const filePath = join(ROOT, candidate)
      const body = await readFile(filePath)
      res.writeHead(200, {
        'Content-Type': CONTENT_TYPES[extname(filePath)] ?? 'application/octet-stream'
      })
      res.end(body)
      return
    } catch {
      // Try the next candidate, if any.
    }
  }
  res.writeHead(404)
  res.end('Not found')
}).listen(PORT, () => {
  console.log(`Serving ./public on http://localhost:${PORT}`)
})
