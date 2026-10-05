---
name: html-deploy
description: Deploy a ZIP of static HTML/CSS/JS to the html-deploy platform, inspect deployment status, and troubleshoot upload, validation, release, or rollback failures. Do not use for unrelated static hosting or generic application deployment.
---

# HTML Deploy

Use the platform API or the bundled Node script to publish a static site ZIP, wait for validation and release, and report the live URL.

## Quick Deploy

Prefer `scripts/deploy.mjs` for the complete workflow.

```powershell
$env:HTML_DEPLOY_API_URL = "https://html-deploy-api-production.up.railway.app"
$env:HTML_DEPLOY_EMAIL = "<your-email>"
$env:HTML_DEPLOY_PASSWORD = "<your-password>"

node scripts/deploy.mjs `
  --file .\dist.zip `
  --tenant demo-tenant `
  --project demo-site `
  --name "Demo Site" `
  --environment production `
  --provider netlify
```

The script:

1. Logs in.
2. Resolves the tenant and project.
3. Creates the project if it does not exist.
4. Creates an artifact upload target.
5. Uploads the ZIP.
6. Completes the upload and waits for artifact validation.
7. Creates a deployment and waits for an active or failed result.
8. Prints deployment ID, version, status, and live URL as JSON.

Use `--register` only when intentionally creating a new account and tenant:

```powershell
node scripts/deploy.mjs `
  --register `
  --file .\dist.zip `
  --tenant demo-tenant `
  --tenant-name "Demo Tenant" `
  --project demo-site `
  --name "Demo Site"
```

## Inputs

Read credentials from environment variables. Do not print passwords, JWTs, refresh tokens, or presigned upload URLs.

| Setting | Environment variable | Default |
| --- | --- | --- |
| API base URL | `HTML_DEPLOY_API_URL` | `https://html-deploy-api-production.up.railway.app` |
| Email | `HTML_DEPLOY_EMAIL` | none |
| Password | `HTML_DEPLOY_PASSWORD` | none |
| Tenant slug | `--tenant` | first tenant when unambiguous |
| Project slug | `--project` | required |
| Environment | `--environment` | `production` |
| Release provider | `--provider` | platform default (`GET /api/v1/release-providers`) |

## Release Providers

The platform exposes an optional per-deployment release provider:

| Provider | Result | Typical use |
| --- | --- | --- |
| `netlify` | `https://{site}.netlify.app` | shareable public links with CDN |
| `local` | `{SITE_URL_TEMPLATE}` content endpoint | verifying the platform's own publishing path |

Omit `--provider` to use the platform default. Use
`GET /api/v1/release-providers` to read `defaultProvider` and `providers`
before selecting a mode. Provider is stored on the deployment, so rollback
reuses the original provider.

The ZIP is validated by the platform. It must:

- be a valid ZIP archive;
- contain `index.html` at its root;
- stay within configured file, expanded-size, and path limits;
- avoid path traversal, symbolic links, and executable files.

Do not wrap the site in an extra directory unless `index.html` remains at the ZIP root.

## Direct API Use

Read [references/api.md](references/api.md) when:

- using CI/CD rather than the Node script;
- implementing another client;
- inspecting request and response shapes;
- adding domain, rollback, or project-management calls.

## Troubleshooting

Read [references/troubleshooting.md](references/troubleshooting.md) for:

- CORS or `Invalid CORS request`;
- `Invalid JSON` on an error response;
- authentication failures;
- upload URL failures;
- `artifact.*` validation errors;
- `deployment.*` release failures;
- rollback behavior.

Always report the final deployment ID, version, status, and URL. If the deployment failed, report the platform error code rather than guessing a cause.
