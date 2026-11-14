<template>
  <div class="recovery-page">
    <header class="recovery-header">
      <RouterLink class="brand-link" to="/" aria-label="梧桐校园首页">
        <span class="brand-mark" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
        梧桐校园
      </RouterLink>
      <RouterLink class="back-link" to="/login"><span aria-hidden="true">←</span> 返回登录</RouterLink>
    </header>
    <main class="recovery">
      <aside class="recovery-guide" aria-labelledby="recovery-guide-title">
        <div class="guide-content">
          <h2 id="recovery-guide-title">人工找回流程</h2>
          <ol>
            <li><b>提交申请</b><small>填写学号与可联系的方式。</small></li>
            <li><b>等待审核</b><small>管理员核实身份后，凭回执查看结果。</small></li>
            <li><b>设置新密码</b><small>通过后领取临时密码，登录并改密。</small></li>
          </ol>
        </div>
      </aside>
      <section class="recovery-panel" aria-labelledby="recovery-title">
        <div class="panel-heading">
          <h1 id="recovery-title">找回密码</h1>
          <p>提交找回申请，或查询已有申请的处理进度。</p>
        </div>
      <el-tabs v-model="tab">
        <el-tab-pane v-if="phoneEnabled" label="手机号找回" name="phone" lazy>
          <p>使用已验证的登录手机号设置新密码。</p>
          <PhoneAccountForm purpose="reset" @done="resetDone" />
        </el-tab-pane>
        <el-tab-pane label="管理员审核" name="apply" lazy>
          <p>填写账号信息。管理员核实身份后，可在「查询进度」领取临时密码。</p>
          <el-form label-position="top" @submit.prevent="apply">
            <div class="identity-fields">
            <el-form-item label="学号"><el-input v-model.trim="form.studentNo" maxlength="32" required /></el-form-item>
            <el-form-item label="姓名"><el-input v-model.trim="form.name" maxlength="64" required /></el-form-item>
            </div>
            <el-form-item label="可联系的手机号或邮箱"><el-input v-model.trim="form.contact" maxlength="80" required /></el-form-item>
            <el-form-item label="找回说明"><el-input v-model.trim="form.explanation" type="textarea" maxlength="500" required /></el-form-item>
            <el-form-item label="人机验证码">
              <div class="captcha-line">
                <el-input v-model.trim="form.captchaCode" required autocomplete="off" />
                <button type="button" aria-label="刷新人机验证码" @click="refreshCaptcha">
                  <img v-if="captcha.image" :src="captcha.image" alt="图形验证码" />
                  <span v-else>{{ captcha.expression || '加载验证码' }}</span>
                </button>
              </div>
            </el-form-item>
            <el-button type="primary" native-type="submit" :loading="busy">提交审核</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="查询进度" name="status">
          <el-form label-position="top" @submit.prevent="query">
            <el-form-item label="申请回执">
              <el-input v-model.trim="receipt" maxlength="72" autocomplete="off" />
            </el-form-item>
            <p>请妥善保存回执。持有回执的人可以领取审核通过后的临时密码。</p>
            <el-button native-type="submit" :loading="busy">查询进度</el-button>
          </el-form>
          <div v-if="result" class="result" :data-status="result.status" role="status">
            <strong>{{ statusLabel[result.status] || result.status }}</strong>
            <p v-if="result.reason">原因：{{ result.reason }}</p>
          </div>
          <el-button v-if="result?.status === 'APPROVED' && !password" type="primary" :loading="busy" @click="claim">领取临时密码</el-button>
          <div v-if="password" class="secret" role="status">
            <p>临时密码仅显示一次，24 小时内有效。登录后请设置新密码。</p>
            <strong>{{ password }}</strong>
          </div>
        </el-tab-pane>
      </el-tabs>
      </section>
    </main>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PhoneAccountForm from './PhoneAccountForm.vue'
import { getCaptcha } from '../../api/auth'
import { applyRecovery, claimPassword, phoneOptions, recoveryStatus } from '../../api/accountRecovery'

const router = useRouter()
const phoneEnabled = ref(false)
const receipt = ref(sessionStorage.getItem('recoveryReceipt') || '')
const tab = ref(receipt.value ? 'status' : 'apply')
const result = ref(null)
const password = ref('')
const busy = ref(false)
const captcha = ref({})
const form = reactive({ studentNo: '', name: '', contact: '', explanation: '', captchaId: '', captchaCode: '' })
const statusLabel = { PENDING: '等待管理员审核', APPROVED: '审核通过，可领取临时密码', REJECTED: '审核未通过', CLAIMED: '临时密码已领取' }

async function refreshCaptcha() {
  captcha.value = await getCaptcha()
  form.captchaId = captcha.value.captchaId
  form.captchaCode = ''
}

watch(receipt, () => {
  result.value = null
  password.value = ''
})

