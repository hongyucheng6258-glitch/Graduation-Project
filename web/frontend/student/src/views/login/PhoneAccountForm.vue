<template>
  <el-form label-position="top" @submit.prevent="submit">
    <p class="hint">测试验证码显示在浏览器开发者工具的控制台。</p>
    <el-form-item label="手机号">
      <el-input v-model.trim="form.phone" type="tel" maxlength="11" autocomplete="tel" />
    </el-form-item>
    <el-form-item label="短信验证码">
      <el-input v-model.trim="form.code" maxlength="6" inputmode="numeric" autocomplete="one-time-code">
        <template #append>
          <el-button native-type="button" :disabled="sending || seconds > 0" @click="send">
            {{ seconds ? seconds + ' 秒后重发' : '获取验证码' }}
          </el-button>
        </template>
      </el-input>
    </el-form-item>
    <template v-if="purpose === 'reset'">
      <el-form-item label="新密码"><el-input v-model="form.password" type="password" autocomplete="new-password" show-password /></el-form-item>
      <el-form-item label="确认新密码"><el-input v-model="confirmation" type="password" autocomplete="new-password" show-password /></el-form-item>
    </template>
    <el-button type="primary" native-type="submit" :loading="saving">{{ label }}</el-button>
  </el-form>
</template>

<script setup>
import { computed, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { bindPhone, loginByPhone, resetByPhone, sendPhoneCode } from '../../api/accountRecovery'

const props = defineProps({ purpose: { type: String, required: true }, initialPhone: String })
const emit = defineEmits(['done'])
const form = reactive({ phone: props.initialPhone || '', code: '', password: '' })
const confirmation = ref('')
const saving = ref(false)
const sending = ref(false)
const seconds = ref(0)
let timer
const label = computed(() => ({ bind: '验证并绑定', login: '验证码登录', reset: '保存新密码' }[props.purpose]))

async function send() {
  if (sending.value || seconds.value) return
  sending.value = true
  try {
    await sendPhoneCode(form.phone, props.purpose)
    ElMessage.success('验证码已发送，请查看控制台')
    seconds.value = 60
    timer = setInterval(() => {
      if (--seconds.value === 0) clearInterval(timer)
    }, 1000)
  } finally {
    sending.value = false
  }
}

async function submit() {
  if (saving.value) return
  if (props.purpose === 'reset' && form.password !== confirmation.value) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    const action = { bind: bindPhone, login: loginByPhone, reset: resetByPhone }[props.purpose]
    emit('done', await action({ ...form }))
  } finally {
    saving.value = false
  }
}

onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.hint { color: var(--ink-2); font-size: 13px; margin-bottom: 14px; }
</style>
