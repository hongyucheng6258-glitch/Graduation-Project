<template>
  <AuthCampusShell compact-text="设置你的正式密码">
    <div class="change-password">
      <h1>设置新密码</h1>
      <p>你使用临时密码登录。请设置新密码，然后重新登录。</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="临时密码">
          <el-input v-model="form.oldPassword" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" autocomplete="new-password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="confirmation" type="password" autocomplete="new-password" show-password />
        </el-form-item>
        <p v-if="error" role="alert">{{ error }}</p>
        <el-button type="primary" native-type="submit" :loading="saving">保存新密码</el-button>
        <el-button native-type="button" :disabled="saving" @click="returnToLogin">返回登录</el-button>
      </el-form>
    </div>
  </AuthCampusShell>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthCampusShell from '../../components/auth/AuthCampusShell.vue'
import { updatePassword } from '../../api/user'
import { useUserStore } from '../../store/user'

const router = useRouter()
const userStore = useUserStore()
const form = reactive({ oldPassword: '', newPassword: '' })
const confirmation = ref('')
const error = ref('')
const saving = ref(false)

async function returnToLogin() {
  userStore.logout()
  await router.replace({ name: 'Login' })
}

async function submit() {
  if (saving.value) return
  error.value = ''
  if (!form.oldPassword || form.newPassword.length < 6) {
    error.value = '请输入临时密码，新密码至少需要 6 位。'
    return
  }
  if (form.newPassword !== confirmation.value) {
    error.value = '两次输入的新密码不一致。'
    return
  }
  saving.value = true
  try {
    await updatePassword(form)
    ElMessage.success('密码已更新，请重新登录')
    await returnToLogin()
  } catch (failure) {
    error.value = failure.message || '密码更新失败，请重试。'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.change-password { width: 100%; max-width: 400px; }
h1 { margin: 0 0 8px; }
p { margin: 0 0 20px; color: var(--ink-2); }
[role="alert"] { color: var(--danger, #b42318); }
</style>
