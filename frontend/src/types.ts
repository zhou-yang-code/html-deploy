export type Role = 'OWNER' | 'MAINTAINER' | 'DEVELOPER' | 'VIEWER'
export type ArtifactStatus = 'CREATED' | 'UPLOADED' | 'VALIDATING' | 'READY' | 'REJECTED'
export type DeploymentStatus = 'CREATED' | 'DEPLOYING' | 'ACTIVE' | 'FAILED' | 'SUPERSEDED'

export interface Tokens {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
}

export interface TenantSummary {
  id: string
  name: string
  slug: string
  role: Role
}

export interface UserSummary {
  id: string
  email: string
  tenants: TenantSummary[]
}

export interface AuthResult {
  tokens: Tokens
  user: UserSummary
}

export interface ProjectSummary {
  id: string
  tenantId: string
  tenantSlug: string
  name: string
  slug: string
  status: 'ACTIVE' | 'ARCHIVED'
  deploymentUrl: string
  createdAt: string
  updatedAt: string
}

export interface ArtifactUpload {
  artifactId: string
  status: ArtifactStatus
  method: string
  uploadUrl: string
  expiresAt: string
}

export interface ArtifactDetails {
  id: string
  projectId: string
  originalFilename: string
  status: ArtifactStatus
  sizeBytes: number
  sha256?: string
  fileCount?: number
  totalBytes?: number
  errorCode?: string
  createdAt: string
  updatedAt: string
}

export interface DeploymentDetails {
  id: string
  projectId: string
  artifactId: string
  environment: string
  provider: string
  version: number
  status: DeploymentStatus
  releasePath?: string
  errorCode?: string
  url: string
  createdAt: string
  updatedAt: string
  finishedAt?: string
}

export interface ReleaseProviderInfo {
  defaultProvider: string
  providers: string[]
}
