<script setup lang="ts">
import { getExceptionStatusMeta, getRegistrationStatusMeta, getSeverityMeta } from '../_utils/status';

const props = withDefaults(defineProps<{
  value?: string;
  kind?: 'registration' | 'severity' | 'exception';
  showCode?: boolean;
}>(), {
  kind: 'registration',
  showCode: false,
});

const meta = computed(() => {
  if (props.kind === 'severity')
    return getSeverityMeta(props.value);
  if (props.kind === 'exception')
    return getExceptionStatusMeta(props.value);
  return getRegistrationStatusMeta(props.value);
});
</script>

<template>
  <el-tag :type="meta.type" effect="light" round>
    <span>{{ meta.label }}</span>
    <span v-if="showCode && value" class="status-code"> / {{ value }}</span>
  </el-tag>
</template>

<style scoped lang="scss">
.status-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  opacity: 0.72;
}
</style>
