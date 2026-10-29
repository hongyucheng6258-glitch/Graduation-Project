<template>
  <router-view />
</template>

<script setup>
// 根组件
</script>

<style>
/* 全局基线：统一指向梧桐校园设计系统 tokens.css 的 --brand */
:root {
  /* 兼容旧引用：梧桐绿品牌色（与 tokens.css --brand 对齐） */
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
</style>
