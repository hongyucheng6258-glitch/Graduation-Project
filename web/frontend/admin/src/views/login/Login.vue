<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="brand">🎓 校园平台 · 管理后台</div>
      <el-form :model="form" size="large" @keyup.enter="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="管理员账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item>
          <div class="captcha-row">
            <el-input v-model="form.captchaCode"
                      :placeholder="captchaMode === 'math' ? '输入运算结果' : '4位验证码'"
                      :maxlength="captchaMode === 'math' ? 3 : 4" @keyup.enter="submit" />
            <img v-if="captchaMode !== 'math'" class="captcha-img" :src="captchaImage"
                 title="看不清？点击刷新" @click="loadCaptcha" />
            <div v-else class="captcha-math" title="换一题" @click="loadCaptcha">{{ captchaExpression }}</div>
          </div>
        </el-form-item>
        <el-form-item>
          <div class="login-row">
            <el-checkbox v-model="remember">记住此设备</el-checkbox>
            <a class="forgot" @click.prevent>忘记密码？</a>
          </div>
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <div class="tip">初始账号：admin / admin123（请登录后及时修改）</div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { adminLogin, getCaptcha } from '../../api/auth'
import { useAdminStore } from '../../store/admin'

const router = useRouter()
const adminStore = useAdminStore()
const loading = ref(false)
const captchaImage = ref('')
const captchaMode = ref('image')
const captchaExpression = ref('')
const remember = ref(false)
const form = reactive({ username: '', password: '', captchaId: '', captchaCode: '' })

async function loadCaptcha() {
  const data = await getCaptcha()
  form.captchaId = data.captchaId
  captchaMode.value = data.mode || 'image'
  captchaImage.value = data.image
  captchaExpression.value = data.expression
  form.captchaCode = ''
}

async function submit() {
  if (!form.username || !form.password || !form.captchaCode) {
    ElMessage.warning('请输入账号、密码和验证码')
    return
  }
  loading.value = true
  try {
    // 登录前清理旧管理端状态，避免旧 Token 影响登录请求或路由判断
    adminStore.logout()
    if (remember.value) {
      localStorage.setItem('admin_remember_username', form.username)
    } else {
      localStorage.removeItem('admin_remember_username')
    }
    const res = await adminLogin(form)
    adminStore.loginSuccess(res.token, res.adminInfo)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // 登录失败（验证码错误/过期/账号密码错误）自动刷新验证码
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const saved = localStorage.getItem('admin_remember_username')
  if (saved) {
    form.username = saved
    remember.value = true
  }
  loadCaptcha()
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(120% 120% at 0% 0%, var(--brand-soft) 0%, transparent 55%),
    radial-gradient(120% 120% at 100% 100%, var(--accent-soft) 0%, transparent 55%),
    var(--paper);
}
.login-card {
  width: 380px;
  padding: 16px;
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-lg);
}
.brand {
  font-size: 20px;
  font-weight: 600;
  font-family: var(--font-display);
  text-align: center;
  margin-bottom: 24px;
  color: var(--brand);
}
.captcha-row {
  display: flex;
  width: 100%;
  gap: 10px;
  align-items: center;
}
.captcha-img {
  width: 110px;
  height: 40px;
  border: 1px solid var(--line, #dcdfe6);
  border-radius: 4px;
  cursor: pointer;
  flex-shrink: 0;
  background: #f5f7fa;
}
.captcha-math {
  width: 110px;
  height: 40px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line, #dcdfe6);
  border-radius: 4px;
  cursor: pointer;
  flex-shrink: 0;
  background:
    radial-gradient(circle at 12% 24%, rgba(22, 112, 83, .24) 0 1px, transparent 2px),
    radial-gradient(circle at 27% 76%, rgba(41, 166, 181, .25) 0 1px, transparent 2px),
    radial-gradient(circle at 77% 23%, rgba(218, 91, 124, .2) 0 1px, transparent 2px),
    radial-gradient(circle at 91% 69%, rgba(68, 160, 116, .25) 0 1.2px, transparent 2.2px),
    repeating-linear-gradient(164deg, transparent 0 18px, rgba(22, 112, 83, .055) 18px 19px, transparent 19px 36px),
    linear-gradient(120deg, #fcfffd 0%, #edf7f2 56%, #f3fafb 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .85);
  color: var(--brand, #0d5c3f);
  font-weight: 700;
  font-size: 16px;
  letter-spacing: 0.02em;
  user-select: none;
  transition: border-color .18s ease, box-shadow .18s ease;
}
.captcha-math:hover { border-color: var(--brand, #0d5c3f); box-shadow: 0 3px 10px rgba(22, 112, 83, .1), inset 0 1px 0 rgba(255, 255, 255, .85); }
.login-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: var(--fs-sm);
}
.login-row .forgot {
  color: var(--brand-strong);
  font-weight: 600;
  cursor: pointer;
}
.submit {
  width: 100%;
}
.tip {
  margin-top: 14px;
  text-align: center;
  font-size: 12px;
  color: var(--ink-3);
}
</style>
