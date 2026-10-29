<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import request from './api/request'

// 根组件：获取站点配置，动态设置页面标题
onMounted(async () => {
  try {
    const cfg = await request.get('/site/config', { silent: true })
    if (cfg?.siteName) {
      document.title = cfg.siteName
    }
  } catch (e) {
    // 忽略，使用默认标题
  }
})
</script>

<style>
/* 全局基线：统一指向梧桐校园设计系统 tokens.css 的 --brand */
:root {
  /* 兼容旧引用：校园蓝品牌色（与 tokens.css --brand 对齐） */
  --campus-primary: var(--brand);
}
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
body {
  font-family: var(--font-sans);
  background: var(--paper);
  color: var(--ink);
}
a {
  text-decoration: none;
  color: var(--brand);
}
</style>
