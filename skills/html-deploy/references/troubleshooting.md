# Troubleshooting

## `Invalid CORS request`

The browser Origin is not in the backend allowlist. For local Vite, both forms may appear:

```text
http://localhost:5173
http://127.0.0.1:5173
```

Check `app.security.allowed-origins` in the backend configuration. Do not disable CORS globally as a fix.

## `Unexpected token ... is not valid JSON`

An error response was plain text rather than JSON, usually from a CORS rejection or proxy error.

1. inspect the HTTP status and raw response body;
2. fix the upstream response if it should be JSON;
3. clients should preserve the raw body as the fallback error message instead of assuming JSON.

## Authentication Fails

- Confirm the API base URL.
- Check `HTML_DEPLOY_EMAIL` and `HTML_DEPLOY_PASSWORD`.
- Local demo credentials exist only while the local H2 database remains.
- Do not print tokens when reporting failures.

## Upload Fails

- Verify the file is a ZIP.
- Verify the upload URL has not expired.
- Copy the exact `Content-Type` expected by the upload target.
- In S3/MinIO mode, ensure the browser or CI client can reach the presigned public endpoint.
- Do not retry the same expired presigned request; create a new artifact target.

## Artifact Is `REJECTED`

Common codes:

- `artifact.invalid_archive`
- `artifact.path_traversal`
- `artifact.symlink`
- `artifact.too_many_files`
- `artifact.expanded_too_large`
- `artifact.missing_entrypoint`
- `artifact.blocked_file_type`

Create the ZIP so `index.html` is at the archive root. Remove parent-directory entries, symlinks, executables, and build caches.

## Deployment Is `FAILED`

- `artifact.not_ready`: wait for validation or inspect the artifact rejection.
- `deployment.publish_failed`: inspect release storage permissions and worker logs.
- A failed deployment does not replace the active release.
- Create a new deployment after fixing the cause.

## Rollback

Only `ACTIVE` or `SUPERSEDED` deployments can be rollback targets. Rollback creates a new deployment version and then switches the active release.

## Verify the Published Site

Request the deployment URL and require HTTP 200. Check that the response contains an expected marker from the deployed page.

For local content routing, the Host header pattern is:

```text
{tenantSlug}-{projectSlug}.apps.localhost:8081
```
