import { createServer } from 'node:http'
import { createReadStream, existsSync, statSync } from 'node:fs'
import { extname, join, normalize, resolve } from 'node:path'

const port = Number(process.env.PORT ?? 8081)
const workspaceRoot = process.cwd()

const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.gif': 'image/gif',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.jpeg': 'image/jpeg',
  '.jpg': 'image/jpeg',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain; charset=utf-8',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
}

function responseWithHeaders(response, status, body) {
  response.writeHead(status, {
    'Content-Type': 'text/plain; charset=utf-8',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
  })
  response.end(body)
}

function siteRootFromHost(hostHeader) {
  const host = (hostHeader ?? '').split(':')[0].toLowerCase()
  if (!host.endsWith('.apps.localhost')) {
    return null
  }
  const siteName = host.slice(0, -'.apps.localhost'.length)
  if (!/^[a-z0-9](?:[a-z0-9-]{0,98}[a-z0-9])?$/.test(siteName)) {
    return null
  }
  return resolve(workspaceRoot, 'backend', 'data', 'www', siteName, 'current')
}

createServer((request, response) => {
  const siteRoot = siteRootFromHost(request.headers.host)
  if (!siteRoot) {
    responseWithHeaders(response, 404, 'Unknown content host')
    return
  }

  const requestUrl = new URL(request.url ?? '/', 'http://localhost')
  const relativePath = decodeURIComponent(requestUrl.pathname).replace(/^\/+/, '')
  const normalizedPath = normalize(relativePath)
  if (normalizedPath.startsWith('..') || normalizedPath.includes('\0')) {
    responseWithHeaders(response, 400, 'Invalid path')
    return
  }

  let filePath = join(siteRoot, normalizedPath)
  if (!filePath.startsWith(siteRoot)) {
    responseWithHeaders(response, 400, 'Invalid path')
    return
  }
  if (existsSync(filePath) && statSync(filePath).isDirectory()) {
    filePath = join(filePath, 'index.html')
  }
  if (!existsSync(filePath) && extname(filePath) === '') {
    filePath = join(siteRoot, 'index.html')
  }
  if (!existsSync(filePath) || !statSync(filePath).isFile()) {
    responseWithHeaders(response, 404, 'Not found')
    return
  }

  const extension = extname(filePath)
  response.writeHead(200, {
    'Content-Type': contentTypes[extension] ?? 'application/octet-stream',
    'Cache-Control': extension === '.html'
      ? 'public, max-age=60, must-revalidate'
      : 'public, max-age=3600',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'strict-origin-when-cross-origin',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
  })
  if (request.method === 'HEAD') {
    response.end()
    return
  }
  createReadStream(filePath).pipe(response)
}).listen(port, '127.0.0.1', () => {
  console.log(`Local content server listening on http://127.0.0.1:${port}`)
})
