<template>
  <div class="ident-bar">
    <span class="ident-label">当前身份</span>
    <el-radio-group v-model="identity.role" size="small" @change="persistIdentity">
      <el-radio-button v-for="r in ROLE_OPTIONS" :key="r.value" :value="r.value">{{ r.label }}</el-radio-button>
    </el-radio-group>
    <el-input
      v-model="identity.operator"
      size="small"
      placeholder="操作人姓名"
      class="ident-name"
      maxlength="32"
      @change="persistIdentity"
    />
    <el-tag size="small" :type="tagType" effect="plain">{{ roleName(identity.role) }}</el-tag>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ROLE_OPTIONS, identity, persistIdentity, roleName } from './identity'

const tagType = computed(() => {
  if (identity.role === 'MANAGER') return 'warning'
  if (identity.role === 'REFEREE') return 'success'
  return 'info'
})
</script>

<style scoped>
.ident-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.ident-label {
  font-size: 12px;
  color: var(--ink-mute);
}

.ident-name {
  width: 150px;
}
</style>
