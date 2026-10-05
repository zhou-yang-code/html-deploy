import type {
  ArtifactDetails,
  ArtifactUpload,
  AuthResult,
  DeploymentDetails,
  ProjectSummary,
  ReleaseProviderInfo,
  TenantSummary,
  UserSummary,
} from '@/types'

const tokenKey = 'html-deploy-access-token'
const refreshKey = 'html-deploy-refresh-token'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
  ) {
    super(message)
  }
}

function authToken() {
  return localStorage.getItem(tokenKey)
}

export function setSession(tokens: { accessToken: string; refreshToken: string }) {
  localStorage.setItem(tokenKey, tokens.accessToken)
  localStorage.setItem(refreshKey, tokens.refreshToken)
}

export function clearSession() {
  localStorage.removeItem(tokenKey)
  localStorage.removeItem(refreshKey)
}

export function hasSession() {
  return Boolean(authToken())
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (!(init.body instanceof Blob) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (authToken()) {
    headers.set('Authorization', `Bearer ${authToken()}`)
  }
  const response = await fetch(path, { ...init, headers })
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  let data: { message?: string; code?: string } | null = null
  if (text) {
    try {
      data = JSON.parse(text) as { message?: string; code?: string }
    } catch {
      if (response.ok) {
        throw new ApiError('服务返回了无法解析的响应', response.status, 'invalid_response')
      }
    }
  }
  if (!response.ok) {
    const fallback = text || `请求失败，HTTP ${response.status}`
    throw new ApiError(data?.message ?? fallback, response.status, data?.code ?? 'request_failed')
  }
  return data as T
}

export const api = {
  async register(payload: {
    email: string
    password: string
    tenantName: string
    tenantSlug?: string
  }) {
    const result = await request<AuthResult>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    setSession(result.tokens)
    return result
  },

  async login(payload: { email: string; password: string }) {
    const result = await request<AuthResult>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    setSession(result.tokens)
    return result
  },

  me() {
    return request<UserSummary>('/api/v1/auth/me')
  },

  tenants() {
    return request<TenantSummary[]>('/api/v1/tenants')
  },

  projects(tenantId: string) {
    return request<ProjectSummary[]>(`/api/v1/tenants/${tenantId}/projects`)
  },

  createProject(tenantId: string, payload: { name: string; slug?: string }) {
    return request<ProjectSummary>(`/api/v1/tenants/${tenantId}/projects`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  project(projectId: string) {
    return request<ProjectSummary>(`/api/v1/projects/${projectId}`)
  },

  archiveProject(projectId: string) {
    return request<ProjectSummary>(`/api/v1/projects/${projectId}/archive`, {
      method: 'POST',
    })
  },

  deleteProject(projectId: string) {
    return request<void>(`/api/v1/projects/${projectId}`, {
      method: 'DELETE',
    })
  },

  createUpload(projectId: string, originalFilename: string) {
    return request<ArtifactUpload>(`/api/v1/projects/${projectId}/artifacts`, {
      method: 'POST',
      body: JSON.stringify({ originalFilename }),
    })
  },

  async uploadZip(upload: ArtifactUpload, file: File) {
    const response = await fetch(upload.uploadUrl, {
      method: upload.method,
      headers: {
        'Content-Type': 'application/zip',
      },
      body: file,
    })
    if (!response.ok) {
      throw new Error(`文件上传失败，HTTP ${response.status}`)
    }
  },

  completeUpload(artifactId: string) {
    return request<ArtifactDetails>(`/api/v1/artifacts/${artifactId}/complete`, {
      method: 'POST',
    })
  },

  artifact(artifactId: string) {
    return request<ArtifactDetails>(`/api/v1/artifacts/${artifactId}`)
  },

  deployments(projectId: string) {
    return request<DeploymentDetails[]>(`/api/v1/projects/${projectId}/deployments`)
  },

  releaseProviders() {
    return request<ReleaseProviderInfo>('/api/v1/release-providers')
  },

  createDeployment(
    projectId: string,
    artifactId: string,
    environment = 'production',
    provider?: string,
  ) {
    return request<DeploymentDetails>(`/api/v1/projects/${projectId}/deployments`, {
      method: 'POST',
      body: JSON.stringify(provider ? { artifactId, environment, provider } : { artifactId, environment }),
    })
  },

  deployment(deploymentId: string) {
    return request<DeploymentDetails>(`/api/v1/deployments/${deploymentId}`)
  },

  rollback(deploymentId: string) {
    return request<DeploymentDetails>(`/api/v1/deployments/${deploymentId}/rollback`, {
      method: 'POST',
    })
  },
}
