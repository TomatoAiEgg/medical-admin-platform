<script lang="ts" setup>
import { ref, watch } from 'vue';
import AccountPassword from './components/FormLogin/AccountPassword.vue';

const visible = defineModel<boolean>('visible');
const showMask = ref(false);

watch(
  visible,
  (newVal) => {
    if (newVal)
      showMask.value = true;
  },
  { immediate: true },
);

function onAfterLeave() {
  if (!visible.value)
    showMask.value = false;
}
</script>

<template>
  <Teleport to="body">
    <div v-show="showMask" class="mask">
      <Transition name="dialog-zoom" @after-leave="onAfterLeave">
        <div v-show="visible" class="medical-login-dialog">
          <div class="medical-login-dialog__header">
            <span class="medical-login-dialog__logo">
              <el-icon><FirstAidKit /></el-icon>
            </span>
            <h2>医疗运营后台</h2>
            <p>请使用管理员账号登录</p>
          </div>

          <AccountPassword />
        </div>
      </Transition>
    </div>
  </Teleport>
</template>

<style scoped lang="scss">
.dialog-zoom-enter-active,
.dialog-zoom-leave-active {
  transform-origin: center;
  transition: all 0.2s ease-in-out;
}

.dialog-zoom-enter-from,
.dialog-zoom-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}

.dialog-zoom-enter-to,
.dialog-zoom-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
  user-select: none;
  background: rgb(15 23 42 / 42%);
}

.medical-login-dialog {
  z-index: 1000;
  box-sizing: border-box;
  width: 420px;
  max-width: 100%;
  padding: 32px 36px 30px;
  background: #ffffff;
  border: 1px solid var(--medical-border, #d9dade);
  border-radius: 8px;
  box-shadow: 0 16px 48px rgb(0 0 0 / 18%);
}

.medical-login-dialog__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
  text-align: center;
}

.medical-login-dialog__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-bottom: 14px;
  font-size: 22px;
  color: #ffffff;
  background: var(--medical-primary, #006767);
  border-radius: 8px;
}

.medical-login-dialog__header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  line-height: 30px;
  color: var(--medical-text, #191c1f);
}

.medical-login-dialog__header p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--medical-text-muted, #6d7979);
}
</style>
