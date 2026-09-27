import { reactive } from 'vue'

/**
 * 赛事计分台的「当前登录身份」：值班经理 / 裁判 / 普通会员 + 姓名。
 * 纯前端的身份选择只用于界面可用性；真正的权限收口在服务端，每个写接口都带 role+operator。
 */
const STORAGE_KEY = 'tournament-identity'

function load() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
  } catch (e) {
    return {}
  }
}

const saved = load()

export const identity = reactive({
  role: saved.role || 'MANAGER',
  operator: saved.operator || ''
})

export function persistIdentity() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ role: identity.role, operator: identity.operator }))
}

export function authPayload(extra = {}) {
  return { operator: identity.operator || '', role: identity.role, ...extra }
}

export const ROLE_OPTIONS = [
  { value: 'MANAGER', label: '值班经理' },
  { value: 'REFEREE', label: '裁判' },
  { value: 'MEMBER', label: '普通会员' }
]

export function roleName(role) {
  return ROLE_OPTIONS.find((r) => r.value === role)?.label || role
}
