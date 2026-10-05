# HTML Deploy API

Base URL defaults to `https://html-deploy-api-production.up.railway.app`.

Use `Authorization: Bearer <access-token>` for authenticated endpoints.

## Authentication

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
GET  /api/v1/auth/me
```

Login:

```json
{
  "email": "<your-email>",
  "password": "<your-password>"
}
```

The response contains `tokens.accessToken`, `tokens.refreshToken`, and `user.tenants`.

## Tenants and Projects

```http
GET  /api/v1/tenants
GET  /api/v1/tenants/{tenantId}/members
POST /api/v1/tenants/{tenantId}/members
GET  /api/v1/tenants/{tenantId}/projects
POST /api/v1/tenants/{tenantId}/projects
GET  /api/v1/projects/{projectId}
POST /api/v1/projects/{projectId}/archive
POST /api/v1/projects/{projectId}/restore
DELETE /api/v1/projects/{projectId}
```

`GET /api/v1/tenants/{tenantId}/projects` returns active projects by default.
Pass `?includeArchived=true` to include archived ones. Archive, restore, and
delete require the `OWNER` role; delete removes platform data and any matching
Netlify Site.

Create project:

```json
{
  "name": "Demo Site",
  "slug": "demo-site"
}
```

## Upload

Create an upload target:

```http
POST /api/v1/projects/{projectId}/artifacts
```

```json
{
  "originalFilename": "dist.zip"
}
```

Response:

```json
{
  "artifactId": "uuid",
  "status": "CREATED",
  "method": "PUT",
  "uploadUrl": "https://html-deploy-api-production.up.railway.app/api/v1/artifacts/{artifactId}/content?token=...",
  "expiresAt": "2026-10-04T12:00:00Z"
}
```

Upload the ZIP bytes using the returned method and URL. Set `Content-Type: application/zip`.

Complete the upload:

```http
POST /api/v1/artifacts/{artifactId}/complete
```

Inspect validation:

```http
GET /api/v1/artifacts/{artifactId}
```

Artifact states:

```text
CREATED -> UPLOADED -> VALIDATING -> READY
                                  -> REJECTED
```

Do not create a deployment until the artifact is `READY`.

On failure the artifact response carries `errorCode` plus a human-readable
`errorMessage` (for example the paths found when `index.html` is missing).
`index.html` must be at the ZIP root; when every entry sits inside one
top-level folder, the platform drops that wrapper automatically.

## Deployment

Create:

```http
POST /api/v1/projects/{projectId}/deployments
```

```json
{
  "artifactId": "uuid",
  "environment": "production",
  "provider": "local"
}
```

`provider` is optional. Omit it to use the platform default (self-hosted `local`
on this deployment). Supported values:

```text
local    -> {SITE_URL_TEMPLATE} content endpoint
netlify  -> https://{site}.netlify.app
```

Read the current default and supported list:

```http
GET /api/v1/release-providers
```

```json
{
  "defaultProvider": "local",
  "providers": ["netlify", "local"]
}
```

The resolved provider is stored on the deployment and returned as `provider`.
An unsupported value fails the request with `deployment.provider_unsupported`.

Inspect:

```http
GET /api/v1/projects/{projectId}/deployments
GET /api/v1/deployments/{deploymentId}
```

Deployment states:

```text
CREATED -> DEPLOYING -> ACTIVE
                     -> FAILED
ACTIVE -> SUPERSEDED
```

Poll until `ACTIVE` or `FAILED`.

## Rollback

```http
POST /api/v1/deployments/{targetDeploymentId}/rollback
```

Rollback creates a new deployment based on the target deployment artifact and reuses the
target deployment's provider. It does not rewrite history.

## Upload URL Behavior

- Local profile: upload URL points to the Spring Boot upload endpoint and uses a signed token.
- S3/MinIO profile: upload URL is an S3-compatible presigned URL.
- Treat upload URLs as secrets and do not log them in CI.
- If an upload URL expires, create a new artifact upload target.

## Error Shape

```json
{
  "timestamp": "2026-10-04T12:00:00Z",
  "status": 409,
  "code": "artifact.not_ready",
  "message": "artifact is not validated",
  "details": {}
}
```

Always branch on `code` in automation.
