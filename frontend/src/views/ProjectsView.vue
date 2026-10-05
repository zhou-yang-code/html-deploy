<script setup lang="ts">
import { ExternalLink, FolderPlus, RefreshCw, Rocket } from 'lucide-vue-next'
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
const error = ref('')
const form = reactive({
  name: '',
  slug: '',
})

const selectedTenant = computed(() => tenants.value.find((tenant) => tenant.id === selectedTenantId.value))
const canCreate = computed(() => ['OWNER', 'MAINTAINER'].includes(selectedTenant.value?.role ?? ''))

async function loadProjects() {
  if (!selectedTenantId.value) {
    projects.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    projects.value = await api.projects(selectedTenantId.value)
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

watch(selectedTenantId, loadProjects)
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
        <span class="muted">{{ projects.length }} 个项目</span>
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
          <button class="button project-open" type="button" @click="router.push(`/projects/${project.id}`)">
            打开发布面板
          </button>
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
  width: 100%;
}
</style>
