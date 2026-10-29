<template>
  <AuthCampusShell compact-text="你好呀，梧桐校园见。">
    <template #story>
      <p class="hello">你好呀，梧桐校园见。</p>
      <h2>新朋友很多，<br>好的校园生活不止一种。</h2>
      <p class="auth-story-more">想参加活动、淘好物、找搭子？<br>这里都可以帮你</p>
    </template>

    <div class="login-card">
      <div class="auth-form-head">
        <p>欢迎回来</p>
        <h1>登录梧桐校园</h1>
        <span class="muted">学生端 · 学号密码登录</span>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="studentNo">
          <el-input v-model="form.studentNo" placeholder="请输入学号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode"
                      :placeholder="captchaMode === 'math' ? '输入运算结果' : '4位验证码'"
                      :maxlength="captchaMode === 'math' ? 3 : 4" @keyup.enter="submit" />
            <img v-if="captchaMode !== 'math'" class="captcha-img" :src="captchaImage"
                 title="看不清？点击刷新" @click="loadCaptcha" />
            <div v-else class="captcha-math" title="换一题" @click="loadCaptcha">{{ captchaExpression }}</div>
          </div>
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <div class="login-foot">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </AuthCampusShell>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import AuthCampusShell from '../../components/auth/AuthCampusShell.vue'
import { login, getCaptcha } from '../../api/auth'
import { useUserStore } from '../../store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const captchaImage = ref('')
const captchaMode = ref('image')
const captchaExpression = ref('')
const form = reactive({ studentNo: '', password: '', captchaId: '', captchaCode: '' })
const rules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function loadCaptcha() {
  const data = await getCaptcha()
  form.captchaId = data.captchaId
  captchaMode.value = data.mode || 'image'
  captchaImage.value = data.image
  captchaExpression.value = data.expression
  form.captchaCode = ''
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await login(form)
    userStore.loginSuccess(res.token, res.userInfo)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
  } catch (e) {
    // 登录失败（验证码错误/过期/账号密码错误）自动刷新验证码
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
</script>

<style scoped>
.login-card { width: 100%; max-width: 400px; }
.auth-form-head { margin-bottom: 27px; }
.auth-form-head p { color: var(--ink-3); font-size: 13px; }
.auth-form-head h1 { font-size: 25px; font-weight: 700; margin: 7px 0 5px; color: var(--ink); letter-spacing: -.4px; }
.auth-form-head .muted { color: var(--ink-3); font-size: 12px; }
.captcha-row { display: flex; width: 100%; gap: var(--s-3); align-items: center; }
.captcha-img {
  width: 120px;
  height: 44px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  cursor: pointer;
  flex-shrink: 0;
  background: var(--surface-3);
  object-fit: cover;
}
.captcha-math {
  width: 120px;
  height: 44px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  cursor: pointer;
  flex-shrink: 0;
  background:
    radial-gradient(circle at 12% 24%, rgba(49, 91, 184, .28) 0 1px, transparent 2px),
    radial-gradient(circle at 27% 76%, rgba(41, 166, 181, .28) 0 1px, transparent 2px),
    radial-gradient(circle at 77% 23%, rgba(218, 91, 124, .22) 0 1px, transparent 2px),
    radial-gradient(circle at 91% 69%, rgba(68, 160, 116, .26) 0 1.2px, transparent 2.2px),
    repeating-linear-gradient(164deg, transparent 0 18px, rgba(49, 91, 184, .055) 18px 19px, transparent 19px 36px),
    linear-gradient(120deg, #fbfcff 0%, #edf3ff 56%, #f3fbfc 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .8);
  color: var(--brand-strong);
  font-weight: 700;
  font-size: 17px;
  letter-spacing: 0.02em;
  user-select: none;
  transition: border-color .18s ease, box-shadow .18s ease;
}
.captcha-math:hover { border-color: var(--brand-line); box-shadow: 0 3px 10px rgba(49, 91, 184, .1), inset 0 1px 0 rgba(255, 255, 255, .8); }
.submit { width: 100%; height: 44px; margin-top: 8px; border-radius: var(--r-pill); font-weight: 600; }
.login-foot { text-align: center; margin-top: var(--s-5); font-size: var(--fs-sm); color: var(--ink-3); }
.login-foot a { color: var(--brand-strong); font-weight: 600; text-decoration: none; }

.login-card :deep(.el-input__wrapper) { border-radius: var(--r-md); }
.login-card :deep(.el-button--primary) {
  --el-button-bg-color: var(--brand);
  --el-button-border-color: var(--brand);
  --el-button-hover-bg-color: var(--brand-strong);
  --el-button-hover-border-color: var(--brand-strong);
  --el-button-active-bg-color: var(--brand-strong);
  --el-button-active-border-color: var(--brand-strong);
}

</style>
