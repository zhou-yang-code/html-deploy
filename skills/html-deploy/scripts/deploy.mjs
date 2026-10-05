#!/usr/bin/env node

import { readFile, stat } from 'node:fs/promises'
import { basename, resolve } from 'node:path'

const args = parseArgs(process.argv.slice(2))
if (args.help) {
  printHelp()
  process.exit(0)
}
const apiBase = (args.api ?? process.env.HTML_DEPLOY_API_URL ?? 'https://html-deploy-api-production.up.railway.app').replace(/\/+$/, '')
const email = args.email ?? process.env.HTML_DEPLOY_EMAIL
const password = args.password ?? process.env.HTML_DEPLOY_PASSWORD
const filePath = args.file ? resolve(args.file) : null
const environment = args.environment ?? 'production'
const tenantSlug = args.tenant
const projectSlug = args.project
const projectName = args.name ?? projectSlug
let accessToken = ''

if (!filePath) {
  fail('Missing --file <path-to-zip>.')
}
if (!projectSlug) {
  fail('Missing --project <slug>.')
}
if (!email || !password) {
  fail('Set HTML_DEPLOY_EMAIL and HTML_DEPLOY_PASSWORD, or pass --email and --password.')
}

async function main() {
  const fileStat = await stat(filePath)
  if (!fileStat.isFile()) {
    fail(`Not a file: ${filePath}`)
  }
  if (!filePath.toLowerCase().endsWith('.zip')) {
    fail('The deployment artifact must be a .zip file.')
  }

  const auth = args.register
    ? await api('/api/v1/auth/register', {
        method: 'POST',
        body: {
          email,
          password,
          tenantName: args['tenant-name'] ?? args.tenantName ?? tenantSlug,
          tenantSlug,
        },
        auth: false,
      })
    : await api('/api/v1/auth/login', {
        method: 'POST',
        body: { email, password },
        auth: false,
      })

  accessToken = auth.tokens.accessToken

  const tenants = await api('/api/v1/tenants')
  const tenant = tenantSlug
    ? tenants.find((item) => item.slug === tenantSlug)
    : tenants.length === 1
      ? tenants[0]
      : null
  if (!tenant) {
    fail(tenantSlug
      ? `Tenant "${tenantSlug}" was not found for this account.`
      : 'Multiple tenants are available; pass --tenant <slug>.')
  }

  let project = (await api(`/api/v1/tenants/${tenant.id}/projects`))
    .find((item) => item.slug === projectSlug)
  if (!project) {
    project = await api(`/api/v1/tenants/${tenant.id}/projects`, {
      method: 'POST',
      body: { name: projectName, slug: projectSlug },
    })
  }

  const upload = await api(`/api/v1/projects/${project.id}/artifacts`, {
    method: 'POST',
    body: { originalFilename: basename(filePath) },
  })

  const uploadUrl = new URL(upload.uploadUrl, apiBase).toString()
  const bytes = await readFile(filePath)
  const uploadResponse = await fetch(uploadUrl, {
    method: upload.method,
    headers: { 'Content-Type': 'application/zip' },
    body: bytes,
  })
  if (!uploadResponse.ok) {
    fail(`ZIP upload failed with HTTP ${uploadResponse.status}.`)
  }

  await api(`/api/v1/artifacts/${upload.artifactId}/complete`, { method: 'POST' })
  const artifact = await poll(
    () => api(`/api/v1/artifacts/${upload.artifactId}`),
    (value) => value.status === 'READY' || value.status === 'REJECTED',
    90,
    1000,
  )
  if (artifact.status !== 'READY') {
    fail(`Artifact validation failed: ${artifact.errorCode ?? artifact.status}`)
  }

  const created = await api(`/api/v1/projects/${project.id}/deployments`, {
    method: 'POST',
    body: { artifactId: artifact.id, environment },
  })
  const deployment = await poll(
    () => api(`/api/v1/deployments/${created.id}`),
    (value) => ['ACTIVE', 'FAILED'].includes(value.status),
    180,
    1000,
  )

  let urlStatus = null
  if (deployment.status === 'ACTIVE' && deployment.url) {
    try {
      const response = await fetch(deployment.url, { method: 'HEAD' })
      urlStatus = response.status
    } catch {
      urlStatus = null
    }
  }

  const result = {
    deploymentId: deployment.id,
    version: deployment.version,
    status: deployment.status,
    url: deployment.url,
    projectId: project.id,
    tenantId: tenant.id,
    artifactId: artifact.id,
    errorCode: deployment.errorCode ?? null,
    urlStatus,
  }
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
  if (deployment.status !== 'ACTIVE') {
    process.exitCode = 1
  }
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers)
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  if (options.auth !== false && accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  const response = await fetch(`${apiBase}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      if (response.ok) {
        throw new Error(`Expected JSON but received: ${text.slice(0, 200)}`)
      }
    }
  }
  if (!response.ok) {
    const message = data?.message ?? text ?? `HTTP ${response.status}`
    const code = data?.code ? ` (${data.code})` : ''
    throw new Error(`${message}${code}`)
  }
  return data
}

async function poll(load, done, attempts, delayMs) {
  let last
  for (let index = 0; index < attempts; index += 1) {
    last = await load()
    if (done(last)) {
      return last
    }
    await new Promise((resolveDelay) => setTimeout(resolveDelay, delayMs))
  }
  throw new Error(`Timed out after ${attempts} attempts. Last status: ${last?.status ?? 'unknown'}`)
}

function parseArgs(values) {
  const parsed = {}
  for (let index = 0; index < values.length; index += 1) {
    const value = values[index]
    if (value === '--help' || value === '-h') {
      parsed.help = true
      continue
    }
    if (!value.startsWith('--')) {
      continue
    }
    const key = value.slice(2)
    if (key === 'register') {
      parsed[key] = true
      continue
    }
    const next = values[index + 1]
    if (next === undefined || next.startsWith('--')) {
      fail(`Missing value for --${key}.`)
    }
    parsed[key] = next
    index += 1
  }
  return parsed
}

function printHelp() {
  process.stdout.write(`HTML Deploy

Usage:
  node scripts/deploy.mjs --file <dist.zip> --project <slug> [options]

Required:
  --file <path>          ZIP file containing index.html at its root
  --project <slug>       Project slug
  HTML_DEPLOY_EMAIL      Platform login email
  HTML_DEPLOY_PASSWORD   Platform login password

Optional:
  --api <url>            API base URL
  --tenant <slug>        Tenant slug; defaults to the only available tenant
  --name <name>          Project name when creating a project
  --environment <name>   Deployment environment (default: production)
  --register             Register the email and tenant before deploying
  --tenant-name <name>   Tenant name when using --register
  --help, -h             Show this help

Environment:
  HTML_DEPLOY_API_URL
  HTML_DEPLOY_EMAIL
  HTML_DEPLOY_PASSWORD
`)
}

function fail(message) {
  process.stderr.write(`${message}\n`)
  process.exit(1)
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`)
  process.exit(1)
})
