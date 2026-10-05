<script setup lang="ts">
import {
  Archive,
  ArrowLeft,
  CircleCheck,
  Clock3,
  CloudUpload,
  ExternalLink,
  History,
  RefreshCw,
  RotateCcw,
  Rocket,
  Trash2,
  TriangleAlert,
} from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { api } from '@/api'
import type { DeploymentDetails, ProjectSummary } from '@/types'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => String(route.params.projectId))
const project = ref<ProjectSummary | null>(null)
const deployments = ref<DeploymentDetails[]>([])
const selectedFile = ref<File | null>(null)
const loading = ref(true)
const deploying = ref(false)
const phase = ref('')
const error = ref('')
let pollTimer: number | undefined

const activeDeployment = computed(() => deployments.value.find((item) => item.status === 'ACTIVE'))

async function loadProject() {
  project.value = await api.project(projectId.value)
}

async function loadDeployments() {
  deployments.value = await api.deployments(projectId.value)
}

async function refresh() {
  error.value = ''
  try {
    await Promise.all([loadProject(), loadDeployments()])
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '项目加载失败'
  } finally {
    loading.value = false
  }
}

async function archiveProject() {
  if (!project.value || !window.confirm(`确认归档“${project.value.name}”？归档后项目会从列表隐藏，数据仍保留。`)) {
    return
  }
  error.value = ''
  try {
    await api.archiveProject(projectId.value)
    await router.push('/projects')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '项目归档失败'
  }
}

async function deleteProject() {
  if (!project.value) {
    return
  }
  const confirmation = window.prompt(`此操作会删除项目、部署历史和对应 Netlify Site，且不可恢复。\n请输入项目标识 ${project.value.slug} 确认：`)
  if (confirmation !== project.value.slug) {
    return
  }
  error.value = ''
  try {
    await api.deleteProject(projectId.value)
    await router.push('/projects')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '项目删除失败'
  }
}

function pickFile(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

async function deploy() {
  if (!selectedFile.value) {
    return
  }
  deploying.value = true
  error.value = ''
  phase.value = '创建上传任务'
  try {
    const upload = await api.createUpload(projectId.value, selectedFile.value.name)
    phase.value = '上传 ZIP'
    await api.uploadZip(upload, selectedFile.value)
    phase.value = '校验静态产物'
    await api.completeUpload(upload.artifactId)
    phase.value = '等待产物校验'
    await waitForArtifact(upload.artifactId)
    phase.value = '创建部署'
    const deployment = await api.createDeployment(projectId.value, upload.artifactId)
    phase.value = '发布到内容目录'
    await waitForDeployment(deployment.id)
    phase.value = '发布完成'
    selectedFile.value = null
    await loadDeployments()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '部署失败'
    phase.value = ''
  } finally {
    deploying.value = false
  }
}

async function waitForArtifact(artifactId: string) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const artifact = await api.artifact(artifactId)
    if (artifact.status === 'READY') {
      return artifact
    }
    if (artifact.status === 'REJECTED') {
      throw new Error(artifact.errorCode ? `产物校验失败：${artifact.errorCode}` : '产物校验失败')
    }
    await sleep(800)
  }
  throw new Error('产物校验超时')
}

async function waitForDeployment(deploymentId: string) {
  for (let attempt = 0; attempt < 45; attempt += 1) {
    const deployment = await api.deployment(deploymentId)
    if (deployment.status === 'ACTIVE') {
      return deployment
    }
    if (deployment.status === 'FAILED') {
      throw new Error(deployment.errorCode ? `部署失败：${deployment.errorCode}` : '部署失败')
    }
    await sleep(900)
  }
  throw new Error('部署超时')
}

function sleep(ms: number) {
  return new Promise((resolve) => {
    pollTimer = window.setTimeout(resolve, ms)
  })
}

async function rollback(deployment: DeploymentDetails) {
  if (!window.confirm(`确认回滚到版本 v${deployment.version}？`)) {
    return
  }
  error.value = ''
  try {
    const next = await api.rollback(deployment.id)
    await waitForDeployment(next.id)
    await loadDeployments()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '回滚失败'
  }
}

function statusClass(status: DeploymentDetails['status']) {
  return `status-${status.toLowerCase()}`
}

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
}

onMounted(refresh)
onBeforeUnmount(() => {
  if (pollTimer) {
    window.clearTimeout(pollTimer)
  }
})
</script>

