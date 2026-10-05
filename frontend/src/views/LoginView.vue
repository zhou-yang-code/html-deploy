<script setup lang="ts">
import { ArrowRight, KeyRound, Rocket, UserPlus } from 'lucide-vue-next'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { api } from '@/api'

type Mode = 'login' | 'register'

const route = useRoute()
const router = useRouter()
const mode = ref<Mode>('login')
const busy = ref(false)
const error = ref('')
const form = reactive({
  email: '',
  password: '',
  tenantName: '',
  tenantSlug: '',
})

const title = computed(() => (mode.value === 'login' ? '登录平台' : '创建第一个租户'))
const submitLabel = computed(() => (mode.value === 'login' ? '登录' : '注册并进入'))

async function submit() {
  busy.value = true
  error.value = ''
  try {
    if (mode.value === 'login') {
      await api.login({ email: form.email, password: form.password })
    } else {
      await api.register({
        email: form.email,
        password: form.password,
        tenantName: form.tenantName,
        tenantSlug: form.tenantSlug || undefined,
      })
    }
    await router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/projects')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '请求失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="login-page">
    <div class="login-panel">
      <div class="login-brand">
        <span class="brand-mark"><Rocket :size="20" /></span>
        <div>
          <h1>HTML Deploy</h1>
          <p>静态站点发布控制台</p>
        </div>
      </div>

      <div class="auth-switch" role="tablist" aria-label="登录或注册">
        <button
          type="button"
          :class="{ active: mode === 'login' }"
          @click="mode = 'login'"
        >
          <KeyRound :size="16" />
          登录
        </button>
        <button
          type="button"
          :class="{ active: mode === 'register' }"
          @click="mode = 'register'"
        >
          <UserPlus :size="16" />
          注册
        </button>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <div>
          <h2>{{ title }}</h2>
          <p class="muted">使用邮箱和密码进入工作区。</p>
        </div>

        <div v-if="error" class="error-banner">{{ error }}</div>

        <div class="field">
          <label for="email">邮箱</label>
          <input
            id="email"
            v-model.trim="form.email"
            type="email"
            autocomplete="email"
            placeholder="owner@example.com"
            required
          >
        </div>

        <div class="field">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="form.password"
            type="password"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
            minlength="8"
            placeholder="至少 8 位"
            required
          >
        </div>

        <template v-if="mode === 'register'">
          <div class="field">
            <label for="tenantName">租户名称</label>
            <input
              id="tenantName"
              v-model.trim="form.tenantName"
              type="text"
              placeholder="例如：Acme Studio"
              required
            >
          </div>
          <div class="field">
            <label for="tenantSlug">租户标识</label>
            <input
              id="tenantSlug"
              v-model.trim="form.tenantSlug"
              type="text"
              pattern="[a-z0-9-]+"
              placeholder="acme-studio，可留空自动生成"
            >
          </div>
        </template>

        <button class="button button--primary login-submit" type="submit" :disabled="busy">
          <span v-if="busy" class="spinner" />
          <span>{{ submitLabel }}</span>
          <ArrowRight v-if="!busy" :size="17" />
        </button>
      </form>
    </div>
  </section>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 28px;
  background:
    linear-gradient(90deg, rgba(22, 125, 127, 0.05) 1px, transparent 1px),
    linear-gradient(rgba(22, 125, 127, 0.05) 1px, transparent 1px),
    #f5f6f8;
  background-size: 28px 28px;
}

.login-panel {
  width: min(440px, 100%);
  padding: 28px;
  border: 1px solid #dce2e8;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 16px 48px rgba(38, 48, 58, 0.09);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.login-brand h1 {
  margin: 0;
  font-size: 20px;
}

.login-brand p {
  margin: 3px 0 0;
  color: #6b7785;
  font-size: 13px;
}

.auth-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 4px;
  border-radius: 8px;
  background: #eef1f4;
}

.auth-switch button {
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #5a6674;
  font-weight: 600;
}

.auth-switch button.active {
  background: #ffffff;
  color: #1b2834;
  box-shadow: 0 1px 4px rgba(33, 43, 54, 0.12);
}

.login-form {
  display: grid;
  gap: 16px;
  margin-top: 24px;
}

.login-form h2 {
  margin: 0 0 4px;
  font-size: 22px;
}

.login-form p {
  margin: 0;
}

.login-submit {
  width: 100%;
  margin-top: 4px;
}
</style>
