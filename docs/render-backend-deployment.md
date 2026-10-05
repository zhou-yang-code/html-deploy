# 后端部署到 Render

## 1. 适用边界

Render 可以运行当前 Spring Boot 后端、PostgreSQL 和后台 Worker，但不能运行 MinIO 和本地 Nginx 内容节点。

推荐拓扑：

```text
Netlify Console
      |
      v
Render Web Service (Spring Boot)
      |
      +-- Render PostgreSQL
      |
      +-- S3 / Cloudflare R2 / Backblaze B2
      |
      +-- Netlify Deploy API（让发布结果返回公网静态站 URL）
```

只部署 Render 后端时，API、登录、项目和部署状态都可以正常工作。当前版本返回的 `*.apps.localhost:8081` 仍然只是本地内容域名，别人无法访问。要让静态站点公网可访问，需要：

- 增加 Netlify ReleasePublisher；或
- 给 Render 服务配置持久磁盘和公网内容域名/反向代理。

## 2. 准备仓库

把项目推送到 GitHub、GitLab 或 Bitbucket。Render Blueprint 会使用根目录的 `render.yaml`。

确认以下文件存在：

```text
render.yaml
backend/Dockerfile
backend/pom.xml
```

## 3. 准备对象存储

Render 不提供 MinIO。建议使用：

- Cloudflare R2
- AWS S3
- Backblaze B2 S3

创建 bucket，例如：

```text
html-deploy
```

记录：

```text
S3_ENDPOINT
S3_PUBLIC_ENDPOINT
S3_ACCESS_KEY
S3_SECRET_KEY
S3_BUCKET
S3_REGION
```

`S3_ENDPOINT` 是后端访问地址，`S3_PUBLIC_ENDPOINT` 是浏览器访问预签名上传地址的外网地址。对象存储需要允许浏览器跨域 `PUT` 上传 ZIP。

## 4. 使用 Blueprint 创建服务

在 Render 控制台选择：

```text
New -> Blueprint
```

选择仓库，Render 会读取 `render.yaml` 并创建：

- `html-deploy-api` Web Service
- `html-deploy-db` PostgreSQL

首次创建时填写 `sync: false` 的环境变量：

```text
S3_ENDPOINT
S3_PUBLIC_ENDPOINT
S3_ACCESS_KEY
S3_SECRET_KEY
PUBLIC_BASE_URL
CONTENT_DOMAIN
```

`PUBLIC_BASE_URL` 先填写 Render 预计分配的地址，例如：

```text
https://html-deploy-api.onrender.com
```

部署完成后如果实际域名不同，再回到 Render 环境变量中修正并重新部署。

`DATABASE_URL` 由 Render 自动注入。后端会把它转换成 Spring JDBC 需要的：

```text
jdbc:postgresql://...
```

## 5. 手动创建 Web Service

不使用 Blueprint 时，按以下配置创建：

| 配置 | 值 |
| --- | --- |
| Runtime | Docker |
| Dockerfile Path | `backend/Dockerfile` |
| Docker Build Context | `backend` |
| Health Check Path | `/actuator/health` |
| Auto Deploy | Yes |

环境变量：

```text
SPRING_PROFILES_ACTIVE=postgres
DATABASE_URL=<Render PostgreSQL connection string>
S3_ENDPOINT=<S3 endpoint>
S3_PUBLIC_ENDPOINT=<public S3 endpoint>
S3_ACCESS_KEY=<access key>
S3_SECRET_KEY=<secret key>
S3_BUCKET=html-deploy
S3_REGION=auto
STORAGE_ROOT=/tmp/html-deploy
NGINX_ROOT=/tmp/html-deploy/www
PUBLIC_BASE_URL=https://html-deploy-api.onrender.com
CONTENT_SCHEME=https
CONTENT_DOMAIN=<public content domain>
JWT_SECRET=<random 32+ byte secret>
UPLOAD_TOKEN_SECRET=<random 32+ byte secret>
```

`STORAGE_ROOT` 使用 `/tmp` 时，服务重建后会丢失 release 文件。需要持久化发布内容时，给 Web Service 配置 Render Persistent Disk：

```text
Mount Path: /var/html-deploy
STORAGE_ROOT=/var/html-deploy
NGINX_ROOT=/var/html-deploy/www
```

Persistent Disk 需要付费实例，并且只能挂载到一个服务。

## 6. 验证部署

渲染完成后访问：

```text
https://html-deploy-api.onrender.com/actuator/health
```

预期：

```json
{
  "status": "UP"
}
```

测试注册：

```powershell
$body = @{
  email = "owner@example.com"
  password = "Password123!"
  tenantName = "Demo Tenant"
  tenantSlug = "demo-tenant"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "https://html-deploy-api.onrender.com/api/v1/auth/register" `
  -ContentType "application/json" `
  -Body $body
```

## 7. 让部署 skill 指向 Render

```powershell
$env:HTML_DEPLOY_API_URL = "https://html-deploy-api.onrender.com"
$env:HTML_DEPLOY_EMAIL = "owner@example.com"
$env:HTML_DEPLOY_PASSWORD = "Password123!"

node scripts/deploy.mjs `
  --file .\dist.zip `
  --tenant demo-tenant `
  --project demo-site `
  --name "Demo Site"
```

如果 Render 后端尚未接入 Netlify ReleasePublisher，skill 返回的 `url` 仍然是配置的内容域名。要返回 `https://xxx.netlify.app`，需要把 `ReleasePublisher` 从本地 Nginx 实现切换到 Netlify 实现，并把 Netlify 返回的 `deploy_ssl_url` 保存到 deployment。

## 8. 免费实例注意事项

- Render Free Web Service 一段时间无请求后会休眠，首次访问会变慢。
- 免费 PostgreSQL 有存储和连接限制。
- 免费实例没有 Persistent Disk，`/tmp` 内容会在重启或重新部署后丢失。
- 生产环境建议使用付费 Starter 实例，并为发布目录挂载磁盘或把内容层迁移到 Netlify/S3/CDN。