<template>
  <section>
    <button class="back-button" type="button" @click="router.push('/projects')">
      <ArrowLeft :size="16" />
      返回项目
    </button>

    <div class="page-heading">
      <div>
        <h1>{{ project?.name ?? '项目' }}</h1>
        <p v-if="project">
          {{ project.slug }} ·
          <a :href="project.deploymentUrl" target="_blank" rel="noreferrer">
            {{ project.deploymentUrl }} <ExternalLink :size="13" />
          </a>
        </p>
      </div>
      <div class="header-actions">
        <button class="icon-button" type="button" title="归档项目" @click="archiveProject">
          <Archive :size="17" />
        </button>
        <button class="icon-button icon-button--danger" type="button" title="删除项目" @click="deleteProject">
          <Trash2 :size="17" />
        </button>
        <button class="button" type="button" @click="refresh">
          <RefreshCw :size="16" />
          刷新
        </button>
      </div>
    </div>

    <div v-if="error" class="error-banner">{{ error }}</div>

    <div v-if="loading" class="panel empty-state">正在加载发布面板...</div>

    <template v-else>
      <div class="active-strip">
        <div class="active-strip__icon">
          <CircleCheck v-if="activeDeployment" :size="20" />
          <Clock3 v-else :size="20" />
        </div>
        <div>
          <strong>{{ activeDeployment ? `版本 v${activeDeployment.version} 在线` : '暂无在线版本' }}</strong>
          <p v-if="activeDeployment">发布时间：{{ formatTime(activeDeployment.finishedAt) }}</p>
          <p v-else>上传 ZIP 并完成部署后，这里会显示当前版本。</p>
        </div>
        <span v-if="activeDeployment" class="status-badge status-active">ACTIVE</span>
      </div>

      <section class="panel deploy-panel">
        <div class="panel-header">
          <h2>发布新版本</h2>
          <CloudUpload :size="18" />
        </div>
        <div class="panel-body deploy-body">
          <label class="drop-zone">
            <input type="file" accept=".zip,application/zip" @change="pickFile">
            <CloudUpload :size="26" />
            <strong>{{ selectedFile?.name ?? '选择 ZIP 静态包' }}</strong>
            <span>压缩包根目录必须包含 index.html</span>
          </label>
          <div class="deploy-action">
            <div v-if="deploying" class="deploy-progress">
              <span class="spinner spinner--dark" />
              {{ phase }}
            </div>
            <button
              class="button button--primary"
              type="button"
              :disabled="!selectedFile || deploying"
              @click="deploy"
            >
              <Rocket :size="17" />
              部署到 production
            </button>
          </div>
        </div>
      </section>

      <section class="panel history-panel">
        <div class="panel-header">
          <h2>部署历史</h2>
          <div class="panel-title">
            <History :size="17" />
            {{ deployments.length }} 条记录
          </div>
        </div>

        <div v-if="deployments.length === 0" class="empty-state">暂无部署记录。</div>
        <div v-else class="deployment-table-wrap">
          <table class="deployment-table">
            <thead>
              <tr>
                <th>版本</th>
                <th>状态</th>
                <th>环境</th>
                <th>创建时间</th>
                <th>完成时间</th>
                <th>错误</th>
                <th aria-label="操作"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="deployment in deployments" :key="deployment.id">
                <td><strong>v{{ deployment.version }}</strong></td>
                <td>
                  <span :class="['status-badge', statusClass(deployment.status)]">
                    {{ deployment.status }}
                  </span>
                </td>
                <td>{{ deployment.environment }}</td>
                <td>{{ formatTime(deployment.createdAt) }}</td>
                <td>{{ formatTime(deployment.finishedAt) }}</td>
                <td class="error-cell">
                  <TriangleAlert v-if="deployment.errorCode" :size="14" />
                  {{ deployment.errorCode ?? '-' }}
                </td>
                <td>
                  <button
                    class="icon-button"
                    type="button"
                    title="回滚到此版本"
                    :disabled="!['ACTIVE', 'SUPERSEDED'].includes(deployment.status)"
                    @click="rollback(deployment)"
                  >
                    <RotateCcw :size="16" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.back-button {
  margin-bottom: 18px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: #5d6976;
  font-weight: 600;
}

.page-heading a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #167d7f;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-button--danger {
  color: #9f2f2f;
}

.active-strip {
  margin-bottom: 18px;
  padding: 17px 18px;
  display: flex;
  align-items: center;
  gap: 13px;
  border: 1px solid #cce1dd;
  border-radius: 8px;
  background: #f6fbfa;
}

.active-strip__icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: #dff1ed;
  color: #176e68;
}

.active-strip div:nth-child(2) {
  flex: 1;
  min-width: 0;
}

.active-strip p {
  margin: 4px 0 0;
  color: #65727f;
  font-size: 13px;
}

.deploy-panel,
.history-panel {
  margin-bottom: 20px;
}

.deploy-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
}

.drop-zone {
  min-height: 126px;
  padding: 20px;
  display: grid;
  place-items: center;
  gap: 6px;
  border: 1px dashed #b8c3cd;
  border-radius: 8px;
  background: #fafbfc;
  color: #52606e;
  text-align: center;
  cursor: pointer;
}

.drop-zone input {
  display: none;
}

.drop-zone span {
  color: #74818e;
  font-size: 12px;
}

.deploy-action {
  display: grid;
  gap: 12px;
  justify-items: end;
}

.deploy-progress {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #53616f;
  font-size: 13px;
}

.spinner--dark {
  border-color: rgba(22, 125, 127, 0.22);
  border-top-color: #167d7f;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #667382;
  font-size: 13px;
}

.deployment-table-wrap {
  overflow-x: auto;
}

.deployment-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.deployment-table th,
.deployment-table td {
  padding: 12px 16px;
  border-bottom: 1px solid #edf0f3;
  text-align: left;
  white-space: nowrap;
}

.deployment-table th {
  color: #65727f;
  font-size: 12px;
  font-weight: 700;
}

.deployment-table tr:last-child td {
  border-bottom: 0;
}

.status-badge {
  padding: 4px 7px;
  border-radius: 5px;
  background: #edf0f3;
  color: #586572;
  font-size: 11px;
  font-weight: 700;
}

.status-active {
  background: #e3f3ee;
  color: #17665f;
}

.status-failed {
  background: #fff0ee;
  color: #9b3535;
}

.status-deploying,
.status-created {
  background: #fff7df;
  color: #826317;
}

.status-superseded {
  background: #eef1f4;
  color: #697684;
}

.error-cell {
  max-width: 260px;
  overflow: hidden;
  color: #8c3838;
  text-overflow: ellipsis;
}

@media (max-width: 760px) {
  .deploy-body {
    grid-template-columns: 1fr;
  }

  .deploy-action {
    justify-items: stretch;
  }
}
</style>