async function apply() {
  if (busy.value) return
  busy.value = true
  try {
    receipt.value = (await applyRecovery(form)).receipt
    sessionStorage.setItem('recoveryReceipt', receipt.value)
    await nextTick()
    result.value = { status: 'PENDING' }
    tab.value = 'status'
    ElMessage.success('申请已提交，请保存回执')
  } catch {
    await refreshCaptcha()
  } finally {
    busy.value = false
  }
}

async function query() {
  if (busy.value || !receipt.value) return
  busy.value = true
  try {
    result.value = await recoveryStatus(receipt.value)
  } finally {
    busy.value = false
  }
}

async function claim() {
  if (busy.value) return
  busy.value = true
  try {
    password.value = (await claimPassword(receipt.value)).temporaryPassword
  } finally {
    busy.value = false
  }
}

async function resetDone() {
  ElMessage.success('密码已更新，请重新登录')
  await router.replace('/login')
}

onMounted(() => {
  refreshCaptcha().catch(() => {})
  phoneOptions().then(options => { phoneEnabled.value = options.enabled }).catch(() => {})
})
</script>

<style scoped>
.recovery-page {
  min-height: 100svh;
  background: radial-gradient(ellipse at 15% 0%, var(--brand-soft), transparent 55%), var(--paper);
  padding: 0 clamp(16px, 5vw, 64px) 48px;
}
.recovery-header {
  max-width: 1056px;
  margin: auto;
  min-height: 80px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.brand-link, .back-link { display: inline-flex; align-items: center; gap: 9px; font-weight: 650; }
.brand-link { color: var(--ink); }
.brand-mark { display: grid; grid-template-columns: repeat(2, 5px); gap: 2px; background: var(--brand); padding: 8px; border-radius: 8px; }
.brand-mark i { height: 5px; background: white; border-radius: 1px; }
.brand-mark i:nth-child(even) { opacity: .62; }
.back-link { color: var(--brand-strong); padding: 8px 12px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }
.recovery {
  display: grid;
  grid-template-columns: minmax(230px, .78fr) minmax(0, 1.32fr);
  max-width: 1056px;
  margin: clamp(12px, 4vw, 48px) auto 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 22px;
  overflow: hidden;
  box-shadow: 0 20px 52px rgba(25, 54, 112, .12);
}
.recovery-guide {
  display: flex;
  align-items: flex-end;
  padding: clamp(24px, 3.6vw, 44px);
  color: #fff;
  background: linear-gradient(180deg, rgba(16, 42, 86, .24), #10284f 85%), url('/images/campus-v2.webp') center / cover;
}
.guide-content { width: 100%; }
.guide-content h2 { font-size: 25px; margin: 0 0 24px; }
.guide-content ol { list-style: none; padding: 0; margin: 0; display: grid; gap: 18px; }
.guide-content li { padding-inline-start: 15px; border-inline-start: 2px solid #9cb7ff; }
.guide-content b, .guide-content small { display: block; }
.guide-content small { color: #e0e9ff; margin-top: 4px; line-height: 1.55; }
.recovery-panel { min-width: 0; padding: clamp(22px, 4vw, 48px); }
.panel-heading h1 { font-size: clamp(26px, 4vw, 38px); line-height: 1.2; }
.panel-heading { margin-bottom: 18px; }
p { margin: 10px 0; color: var(--ink-2); line-height: 1.6; }
.identity-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.recovery-panel :deep(.el-input__wrapper) { min-height: 40px; }
.recovery-panel :deep(.el-tabs__item) { padding-inline: clamp(8px, 1.35vw, 20px); }
.captcha-line { display: flex; align-items: stretch; width: 100%; gap: 8px; }
.captcha-line .el-input { min-width: 0; }
.captcha-line button { min-width: 105px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface-2); color: var(--brand-strong); cursor: pointer; }
.captcha-line img { display: block; width: 105px; border-radius: 7px; }
.secret, .result { margin: 14px 0; padding: 14px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface-2); overflow-wrap: anywhere; }
.result[data-status='APPROVED'], .secret { border-color: var(--brand-line); background: var(--brand-soft); }
.result[data-status='REJECTED'] { border-inline-start: 3px solid var(--error); }
.result p, .secret p { margin: 4px 0 0; }
a:focus-visible, button:focus-visible { outline: 2px solid var(--brand); outline-offset: 3px; }
@media (max-width: 720px) {
  .recovery-header { min-height: 62px; }
  .recovery { grid-template-columns: minmax(0, 1fr); border-radius: 16px; }
  .recovery-guide { padding: 16px 22px; order: 1; }
  .guide-content h2 { font-size: 16px; margin-bottom: 10px; }
  .guide-content ol { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
  .guide-content li { padding-inline-start: 6px; font-size: 12px; }
  .guide-content small { display: none; }
}
</style>
