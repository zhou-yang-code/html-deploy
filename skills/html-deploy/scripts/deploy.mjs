#!/usr/bin/env node

import { randomBytes } from 'node:crypto'
import { readFile, stat } from 'node:fs/promises'
import { basename, resolve } from 'node:path'
import { createInterface } from 'node:readline/promises'

const args = parseArgs(process.argv.slice(2))
if (args.help) {
  printHelp()
  process.exit(0)
}
const apiBase = (args.api ?? process.env.HTML_DEPLOY_API_URL ?? 'https://html-deploy-api-production.up.railway.app').replace(/\/+$/, '')
let email = args.email ?? process.env.HTML_DEPLOY_EMAIL
let password = args.password ?? process.env.HTML_DEPLOY_PASSWORD
const filePath = args.file ? resolve(args.file) : null
const environment = args.environment ?? 'production'
const providerAliases = { 'self-hosted': 'local', selfhosted: 'local' }
// Self-hosted is the default; pass --provider netlify for public Netlify links.
const requestedProvider = args.provider ? (providerAliases[args.provider] ?? args.provider) : 'local'
let tenantSlug = args.tenant
let tenantName = args['tenant-name'] ?? args.tenantName ?? null
const projectSlug = args.project
const projectName = args.name ?? projectSlug
let authMode = normalizeAuthMode(args.auth ?? (args.register ? 'register' : null))
let createdAccount = null
let accessToken = ''

if (!filePath) {
  fail('Missing --file <path-to-zip>.')
}
if (!projectSlug) {
  fail('Missing --project <slug>.')
}

async function main() {
  const fileStat = await stat(filePath)
  if (!fileStat.isFile()) {
    fail(`Not a file: ${filePath}`)
  }
  if (!filePath.toLowerCase().endsWith('.zip')) {
    fail('The deployment artifact must be a .zip file.')
  }

  authMode = await resolveAuthMode(authMode)
  if (authMode === 'random') {
    createRandomAccount()
  }
  if (authMode === 'register' && (!email || !password)) {
    await promptCredentials()
  }
  if (!email || !password) {
    fail('Set HTML_DEPLOY_EMAIL and HTML_DEPLOY_PASSWORD, or pass --email and --password.')
  }
  if (authMode === 'register') {
    tenantSlug = tenantSlug ?? slugFromEmail(email)
    tenantName = tenantName ?? tenantSlug
  }

  const auth = authMode === 'login'
    ? await api('/api/v1/auth/login', {
        method: 'POST',
        body: { email, password },
        auth: false,
      })
    : await api('/api/v1/auth/register', {
        method: 'POST',
        body: { email, password, tenantName, tenantSlug },
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
    const detail = artifact.errorMessage ? ` - ${artifact.errorMessage}` : ''
    fail(`Artifact validation failed: ${artifact.errorCode ?? artifact.status}${detail}`)
  }

  const releaseProviders = await api('/api/v1/release-providers').catch(() => null)
  if (requestedProvider && releaseProviders?.providers && !releaseProviders.providers.includes(requestedProvider)) {
    fail(`Unsupported --provider "${requestedProvider}". Supported providers: ${releaseProviders.providers.join(', ')}.`)
  }
  const provider = requestedProvider ?? releaseProviders?.defaultProvider ?? null

  const created = await api(`/api/v1/projects/${project.id}/deployments`, {
    method: 'POST',
    body: { artifactId: artifact.id, environment, ...(provider ? { provider } : {}) },
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
    provider: deployment.provider ?? provider,
    url: deployment.url,
    projectId: project.id,
    tenantId: tenant.id,
    artifactId: artifact.id,
    errorCode: deployment.errorCode ?? null,
    urlStatus,
    authMode,
    ...(createdAccount ? { createdAccount } : {}),
  }
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
  if (deployment.status !== 'ACTIVE') {
    process.exitCode = 1
  }
}

function normalizeAuthMode(value) {
  if (value === undefined || value === null || value === '') {
    return null
  }
  const mode = String(value).toLowerCase()
  if (mode === 'login' || mode === 'register' || mode === 'random') {
    return mode
  }
  fail(`Unsupported --auth "${value}". Use login, register, or random.`)
}

async function resolveAuthMode(current) {
  if (current) {
    return current
  }
  if (email && password) {
    return 'login'
  }
  if (process.stdin.isTTY && process.stdout.isTTY) {
    return promptAuthMode()
  }
  fail('Choose an account mode: --auth login | register | random.')
}

async function promptAuthMode() {
  const rl = createInterface({ input: process.stdin, output: process.stdout })
  try {
    process.stdout.write(
      '\n选择账号方式：\n  1) 登录已有账号\n  2) 用我自己的邮箱注册\n  3) 随机创建一个账号\n'
    )
    const answer = (await rl.question('请输入 1 / 2 / 3（默认 1）：')).trim()
    if (answer === '2') {
      return 'register'
    }
    if (answer === '3') {
      return 'random'
    }
    return 'login'
  } finally {
    rl.close()
  }
}

async function promptCredentials() {
  const rl = createInterface({ input: process.stdin, output: process.stdout })
  try {
    if (!email) {
      email = (await rl.question('邮箱：')).trim()
    }
    if (!password) {
      password = (await rl.question('密码：')).trim()
    }
  } finally {
    rl.close()
  }
}

function createRandomAccount() {
  const suffix = randomBytes(4).toString('hex')
  email = email ?? `html-deploy-${suffix}@example.com`
  password = password ?? randomBytes(9).toString('base64url')
  tenantSlug = tenantSlug ?? `user-${suffix}`
  tenantName = tenantName ?? `我的工作区 ${suffix}`
  createdAccount = { email, password }
}

function slugFromEmail(value) {
  const local = String(value).split('@')[0].toLowerCase()
  const slug = local
    .replace(/[^a-z0-9-]/g, '-')
    .replace(/-{2,}/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 50)
  return slug || `user-${randomBytes(3).toString('hex')}`
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

Optional:
  --api <url>            API base URL
  --auth <mode>          login | register | random
                         login    : use HTML_DEPLOY_EMAIL / HTML_DEPLOY_PASSWORD
                         register : create an account with your own email
                         random   : create an account with generated credentials
                         Omit it to keep existing credentials, or to get asked
                         when the script runs in an interactive terminal.
  --email <value>        Platform account email
  --password <value>     Platform account password
  --tenant <slug>        Tenant slug; defaults to the only available tenant
  --name <name>          Project name when creating a project
  --environment <name>   Deployment environment (default: production)
  --provider <name>      Release provider: local (default, self-hosted) or netlify
  --register             Shorthand for --auth register
  --tenant-name <name>   Tenant name when registering
  --help, -h             Show this help

Environment:
  HTML_DEPLOY_API_URL
  HTML_DEPLOY_EMAIL
  HTML_DEPLOY_PASSWORD

The JSON result contains authMode, and createdAccount with the generated
email and password when --auth random is used.
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
