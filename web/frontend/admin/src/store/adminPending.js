import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { statsPendingCounts } from '../api/stats'
import { pendingTotal } from '../layout/adminNavigation.mjs'

export const useAdminPendingStore = defineStore('adminPending', () => {
  const counts = ref(null)
  const loading = ref(false)
  const failed = ref(false)
  const total = computed(() => counts.value === null ? null : pendingTotal(counts.value))
  let version = 0

  async function refresh({ afterMutation = false } = {}) {
    if (loading.value && !afterMutation) return
    const request = ++version
    loading.value = true
    try {
      const data = await statsPendingCounts()
      if (request !== version) return
      counts.value = data || {}
      failed.value = false
    } catch {
      if (request === version) failed.value = true
    } finally {
      if (request === version) loading.value = false
    }
  }

  function clear() {
    version++
    counts.value = null
    loading.value = false
    failed.value = false
  }

  return { counts, loading, failed, total, refresh, clear }
})
