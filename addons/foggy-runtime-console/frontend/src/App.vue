<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { router } from './router'

function returnToLogin(): void {
  void router.replace({ name: 'login' })
}

onMounted(() => {
  window.addEventListener('foggy:runtime-auth-required', returnToLogin)
  window.addEventListener('foggy:runtime-session-cleared', returnToLogin)
})

onBeforeUnmount(() => {
  window.removeEventListener('foggy:runtime-auth-required', returnToLogin)
  window.removeEventListener('foggy:runtime-session-cleared', returnToLogin)
})
</script>

<template>
  <RouterView />
</template>
