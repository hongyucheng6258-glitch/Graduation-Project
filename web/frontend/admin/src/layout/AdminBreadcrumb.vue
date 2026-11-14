<script setup>
defineProps({ breadcrumbs: { type: Array, required: true } })
</script>

<template>
  <nav class="crumbs" aria-label="面包屑">
    <ol class="crumb-list" role="list">
      <li v-for="(crumb, index) in breadcrumbs" :key="index">
        <span v-if="index" class="sep" aria-hidden="true">›</span>
        <RouterLink v-if="crumb.to" class="crumb-label" :to="crumb.to">{{ crumb.label }}</RouterLink>
        <span v-else class="crumb-label" :aria-current="index === breadcrumbs.length - 1 ? 'page' : undefined">
          {{ crumb.label }}
        </span>
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.crumbs { min-width: 0; font-size: var(--fs-sm); color: var(--ink-3); }
.crumb-list { display: flex; flex-wrap: wrap; gap: 2px 8px; margin: 0; padding: 0; list-style: none; }
.crumb-list li { display: inline-flex; align-items: center; gap: 8px; white-space: nowrap; }
.crumb-label { padding-block: 3px; }
.crumbs a { color: var(--brand-strong); text-decoration: none; }
.crumbs a:hover, .crumbs a:focus-visible { text-decoration: underline; }
.crumbs [aria-current="page"] { color: var(--ink); font-weight: 650; }
.crumbs .sep { color: var(--ink-3); }
@media (max-width: 480px) {
  .crumb-list, .crumb-list li { column-gap: 5px; }
  .crumb-list li { white-space: normal; }
}
</style>
