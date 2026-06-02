<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus';
import type { LoginDTO } from '@/api/auth/types';
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getCaptchaCode, login } from '@/api';
import { useUserStore } from '@/stores';

const userStore = useUserStore();
const router = useRouter();

const formRef = ref<FormInstance>();
const submitting = ref(false);
const captchaLoading = ref(false);
const captchaEnabled = ref(true);
const captchaImage = ref('');

const formModel = reactive<LoginDTO>({
  username: '',
  password: '',
  code: '',
  clientId: import.meta.env.VITE_CLIENT_ID,
  grantType: 'password',
  tenantId: '000000',
  uuid: '',
});

const rules = reactive<FormRules<LoginDTO>>({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [
    {
      validator: (_rule, value, callback) => {
        if (captchaEnabled.value && !value)
          callback(new Error('请输入验证码'));
        else
          callback();
      },
      trigger: 'blur',
    },
  ],
});

function normalizeCaptchaImage(img?: string) {
  if (!img)
    return '';
  if (img.startsWith('data:image'))
    return img;
  return `data:image/png;base64,${img}`;
}

async function loadCaptcha() {
  if (captchaLoading.value)
    return;

  captchaLoading.value = true;
  try {
    const res = await getCaptchaCode();
    const data = res.data;
    captchaEnabled.value = data?.captchaEnabled !== false;
    formModel.uuid = data?.uuid ?? '';
    formModel.code = '';
    captchaImage.value = normalizeCaptchaImage(data?.img);
  }
  catch (error) {
    captchaEnabled.value = false;
    captchaImage.value = '';
    formModel.uuid = '';
    console.error('验证码获取失败:', error);
  }
  finally {
    captchaLoading.value = false;
  }
}

async function handleSubmit() {
  if (submitting.value)
    return;

  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid)
    return;

  submitting.value = true;
  try {
    const res = await login(formModel);
    if (res.data.access_token)
      userStore.setToken(res.data.access_token);
    ElMessage.success('登录成功');
    userStore.closeLoginDialog();
    router.replace('/');
  }
  catch (error) {
    if (captchaEnabled.value)
      await loadCaptcha();
    console.error('请求错误:', error);
  }
  finally {
    submitting.value = false;
  }
}

onMounted(() => {
  loadCaptcha();
});
</script>

<template>
  <el-form
    ref="formRef"
    :model="formModel"
    :rules="rules"
    class="login-form"
    label-position="top"
    @submit.prevent="handleSubmit"
  >
    <el-form-item prop="username" label="用户名">
      <el-input v-model="formModel.username" placeholder="请输入用户名" size="large" autocomplete="username">
        <template #prefix>
          <el-icon><User /></el-icon>
        </template>
      </el-input>
    </el-form-item>

    <el-form-item prop="password" label="密码">
      <el-input
        v-model="formModel.password"
        placeholder="请输入密码"
        type="password"
        size="large"
        show-password
        autocomplete="current-password"
        @keyup.enter="handleSubmit"
      >
        <template #prefix>
          <el-icon><Lock /></el-icon>
        </template>
      </el-input>
    </el-form-item>

    <el-form-item v-if="captchaEnabled" prop="code" label="验证码">
      <div class="captcha-row">
        <el-input
          v-model.trim="formModel.code"
          placeholder="请输入验证码"
          size="large"
          autocomplete="off"
          @keyup.enter="handleSubmit"
        >
          <template #prefix>
            <el-icon><Key /></el-icon>
          </template>
        </el-input>
        <button class="captcha-image" type="button" :disabled="captchaLoading" title="点击刷新验证码" @click="loadCaptcha">
          <img v-if="captchaImage" :src="captchaImage" alt="验证码">
          <span v-else>{{ captchaLoading ? '加载中' : '刷新' }}</span>
        </button>
      </div>
    </el-form-item>

    <el-button class="login-submit" type="primary" native-type="submit" size="large" :loading="submitting" :disabled="submitting">
      登录
    </el-button>

    <p class="login-tip">
      如需账号权限，请联系系统管理员。
    </p>
  </el-form>
</template>

<style scoped lang="scss">
.login-form {
  width: 100%;
}

.captcha-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 124px;
  gap: 10px;
  width: 100%;
}

.captcha-image {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  padding: 0;
  overflow: hidden;
  color: var(--medical-text-muted, #6d7979);
  cursor: pointer;
  background: #f5f8f8;
  border: 1px solid #d8e3e3;
  border-radius: 8px;

  &:disabled {
    cursor: wait;
    opacity: 0.72;
  }

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.login-submit {
  width: 100%;
  margin-top: 4px;
}

.login-tip {
  margin: 14px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--medical-text-muted, #6d7979);
  text-align: center;
}
</style>
