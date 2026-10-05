<script setup lang="ts">
import {
  Archive,
  ArchiveRestore,
  ExternalLink,
  FolderPlus,
  RefreshCw,
  Rocket,
  Trash2,
} from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { api } from '@/api'
import type { ProjectSummary, TenantSummary } from '@/types'

const router = useRouter()
const tenants = ref<TenantSummary[]>([])
const selectedTenantId = ref('')
const projects = ref<ProjectSummary[]>([])
const loading = ref(false)
const creating = ref(false)
const showArchived = ref(false)
const busyProjectId = ref('')
const pendingAction = ref<{ type: 'archive' | 'delete'; project: ProjectSummary } | null>(null)
const error = ref('')
const form = reactive({
  name: '',
  slug: '',
})

const selectedTenant = computed(() => tenants.value.find((tenant) => tenant.id === selectedTenantId.value))
const canCreate = computed(() => ['OWNER', 'MAINTAINER'].includes(selectedTenant.value?.role ?? ''))
const canManage = computed(() => selectedTenant.value?.role === 'OWNER')

async function loadProjects() {
  if (!selectedTenantId.value) {
    projects.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    projects.value = await api.projects(selectedTenantId.value, showArchived.value)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '项目加载失败'
  } finally {
    loading.value = false
  }
}

async function load() {
  error.value = ''
  try {
    tenants.value = await api.tenants()
    if (!selectedTenantId.value && tenants.value.length > 0) {
      selectedTenantId.value = tenants.value[0]!.id
    }
    await loadProjects()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '工作区加载失败'
  }
}

async function createProject() {
  if (!selectedTenantId.value) {
    return
  }
  creating.value = true
  error.value = ''
  try {
    const project = await api.createProject(selectedTenantId.value, {
      name: form.name,
      slug: form.slug || undefined,
    })
    form.name = ''
    form.slug = ''
    await loadProjects()
    await router.push(`/projects/${project.id}`)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '项目创建失败'
  } finally {
    creating.value = false
  }
}

async function restoreProject(project: ProjectSummary) {
  busyProjectId.value = project.id
  error.value = ''
  try {
    await api.restoreProject(project.id)
    await loadProjects()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '项目恢复失败'
  } finally {
    busyProjectId.value = ''
  }
}

function askArchive(project: ProjectSummary) {
  pendingAction.value = { type: 'archive', project }
}

function askDelete(project: ProjectSummary) {
  pendingAction.value = { type: 'delete', project }
}

function cancelAction() {
  pendingAction.value = null
}

async function confirmAction() {
  const action = pendingAction.value
  if (!action) {
    return
  }
  busyProjectId.value = action.project.id
  error.value = ''
  try {
    if (action.type === 'archive') {
      await api.archiveProject(action.project.id)
    } else {
      await api.deleteProject(action.project.id)
    }
    pendingAction.value = null
    await loadProjects()
  } catch (cause) {
    error.value = cause instanceof Error
      ? cause.message
      : action.type === 'archive' ? '项目归档失败' : '项目删除失败'
  } finally {
    busyProjectId.value = ''
  }
}

