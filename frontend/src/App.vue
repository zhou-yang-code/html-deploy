<script setup lang="ts">
import { LogOut, Rocket } from 'lucide-vue-next'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { clearSession, hasSession } from '@/api'

const route = useRoute()
const router = useRouter()
const isLogin = computed(() => route.name === 'login')

function logout() {
  clearSession()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <header v-if="!isLogin && hasSession()" class="topbar">
      <RouterLink class="brand" to="/projects">
        <span class="brand-mark"><Rocket :size="17" /></span>
        <span>HTML Deploy</span>
      </RouterLink>
      <button class="icon-button" type="button" title="退出登录" @click="logout">
        <LogOut :size="18" />
      </button>
    </header>
    <main :class="['app-main', { 'app-main--login': isLogin }]">
      <RouterView />
    </main>
  </div>
</template>
