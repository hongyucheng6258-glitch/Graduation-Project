<template>
  <AuthCampusShell compact-text="你好呀，新同学。">
    <template #story>
      <p class="hello">你好呀，新同学。</p>
      <h2>从今天起，<br>校园生活有更多可能。</h2>
      <p class="auth-story-more">报名喜欢的活动、交换闲置好物、<br>和 AI 一起整理错题。</p>
    </template>

    <div class="register-card">
      <div class="auth-form-head">
        <p>欢迎加入</p>
        <h1>注册梧桐校园</h1>
        <span class="muted">填写学号与昵称即可注册（毕设演示不做真认证）</span>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="studentNo">
          <el-input v-model="form.studentNo" placeholder="学号（6-20位数字）" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称" :prefix-icon="Avatar" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码（6-32位）" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item prop="confirm">
          <el-input v-model="form.confirm" type="password" placeholder="确认密码" show-password :prefix-icon="Lock" />
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
        <el-button type="primary" class="submit" :loading="loading" @click="submit">注 册</el-button>
      </el-form>
      <div class="links">
        已有账号？<router-link to="/login">去登录</router-link>
      </div>
    </div>
  </AuthCampusShell>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar } from '@element-plus/icons-vue'
import AuthCampusShell from '../../components/auth/AuthCampusShell.vue'
import { register, getCaptcha } from '../../api/auth'
import { useUserStore } from '../../store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const captchaImage = ref('')
const captchaMode = ref('image')
const captchaExpression = ref('')
const form = reactive({ studentNo: '', nickname: '', password: '', confirm: '', captchaId: '', captchaCode: '' })

const rules = {
  studentNo: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^\d{6,20}$/, message: '学号为6-20位数字', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度6-32位', trigger: 'blur' }
  ],
  confirm: [
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
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
    const res = await register({
      studentNo: form.studentNo,
      nickname: form.nickname,
      password: form.password,
      captchaId: form.captchaId,
      captchaCode: form.captchaCode
    })
    userStore.loginSuccess(res.token, res.userInfo)
    ElMessage.success('注册成功，已自动登录')
    router.push('/')
  } catch (e) {
    // 注册失败（验证码错误/过期/学号重复）自动刷新验证码
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
</script>

<style scoped>
.register-card { width: 100%; max-width: 400px; }
.auth-form-head { margin-bottom: 27px; }
.auth-form-head p { color: var(--ink-3); font-size: 13px; }
.auth-form-head h1 { font-size: 25px; font-weight: 700; margin: 7px 0 5px; color: var(--ink); letter-spacing: -.4px; }
.auth-form-head .muted { color: var(--ink-3); font-size: 12px; line-height: 1.7; }
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
  font-size: 16px;
  letter-spacing: 0.02em;
  user-select: none;
  transition: border-color .18s ease, box-shadow .18s ease;
}
.captcha-math:hover { border-color: var(--brand-line); box-shadow: 0 3px 10px rgba(49, 91, 184, .1), inset 0 1px 0 rgba(255, 255, 255, .8); }
.submit { width: 100%; height: 44px; margin-top: 8px; border-radius: var(--r-pill); font-weight: 600; }
.links { margin-top: var(--s-5); text-align: center; font-size: var(--fs-sm); color: var(--ink-3); }
.links a { color: var(--brand-strong); font-weight: 600; text-decoration: none; }

.register-card :deep(.el-input__wrapper) { border-radius: var(--r-md); }
.register-card :deep(.el-button--primary) {
  --el-button-bg-color: var(--brand);
  --el-button-border-color: var(--brand);
  --el-button-hover-bg-color: var(--brand-strong);
  --el-button-hover-border-color: var(--brand-strong);
  --el-button-active-bg-color: var(--brand-strong);
  --el-button-active-border-color: var(--brand-strong);
}

</style>
