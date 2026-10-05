# HTML Deploy Platform

基于 JDK 21、Spring Boot、DDD 和 Vue 3 的多租户 HTML 静态部署平台。

## 线上演示

| 服务 | 地址 |
| --- | --- |
| 控制台 | https://html-deploy-console-zhou-yang-code.netlify.app |
| 后端健康检查 | https://html-deploy-api-production.up.railway.app/actuator/health |

后端 API 根路径不提供 HTML 页面，不能直接当网站打开。业务接口前缀为：

```text
https://html-deploy-api-production.up.railway.app/api/v1
```

服务是否在线请访问“后端健康检查”链接。

演示项目：[数字华容道公网演示](https://zdemo20261005final-huarong-dao-4384a449.netlify.app) · [页面源码](games/huarong-dao/index.html)

## 核心能力

- 邮箱注册、登录和 JWT。
- 租户、成员和基础 RBAC。
- 项目创建和项目级发布地址。
- ZIP 预签名上传，支持本地签名 URL 和 S3/MinIO。
- ZIP 路径穿越、符号链接、Zip Bomb、危险扩展名和入口文件校验；单个顶层目录会自动剥离，`__MACOSX`/`.DS_Store` 会被忽略，校验失败原因随 artifact 返回。
- 不可变 artifact、release 版本和部署历史。
- Outbox Worker、部署状态机和回滚。
- 项目归档、恢复和彻底删除（删除会清理发布数据、本地 release 与对应 Netlify Site）。
- 两种内容发布模式，可按次发布选择（不传则用后端默认值）：
  - `local`：由平台自己的 Spring Boot 内容接口发布，返回 `/sites/{site}/` 地址。
  - `netlify`：调用 Netlify Deploy API，返回 `https://*.netlify.app` 地址。
- Docker Compose 一键启动 PostgreSQL、MinIO、后端、控制台和 Nginx 内容节点。

## 架构

```text
Vue Console / Deploy Skill
          |
          v
Spring Boot API（Railway）
          |
          +-- PostgreSQL
          |
          +-- Outbox Worker
                |
                +-- local -> /sites/{site}/ 内容接口
                |
                +-- netlify -> Netlify Deploy API
```

后端包结构固定为：

```text
interfaces/application/domain/infrastructure
```

每层内部按 `identity/project/artifact/deployment` 等业务上下文分包。

## 安装部署 Skill

仓库内置 `skills/html-deploy`。安装后可以让 Codex 完成登录、定位租户和项目、上传 ZIP、等待校验、创建部署并返回最终公开 URL。

```powershell
npx skills add zhou-yang-code/html-deploy --skill html-deploy --full-depth
```

需要安装到全局 Codex：

```powershell
npx skills add zhou-yang-code/html-deploy --skill html-deploy --full-depth -g -a codex --copy
```

## 使用 Skill 发布

### 配置账号

```powershell
$env:HTML_DEPLOY_API_URL = "https://html-deploy-api-production.up.railway.app"
$env:HTML_DEPLOY_EMAIL = "<your-email>"
$env:HTML_DEPLOY_PASSWORD = "<your-password>"
$skillDir = Join-Path $HOME ".codex\skills\html-deploy"
```

首次使用且还没有账号时：

```powershell
node "$skillDir\scripts\deploy.mjs" `
  --register `
  --file .\dist.zip `
  --tenant demo-tenant `
  --tenant-name "Demo Tenant" `
  --project demo-site `
  --name "Demo Site"
```

### 自动发布

```powershell
node "$skillDir\scripts\deploy.mjs" `
  --file .\dist.zip `
  --tenant demo-tenant `
  --project demo-site `
  --name "Demo Site" `
  --environment production
```

Skill 会依次完成：

1. 登录并解析租户和项目。
2. 项目不存在时自动创建。
3. 创建 artifact、上传 ZIP 并完成上传确认。
4. 等待 artifact 状态变为 `READY`。
5. 创建 deployment。
6. 等待当前 Publisher 完成发布。
7. 返回 deployment ID、版本、状态和公开 URL。

在 Codex 中也可以直接说：

```text
使用 $html-deploy 把 .\dist.zip 部署到 demo-site，并返回公网 URL。
```

## 内容发布模式

发布模式是**每次发布可选**的参数，不传时使用后端配置的默认值。

| Provider | 返回地址 | 适用场景 |
| --- | --- | --- |
| `netlify` | `https://{site}.netlify.app` | 长期公网分享和 CDN |
| `local` | `https://api.example.com/sites/{site}/` | 验证平台自己的发布链路 |

控制台在“发布新版本”处提供服务选择；Skill 使用 `--provider netlify|local`。
读取当前默认值和可选值：

```http
GET /api/v1/release-providers
```

创建部署时传入 `provider` 即可覆盖默认值：

```json
{
  "artifactId": "uuid",
  "environment": "production",
  "provider": "netlify"
}
```

Provider 会随部署记录落库，回滚复用原部署的 provider。

后端环境变量只决定**默认** provider：

| 模式 | `RELEASE_PROVIDER` | 返回地址 | 适用场景 |
| --- | --- | --- | --- |
| Netlify（当前默认） | `netlify` | `https://{site}.netlify.app` | 长期公网分享和 CDN |
| 自托管（测试） | `local` | `https://api.example.com/sites/{site}/` | 验证平台自己的发布链路 |

### 自托管模式

后端内容接口：

```text
GET /sites/{tenantSlug}-{projectSlug}/**
```

URL 模板：

```text
SITE_URL_TEMPLATE=https://html-deploy-api-production.up.railway.app/sites/{site}/
```

当前 Railway 临时部署使用该模式时，release 文件位于容器临时磁盘。服务重建或重启后，历史 release 文件可能丢失，但新发布仍可正常生成。

### Netlify 模式

配置：

```text
RELEASE_PROVIDER=netlify
NETLIFY_AUTH_TOKEN=<netlify-personal-access-token>
```

Worker 会查找或创建 Netlify Site，将校验后的静态文件打包并调用 Netlify Deploy API。Netlify 内容不依赖 Railway 临时磁盘，适合长期分享。

## 目录

```text
backend/                  Spring Boot DDD 分层单体
frontend/                 Vue 3 控制台
skills/html-deploy/       自动部署 Skill
games/huarong-dao/         华容道示例页面
deploy/nginx/             静态站点内容节点
docker-compose.yml        本地完整环境
docs/                     技术方案和 Render 部署说明
```

## 本地开发

本机只需要 JDK 21、Maven 和 Node.js，不要求安装 Docker。默认使用 H2 文件和本地文件存储。

启动后端：

```powershell
cd backend
mvn spring-boot:run
```

启动前端：

```powershell
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。Vite 会把 `/api` 代理到 `http://localhost:8080`。

本地内容地址默认格式：

```text
http://{tenantSlug}-{projectSlug}.apps.localhost:8081
```

本地内容节点：

```powershell
node scripts/local-content-server.mjs
```

## Docker Compose

```powershell
docker compose up --build
```

| 服务 | 地址 |
| --- | --- |
| 控制台 | http://localhost:5173 |
| 后端 API | http://localhost:8080 |
| 内容节点 | http://localhost:8081 |
| MinIO API | http://localhost:9000 |
| MinIO Console | http://localhost:9001 |

Compose 会创建：

- PostgreSQL 业务数据。
- MinIO 原始 ZIP 对象。
- 共享 volume 中的 release 和 Nginx `current` 链接。

## Railway 部署

当前线上后端使用 Railway PostgreSQL 和 Docker Web Service。

关键环境变量：

```text
SPRING_PROFILES_ACTIVE=postgres
DATABASE_URL=<railway-postgres-url>
STORAGE_TYPE=local
RELEASE_PROVIDER=netlify
NETLIFY_AUTH_TOKEN=<netlify-personal-access-token>
PUBLIC_BASE_URL=https://html-deploy-api-production.up.railway.app
```

临时切换到自托管测试：

```text
RELEASE_PROVIDER=local
SITE_URL_TEMPLATE=https://html-deploy-api-production.up.railway.app/sites/{site}/
```

## Render 部署

仓库包含 `render.yaml`，可作为 Railway 的替代方案。

```text
New -> Blueprint -> 选择仓库
```

完整步骤见 [docs/render-backend-deployment.md](docs/render-backend-deployment.md)。

## 主链路

1. 注册并创建租户。
2. 创建项目。
3. 上传包含根目录 `index.html` 的 ZIP。
4. Worker 校验并固化 artifact。
5. 创建 deployment。
6. Publisher 根据 `RELEASE_PROVIDER` 发布到自托管内容接口或 Netlify。
7. 更新 `ReleaseChannel` active 指针。
8. 返回公开 URL。
9. 在部署历史中对成功版本执行回滚。

## 测试与构建

```powershell
cd backend
mvn test

cd ..\frontend
npm run build
```

## 生产配置清单

至少替换：

- `JWT_SECRET`
- `UPLOAD_TOKEN_SECRET`
- 数据库密码
- 对象存储凭据
- `PUBLIC_BASE_URL`
- `SITE_URL_TEMPLATE`
- 自定义域名和 HTTPS 配置

如果使用 Netlify Publisher，还需定期轮换 `NETLIFY_AUTH_TOKEN`。
