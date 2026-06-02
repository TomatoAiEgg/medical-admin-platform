<script setup lang="ts">
withDefaults(defineProps<{
  title: string;
  value: string | number;
  desc?: string;
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info';
  icon?: string;
}>(), {
  tone: 'primary',
});
</script>

<template>
  <div class="metric-card" :class="`metric-card--${tone}`">
    <div class="metric-card__main">
      <span class="metric-card__title">{{ title }}</span>
      <strong class="metric-card__value">{{ value }}</strong>
      <span v-if="desc" class="metric-card__desc">{{ desc }}</span>
    </div>
    <el-icon v-if="icon" class="metric-card__icon">
      <component :is="icon" />
    </el-icon>
  </div>
</template>

<style scoped lang="scss">
.metric-card {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 96px;
  padding: 14px 16px;
  background: var(--medical-surface);
  border: 1px solid var(--medical-border);
  border-left: 4px solid var(--medical-primary);
  border-radius: var(--medical-radius-lg);
}

.metric-card__title,
.metric-card__desc {
  display: block;
  font-size: 13px;
  color: var(--medical-text-muted);
}

.metric-card__value {
  display: block;
  margin-top: 8px;
  font-size: 26px;
  font-weight: 600;
  line-height: 32px;
  color: var(--medical-text);
}

.metric-card__desc {
  margin-top: 4px;
}

.metric-card__icon {
  width: 36px;
  height: 36px;
  font-size: 20px;
  color: var(--medical-primary);
  background: var(--medical-primary-soft);
  border-radius: 50%;
}

.metric-card--success {
  border-left-color: var(--medical-success);
}

.metric-card--success .metric-card__icon {
  color: var(--medical-success);
  background: var(--medical-success-soft);
}

.metric-card--warning {
  border-left-color: var(--medical-warning);
}

.metric-card--warning .metric-card__icon {
  color: var(--medical-warning);
  background: var(--medical-warning-soft);
}

.metric-card--danger {
  border-left-color: var(--medical-danger);
}

.metric-card--danger .metric-card__icon {
  color: var(--medical-danger);
  background: var(--medical-danger-soft);
}

.metric-card--info {
  border-left-color: var(--medical-info);
}

.metric-card--info .metric-card__icon {
  color: var(--medical-info);
  background: var(--medical-info-soft);
}
</style>
