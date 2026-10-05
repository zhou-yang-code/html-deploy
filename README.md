# HTML Deploy Platform

基于 JDK 21、Spring Boot 和 Vue 3 的多租户 HTML 静态部署平台。

## 已实现能力

- 邮箱注册、登录和 JWT。
- 租户、成员和基础 RBAC。
- 项目创建与默认子域名访问地址。
- ZIP 预签名上传，本地模式使用签名上传 URL，Compose 模式使用 MinIO。
- ZIP 路径穿越、符号链接、Zip Bomb、危险扩展名和入口文件校验。
- 不可变 artifact 和 release 版本。
- 部署状态机、Outbox Worker、部署历史和回滚。
- 本地文件发布与 Nginx 子域名承载。
- Docker Compose 一键启动 PostgreSQL、MinIO、后端、控制台和内容节点。

## 下载 Skill 后自动部署

仓库内置 `skills/html-deploy`，安装后可直接让 Codex 完成登录、创建项目、上传 ZIP、等待校验、发布到 Netlify，并返回公开 URL。

### 1. 安装 Skill

```powershell
npx skills add zhou-yang-code/html-deploy --skill html-deploy --full-depth -g -a codex --copy
```

### 2. 配置平台账号

```powershell
$env:HTML_DEPLOY_API_URL = "https://html-deploy-api-production.up.railway.app"
$env:HTML_DEPLOY_EMAIL = "<your-email>"
$env:HTML_DEPLOY_PASSWORD = "<your-password>"
$skillDir = Join-Path $HOME ".codex\skills\html-deploy"
```

首次使用且还没有账号时，可先注册：

```powershell
node "$skillDir\scripts\deploy.mjs" `
  --register `
  --file .\dist.zip `
  --tenant demo-tenant `
  --tenant-name "Demo Tenant" `
  --project demo-site `
  --name "Demo Site"
```

### 3. 自动发布

```powershell
node "$skillDir\scripts\deploy.mjs" `
  --file .\dist.zip `
  --tenant demo-tenant `
  --project demo-site `
  --name "Demo Site" `
  --environment production
```

Skill 会自动完成：

1. 登录平台并定位租户和项目。
2. 项目不存在时自动创建。
3. 上传 ZIP，等待 artifact 校验为 `READY`。
4. 创建 deployment，等待 Netlify 发布完成。
5. 返回 deployment ID、版本、状态和公开的 `https://*.netlify.app` 地址。

在 Codex 中也可以直接说：

```text
使用 $html-deploy 把 .\dist.zip 部署到 demo-site，并返回公网 URL。
```

## 目录

```text
backend/                  Spring Boot DDD 分层单体
frontend/                 Vue 3 控制台
skills/html-deploy/       自动部署 Skill
deploy/nginx/            静态站点内容节点
docker-compose.yml       完整本地环境
docs/                    技术方案
```

后端包结构固定为：

```text
interfaces/application/domain/infrastructure
```

每层内部按 `identity/project/artifact/deployment` 等业务上下文分包。

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

本地内容地址格式：

```text
http://{tenantSlug}-{projectSlug}.apps.localhost:8081
```

本地模式可启动轻量内容节点，从 `backend/data/www` 读取 release：

```powershell
node scripts/local-content-server.mjs
```

该脚本按 `.apps.localhost` Host 路由到对应站点的 `current` 目录。生产环境使用 Compose 中的 Nginx 内容节点。

## Docker Compose

```powershell
docker compose up --build
```

## 部署到 Render

仓库根目录包含 `render.yaml`，可以直接使用 Render Blueprint 创建 Spring Boot 服务和 PostgreSQL：

```text
New -> Blueprint -> 选择仓库
```

完整步骤见 [docs/render-backend-deployment.md](docs/render-backend-deployment.md)。

服务端口：

| 服务 | 地址 |
| --- | --- |
| 控制台 | http://localhost:5173 |
| 后端 API | http://localhost:8080 |
| 内容节点 | http://localhost:8081 |
| MinIO API | http://localhost:9000 |
| MinIO Console | http://localhost:9001 |

Compose 会创建以下存储：

- PostgreSQL 业务数据。
- MinIO 原始 ZIP 对象。
- 共享 volume 中的 release 和 Nginx `current` 链接。

## 主链路

1. 注册并创建租户。
2. 创建项目。
3. 上传包含根目录 `index.html` 的 ZIP。
4. 后端 Worker 校验并固化 artifact。
5. 创建 deployment。
6. 发布 Worker 生成不可变 release 并切换 active 指针。
7. 通过项目默认域名访问。
8. 在部署历史中对成功版本执行回滚。

## 测试与构建

```powershell
cd backend
mvn test

cd ..\frontend
npm run build
```

## 配置

本地默认配置位于 `backend/src/main/resources/application.yml`。

Compose 和 PostgreSQL 配置位于 `backend/src/main/resources/application-postgres.yml`。

生产环境至少需要替换：

- `JWT_SECRET`
- `UPLOAD_TOKEN_SECRET`
- 数据库密码
- MinIO 凭据
- 默认内容域名和 HTTPS 配置