watch(selectedTenantId, loadProjects)
watch(showArchived, loadProjects)
onMounted(load)
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <h1>项目</h1>
        <p>每个项目对应一个可独立发布的静态站点。</p>
      </div>
      <button class="button" type="button" @click="load">
        <RefreshCw :size="16" />
        刷新
      </button>
    </div>

    <div v-if="error" class="error-banner">{{ error }}</div>

    <div class="tenant-row">
      <div class="field tenant-select">
        <label for="tenant">当前租户</label>
        <select id="tenant" v-model="selectedTenantId">
          <option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id">
            {{ tenant.name }} · {{ tenant.slug }}
          </option>
        </select>
      </div>
      <div class="role-chip">{{ selectedTenant?.role ?? 'NO TENANT' }}</div>
    </div>

    <section v-if="canCreate" class="panel create-panel">
      <div class="panel-header">
        <h2>新建项目</h2>
        <FolderPlus :size="18" />
      </div>
      <div class="panel-body">
        <form class="form-grid" @submit.prevent="createProject">
          <div class="field">
            <label for="projectName">项目名称</label>
            <input id="projectName" v-model.trim="form.name" placeholder="国庆活动页" required>
          </div>
          <div class="field">
            <label for="projectSlug">项目标识</label>
            <input id="projectSlug" v-model.trim="form.slug" pattern="[a-z0-9-]+" placeholder="national-day，可留空">
          </div>
          <button class="button button--primary" type="submit" :disabled="creating">
            <span v-if="creating" class="spinner" />
            <FolderPlus v-else :size="16" />
            创建
          </button>
        </form>
      </div>
    </section>

    <section class="panel projects-panel">
      <div class="panel-header">
        <h2>项目列表</h2>
        <div class="panel-actions">
          <label class="archived-toggle">
            <input v-model="showArchived" type="checkbox">
            显示已归档
          </label>
          <span class="muted">{{ projects.length }} 个项目</span>
        </div>
      </div>

      <div v-if="loading" class="empty-state">正在加载项目...</div>
      <div v-else-if="projects.length === 0" class="empty-state">
        <Rocket :size="30" />
        <p>还没有项目。创建项目后即可上传 ZIP 发布。</p>
      </div>
      <div v-else class="project-grid">
        <article v-for="project in projects" :key="project.id" class="project-card">
          <div class="project-card__main">
            <div>
              <h3>{{ project.name }}</h3>
              <p>{{ project.slug }}</p>
            </div>
            <span class="status-badge">{{ project.status }}</span>
          </div>
          <a :href="project.deploymentUrl" target="_blank" rel="noreferrer" class="project-url">
            {{ project.deploymentUrl }}
            <ExternalLink :size="14" />
          </a>
          <div v-if="pendingAction?.project.id === project.id" class="project-confirm">
            <span class="project-confirm__text">
              {{ pendingAction.type === 'delete' ? `确认删除 ${project.slug}？删除后不可恢复。` : `确认归档 ${project.name}？` }}
            </span>
            <div class="project-confirm__actions">
              <button
                class="button button--danger"
                type="button"
                :disabled="busyProjectId === project.id"
                @click="confirmAction"
              >
                确认
              </button>
              <button class="button" type="button" :disabled="busyProjectId === project.id" @click="cancelAction">
                取消
              </button>
            </div>
          </div>
          <div v-else class="project-card__actions">
            <button class="button project-open" type="button" @click="router.push(`/projects/${project.id}`)">
              打开发布面板
            </button>
            <template v-if="canManage">
              <button
                class="icon-button"
                type="button"
                :title="project.status === 'ARCHIVED' ? '恢复项目' : '归档项目'"
                :disabled="busyProjectId === project.id"
                @click="project.status === 'ARCHIVED' ? restoreProject(project) : askArchive(project)"
              >
                <ArchiveRestore v-if="project.status === 'ARCHIVED'" :size="16" />
                <Archive v-else :size="16" />
              </button>
              <button
                class="icon-button icon-button--danger"
                type="button"
                title="删除项目"
                :disabled="busyProjectId === project.id"
                @click="askDelete(project)"
              >
                <Trash2 :size="16" />
              </button>
            </template>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<style scoped>
.tenant-row {
  display: flex;
  align-items: end;
  gap: 12px;
  margin-bottom: 18px;
}

.tenant-select {
  width: min(420px, 100%);
}

.role-chip {
  min-height: 40px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  border: 1px solid #d7dde4;
  border-radius: 7px;
  background: #ffffff;
  color: #52606e;
  font-size: 12px;
  font-weight: 700;
}

.create-panel {
  margin-bottom: 20px;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.archived-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #5d6976;
  font-size: 13px;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 14px;
  padding: 18px;
}

.project-card {
  min-width: 0;
  padding: 17px;
  border: 1px solid #dde3e9;
  border-radius: 8px;
  background: #ffffff;
  display: grid;
  gap: 14px;
}

.project-card__main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.project-card h3 {
  margin: 0 0 5px;
  font-size: 16px;
}

.project-card p {
  margin: 0;
  color: #6a7785;
  font-size: 13px;
}

.status-badge {
  padding: 4px 7px;
  border-radius: 5px;
  background: #e9f5f2;
  color: #17665f;
  font-size: 11px;
  font-weight: 700;
}

.project-url {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #167d7f;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-open {
  flex: 1;
}

.project-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-confirm {
  display: grid;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #f0d5d5;
  border-radius: 7px;
  background: #fdf5f5;
}

.project-confirm__text {
  color: #7d3b3b;
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.project-confirm__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
