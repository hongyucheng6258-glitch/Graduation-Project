<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="login-brand">
        <span class="login-brand-mark" aria-hidden="true">
          <svg viewBox="0 0 40 40" fill="currentColor" focusable="false">
            <path d="M4 5h13a3 3 0 0 1 3 3v12H8a4 4 0 0 1-4-4Z" />
            <path d="M23 4h13v12a4 4 0 0 1-4 4H23Z" opacity=".65" />
            <path d="M4 23h16v13H8a4 4 0 0 1-4-4Z" opacity=".65" />
            <path d="M23 23h13v13H23Z" opacity=".3" />
          </svg>
        </span>
        <span class="login-brand-name">梧桐校园<small>CAMPUS OPERATIONS</small></span>
      </div>
      <div class="login-heading">
        <h1>欢迎回来</h1>
        <p>登录管理后台，继续处理今日校园运营事务。</p>
      </div>
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
                      :maxlength="captchaMode === 'math' ? 3 : 4" />
            <button type="button" class="captcha-refresh" :class="captchaMode === 'math' ? 'captcha-math' : 'captcha-img'"
                    :aria-label="captchaMode === 'math' ? `当前题目：${captchaExpression}。更换验证码` : '刷新图片验证码'"
                    title="点击刷新验证码" @click="loadCaptcha" @keyup.enter.stop>
              <span v-if="captchaMode === 'math'">{{ captchaExpression }}</span>
              <img v-else-if="captchaImage" :src="captchaImage" alt="" />
            </button>
          </div>
        </el-form-item>
        <el-form-item>
          <div class="login-row">
            <el-checkbox v-model="remember">记住此设备</el-checkbox>
            <button type="button" class="forgot" @click="showRecovery = !showRecovery" @keyup.enter.stop
              :aria-expanded="showRecovery" aria-controls="admin-password-recovery">忘记密码？</button>
          </div>
        </el-form-item>
        <p v-show="showRecovery" id="admin-password-recovery" class="recovery-tip">
          请联系校园平台运维人员核验身份并重置管理员密码。
        </p>
        <el-button type="primary" class="submit" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <div class="tip">初始账号：admin / admin123（请登录后及时修改）</div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { adminLogin, getCaptcha } from '../../api/auth'
import { adminRedirect } from '../../utils/adminRedirect.mjs'
import { useAdminStore } from '../../store/admin'

const router = useRouter()
const route = useRoute()
const adminStore = useAdminStore()
const loading = ref(false)
const showRecovery = ref(false)
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
    router.replace(adminRedirect(router, route.query.redirect))
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
  width: min(430px, calc(100vw - 32px));
  padding: 14px;
  /* 用 token 派生描边：亮色是白边高光，暗色自动降为浅品牌色，不再出现亮白圈 */
  border: 1px solid color-mix(in srgb, var(--brand) 14%, var(--line));
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-lg);
}
.login-card :deep(.el-card__body) { padding: 28px; }
.login-page :deep(.el-input__wrapper) { min-height: 44px; }
.login-page :deep(.el-form-item) { margin-bottom: 18px; }
.login-brand { display: flex; align-items: center; gap: 11px; margin-bottom: 32px; }
.login-brand-mark {
  width: 40px; height: 40px; padding: 9px; border-radius: 12px;
  background: var(--brand-gradient); color: #fff;
  box-shadow: 0 10px 22px -10px color-mix(in srgb, var(--brand) 75%, transparent);
}
.login-brand-name { color: var(--ink); font-size: 17px; font-weight: 700; }
.login-brand-name small { display: block; color: var(--ink-3); font-size: 11px; font-weight: 500; letter-spacing: .12em; }
.login-heading { margin-bottom: 24px; }
.login-heading h1 { font-family: var(--font-display); font-size: 28px; line-height: 1.25; color: var(--ink); }
.login-heading p { margin-top: 8px; color: var(--ink-3); font-size: var(--fs-sm); }
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
.captcha-refresh { padding: 0; overflow: hidden; }
.captcha-refresh img { display: block; width: 100%; height: 100%; object-fit: contain; }
.captcha-refresh:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
.captcha-math:hover { border-color: var(--brand, #0d5c3f); box-shadow: 0 3px 10px rgba(22, 112, 83, .1), inset 0 1px 0 rgba(255, 255, 255, .85); }
.login-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: var(--fs-sm);
}
.recovery-tip { margin: 0 0 16px; font-size: var(--fs-sm); color: var(--ink-2); }
.login-row .forgot {
  background: none; border: 0; padding: 4px; font: inherit;
  color: var(--brand-strong);
  font-weight: 600;
  cursor: pointer;
}
.submit {
  width: 100%;
  min-height: 44px;
  margin-top: 2px;
  border: 0;
  border-radius: 11px;
  background: var(--brand-gradient);
  letter-spacing: .12em;
}
.submit:hover { background: var(--brand-gradient-hover); }
.tip {
  margin-top: 14px;
  text-align: center;
  font-size: 12px;
  color: var(--ink-3);
}
</style>
