<template>
  <!-- 空状态占位组件 -->
  <el-empty :description="description" :image-size="imageSize">
    <slot />
  </el-empty>
</template>

<script setup>
defineProps({
  description: { type: String, default: '暂无数据' },
  imageSize: { type: Number, default: 120 }
})
</script>
