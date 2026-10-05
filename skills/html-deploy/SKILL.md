---
name: html-deploy
description: Deploy a ZIP of static HTML/CSS/JS to the html-deploy platform, inspect deployment status, and troubleshoot upload, validation, release, or rollback failures. Do not use for unrelated static hosting or generic application deployment.
---

# HTML Deploy

Use the platform API or the bundled Node script to publish a static site ZIP, wait for validation and release, and report the live URL.

## Quick Deploy

Prefer `scripts/deploy.mjs` for the complete workflow.

## Account Mode

Ask the user how to authenticate before every first deploy, and never pick an
account mode on their behalf:

1. 登录已有账号 — `--auth login` (needs `HTML_DEPLOY_EMAIL` / `HTML_DEPLOY_PASSWORD`)
2. 用自己的邮箱注册 — `--auth register` (needs the email and password the user wants)
3. 随机创建一个账号 — `--auth random` (the script generates email, password, and tenant)

When the script runs in an interactive terminal and no mode or credentials are
given, it asks the same question itself. In non-interactive runs `--auth` is
required, otherwise the script exits with a message instead of guessing.

```powershell
$env:HTML_DEPLOY_API_URL = "https://html-deploy-api-production.up.railway.app"

# 登录已有账号
$env:HTML_DEPLOY_EMAIL = "<your-email>"
$env:HTML_DEPLOY_PASSWORD = "<your-password>"
node scripts/deploy.mjs `
  --file .\dist.zip `
  --tenant demo-tenant `
  --project demo-site `
  --name "Demo Site" `
  --auth login

# 用自己的邮箱注册，或随机创建账号
node scripts/deploy.mjs --file .\dist.zip --project demo-site --name "Demo Site" --auth register
node scripts/deploy.mjs --file .\dist.zip --project demo-site --name "Demo Site" --auth random
```

The script:

1. Resolves the account mode (login, register, or random).
2. Resolves the tenant and project.
3. Creates the project if it does not exist.
4. Creates an artifact upload target.
5. Uploads the ZIP.
6. Completes the upload and waits for artifact validation.
7. Creates a deployment and waits for an active or failed result.
8. Prints deployment ID, version, status, and live URL as JSON.

`--auth random` returns the generated credentials so the user can log in later:

```json
{
  "authMode": "random",
  "createdAccount": {
    "email": "html-deploy-1a2b3c4d@example.com",
    "password": "<generated>"
  }
}
```

Report those generated credentials to the user; this is the one case where a
password may be shown. `--register` is a shorthand for `--auth register`.

## Inputs

Read credentials from environment variables. Do not print passwords, JWTs, refresh tokens, or presigned upload URLs, except the generated credentials returned by `--auth random`.

| Setting | Environment variable | Default |
| --- | --- | --- |
| API base URL | `HTML_DEPLOY_API_URL` | `https://html-deploy-api-production.up.railway.app` |
| Email | `HTML_DEPLOY_EMAIL` | none |
| Password | `HTML_DEPLOY_PASSWORD` | none |
| Tenant slug | `--tenant` | first tenant when unambiguous |
| Project slug | `--project` | required |
| Environment | `--environment` | `production` |
| Account mode | `--auth` | ask the user; `login` when credentials are already set |
| Release provider | `--provider` | `local` (self-hosted platform) |

## Release Providers

The platform exposes an optional per-deployment release provider:

| Provider | Result | Typical use |
| --- | --- | --- |
| `local` (default) | `{SITE_URL_TEMPLATE}` content endpoint | the platform's own self-hosted publishing path |
| `netlify` | `https://{site}.netlify.app` | shareable public links with CDN |

The script defaults to `local` (self-hosted). Pass `--provider netlify` when the
user wants a public Netlify URL. Use `GET /api/v1/release-providers` to read
`defaultProvider` and `providers`. Provider is stored on the deployment, so
rollback reuses the original provider.

The ZIP is validated by the platform. It must:

- be a valid ZIP archive;
- contain `index.html` at its root;
- stay within configured file, expanded-size, and path limits;
- avoid path traversal, symbolic links, and executable files.

If every entry sits inside a single top-level folder that contains `index.html`
(for example `dist/index.html`), the platform drops that wrapper automatically.
macOS metadata such as `__MACOSX/` and `.DS_Store` is ignored. Archives with
several top-level folders must still place `index.html` at the ZIP root.

When validation fails, read `errorCode` and `errorMessage` from the artifact
response; `artifact.missing_entrypoint` lists the paths that were found.

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
