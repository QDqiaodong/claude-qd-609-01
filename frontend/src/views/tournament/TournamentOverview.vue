<template>
  <div>
    <!-- ============ 列表态：赛事列表 + 建立赛事 ============ -->
    <template v-if="!embedded">
      <div class="panel">
        <div class="panel-title">建立赛事 <small>值班经理：4 或 8 支队 · 每队三名现有会员 · 设置每场局数</small></div>
        <el-form label-width="92px" class="create-form" @submit.prevent>
          <el-form-item label="赛事名称">
            <el-input v-model="createForm.name" placeholder="如：秋季馆内团体淘汰赛" maxlength="64" style="width: 260px" />
          </el-form-item>
          <el-form-item label="参赛队数">
            <el-radio-group v-model="createForm.teamSize">
              <el-radio-button :value="4">4 支队（2 轮）</el-radio-button>
              <el-radio-button :value="8">8 支队（3 轮）</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="每场局数">
            <el-input-number v-model="createForm.gamesPerMatch" :min="1" :max="12" />
          </el-form-item>
          <el-form-item label="每人每局箭数">
            <el-input-number v-model="createForm.arrowsPerMember" :min="1" :max="6" />
            <span class="muted" style="margin-left: 10px">一局须两队 6 名队员的规定箭数全部录齐才能确认</span>
          </el-form-item>
          <el-form-item v-if="identity.role !== 'MANAGER'" label=" ">
            <el-alert type="warning" :closable="false" show-icon title="当前身份不是值班经理，建立赛事会被服务端拒绝（无权操作）" />
          </el-form-item>
          <el-form-item label=" ">
            <el-button type="primary" @click="createTournament">建立赛事</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="panel">
        <div class="panel-title">赛事列表</div>
        <el-table :data="list" stripe>
          <el-table-column prop="tournamentNo" label="编号" width="150" />
          <el-table-column prop="name" label="赛事" min-width="150" />
          <el-table-column label="规模" width="110">
            <template #default="{ row }">{{ row.teamSize }} 队 · {{ row.totalRounds }} 轮</template>
          </el-table-column>
          <el-table-column label="赛制" width="130">
            <template #default="{ row }">{{ row.gamesPerMatch }} 局/场 · {{ row.arrowsPerMember }} 箭/人局</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag size="small" :type="statusTag(row.status)" effect="plain">{{ row.statusName }}</el-tag></template>
          </el-table-column>
          <el-table-column label="场次（待/中/完）" width="130">
            <template #default="{ row }">{{ row.pendingCount }} / {{ row.liveCount }} / {{ row.finishedCount }}</template>
          </el-table-column>
          <el-table-column label="加赛场" width="80">
            <template #default="{ row }">{{ row.playoffMatchCount }}</template>
          </el-table-column>
          <el-table-column label="冠军" width="100">
            <template #default="{ row }">{{ row.championTeamName || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="$emit('open', row.id)">进入</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- ============ 详情态：赛事总览 ============ -->
    <template v-else>
      <div v-if="detail" class="overview">
        <div class="panel">
          <div class="panel-title">
            赛事信息
            <el-tag size="small" :type="statusTag(detail.status)" effect="dark">{{ detail.statusName }}</el-tag>
            <small>{{ detail.tournamentNo }} · {{ detail.teamSize }} 队 · 每场 {{ detail.gamesPerMatch }} 局 · 每人每局 {{ detail.arrowsPerMember }} 支</small>
          </div>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="当前轮次">第 {{ detail.currentRound }} / {{ detail.totalRounds }} 轮</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ detail.createdBy }}</el-descriptions-item>
            <el-descriptions-item label="开赛时间">{{ shortTime(detail.startedAt) || '—' }}</el-descriptions-item>
            <el-descriptions-item label="冠军">{{ detail.championTeamName || '待定' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 参赛队管理（仅筹备期 + 经理） -->
        <div class="panel">
          <div class="panel-title">
            参赛队（{{ detail.teams.length }}/{{ detail.teamSize }}）
            <small v-if="detail.status === 'SETUP'">筹备期可建立/替换；开赛后冻结</small>
            <small v-else class="frozen">赛事已开始，队伍与对阵关系已锁定，不能替换</small>
          </div>

          <el-table :data="teamRows" border size="small">
            <el-table-column prop="slotNo" label="签位" width="70" align="center" />
            <el-table-column prop="teamName" label="队名" width="140" />
            <el-table-column label="三名队员（会员卡号 · 姓名）" min-width="320">
              <template #default="{ row }">
                <el-tag v-for="m in row.members" :key="m.memberId" size="small" effect="plain" class="member-tag">
                  {{ m.seatNo }}. {{ m.name }} · {{ m.cardNo }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="detail.status === 'SETUP' && identity.role === 'MANAGER'" label="操作" width="150">
              <template #default="{ row }">
                <el-button link type="primary" @click="editTeam(row)">替换</el-button>
                <el-button link type="danger" @click="removeTeam(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="detail.status === 'SETUP'" class="team-edit">
            <el-divider content-position="left">{{ editingSlot ? `替换签位 ${editingSlot}` : '建立 / 编辑队伍' }}</el-divider>
            <div class="team-form">
              <span class="muted">签位</span>
              <el-input-number v-model="teamForm.slotNo" :min="1" :max="detail.teamSize" size="small" />
              <el-input v-model="teamForm.teamName" placeholder="队名" size="small" style="width: 150px" maxlength="48" />
              <template v-for="seat in 3" :key="seat">
                <el-select v-model="teamForm.memberIds[seat - 1]" :placeholder="`第 ${seat} 名队员`" size="small" filterable style="width: 200px">
                  <el-option v-for="m in availableMembers(seat - 1)" :key="m.id" :label="`${m.name} · ${m.cardNo}`" :value="m.id" />
                </el-select>
              </template>
              <el-button type="primary" size="small" @click="saveTeam">保存队伍</el-button>
            </div>
            <div v-if="identity.role !== 'MANAGER'" class="muted" style="margin-top: 8px">
              仅值班经理可管理参赛队；当前为{{ roleName(identity.role) }}，保存会被拒绝（无权操作）。
            </div>
          </div>

          <div class="start-line" v-if="detail.status === 'SETUP'">
            <el-button type="success" :disabled="identity.role !== 'MANAGER'" @click="startTournament">
              开赛并锁定队伍 / 对阵
            </el-button>
            <span class="muted" v-if="identity.role !== 'MANAGER'">（仅值班经理可开赛）</span>
            <span class="muted" v-else>开赛后将按签位生成首轮对阵，后续轮次等待自动晋级</span>
          </div>
        </div>

        <!-- 每场状态 / 局分 / 加赛 / 胜者 -->
        <div class="panel">
          <div class="panel-title">场次总览 <small>局分、加赛轮次、胜者全部来自服务端</small></div>
          <el-table :data="detail.matches" border size="small">
            <el-table-column label="场次" width="90">
              <template #default="{ row }">R{{ row.roundNo }}-M{{ row.slotNo - row.roundNo * 100 }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag size="small" :type="matchTag(row.status)" effect="plain">{{ row.statusName }}</el-tag></template>
            </el-table-column>
            <el-table-column label="A 队" min-width="150">
              <template #default="{ row }">
                <span v-if="row.teamA">{{ row.teamA.teamName }}</span>
                <span v-else class="muted">等待 {{ sourceText(row.sourceA) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="B 队" min-width="150">
              <template #default="{ row }">
                <span v-if="row.teamB">{{ row.teamB.teamName }}</span>
                <span v-else class="muted">等待 {{ sourceText(row.sourceB) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="正规局分 (X)" width="130">
              <template #default="{ row }">{{ row.regScoreA }} ({{ row.regXA }}) : {{ row.regScoreB }} ({{ row.regXB }})</template>
            </el-table-column>
            <el-table-column label="加赛" width="80">
              <template #default="{ row }">{{ row.playoffRoundCount ? row.playoffRoundCount + ' 轮' : '—' }}</template>
            </el-table-column>
            <el-table-column label="胜者 / 方式" width="150">
              <template #default="{ row }">
                <template v-if="row.winnerTeamName">
                  <el-tag size="small" type="success" effect="dark">{{ row.winnerTeamName }}</el-tag>
                  <span class="muted"> {{ row.winModeName }}</span>
                </template>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 完整操作轨迹 -->
        <div class="panel">
          <div class="panel-title">完整操作轨迹 <small>撤回人、时间、原因与自动晋级全程留痕</small></div>
          <el-timeline class="trail">
            <el-timeline-item
              v-for="l in detail.logs"
              :key="l.id"
              :timestamp="String(l.createdAt).replace('T', ' ')"
              :type="logType(l.action)"
              placement="top"
            >
              <div class="log-line">
                <el-tag size="small" effect="plain" :type="roleTag(l.role)">{{ l.roleName }}</el-tag>
                <b>{{ l.operator }}</b>
                <span>{{ l.actionName }}</span>
                <span v-if="l.matchSlotNo" class="muted">场次 {{ l.matchSlotNo }}</span>
              </div>
              <div class="log-detail muted">{{ l.detail }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tournamentApi, shortTime } from '../../api'
import { authPayload, identity, roleName } from './identity'

const props = defineProps({
  tournamentId: { type: Number, default: null },
  embedded: { type: Boolean, default: false }
})
const emit = defineEmits(['open', 'changed'])

const list = ref([])
const detail = ref(null)
const members = ref([])
const createForm = reactive({ name: '', teamSize: 4, gamesPerMatch: 3, arrowsPerMember: 2 })
const teamForm = reactive({ slotNo: 1, teamName: '', memberIds: [null, null, null] })
const editingSlot = ref(null)

const teamRows = computed(() => {
  const rows = []
  if (!detail.value) return rows
  const bySlot = new Map(detail.value.teams.map((t) => [t.slotNo, t]))
  for (let s = 1; s <= detail.value.teamSize; s += 1) {
    rows.push(bySlot.get(s) || { slotNo: s, teamName: '', members: [] })
  }
  return rows
})

function availableMembers(seatIndex) {
  if (!detail.value) return members.value
  const used = new Set()
  teamForm.memberIds.forEach((id, i) => {
    if (i !== seatIndex && id != null) used.add(id)
  })
  // 同赛事已被其他签位占用的会员也排除
  const editingTeam = detail.value.teams.find((t) => t.slotNo === teamForm.slotNo)
  detail.value.teams.forEach((t) => {
    if (editingTeam && t.slotNo === editingTeam.slotNo) return
    t.members.forEach((m) => used.add(m.memberId))
  })
  return members.value.filter((m) => !used.has(m.id))
}

async function loadList() {
  list.value = await tournamentApi.list()
}

async function loadDetail() {
  detail.value = await tournamentApi.detail(props.tournamentId)
}

async function createTournament() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请填写赛事名称')
    return
  }
  if (!identity.operator) {
    ElMessage.warning('请先在右上角填写操作人姓名')
    return
  }
  try {
    const created = await tournamentApi.create(authPayload({ ...createForm }))
    ElMessage.success('赛事已建立')
    emit('open', created.id)
    emit('changed')
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function editTeam(row) {
  editingSlot.value = row.slotNo
  teamForm.slotNo = row.slotNo
  teamForm.teamName = row.teamName
  teamForm.memberIds = row.members.map((m) => m.memberId)
  if (teamForm.memberIds.length < 3) teamForm.memberIds = [null, null, null]
}

async function saveTeam() {
  if (!identity.operator) {
    ElMessage.warning('请先在右上角填写操作人姓名')
    return
  }
  if (!teamForm.teamName.trim() || teamForm.memberIds.some((m) => m == null)) {
    ElMessage.warning('请填写队名并选齐三名队员')
    return
  }
  await tournamentApi.upsertTeam(props.tournamentId, authPayload({
    slotNo: teamForm.slotNo,
    teamName: teamForm.teamName,
    memberIds: teamForm.memberIds
  }))
  ElMessage.success(editingSlot.value ? '队伍已替换' : '队伍已建立')
  editingSlot.value = null
  teamForm.teamName = ''
  teamForm.memberIds = [null, null, null]
  await loadDetail()
}

async function removeTeam(row) {
  try {
    await ElMessageBox.confirm(`确认删除签位 ${row.slotNo} 队伍「${row.teamName}」？`, '删除参赛队', { type: 'warning' })
  } catch (e) {
    return
  }
  await tournamentApi.removeTeam(props.tournamentId, row.slotNo, authPayload())
  ElMessage.success('队伍已删除')
  await loadDetail()
}

async function startTournament() {
  try {
    await ElMessageBox.confirm('开赛后队伍与对阵关系将锁定，不能再替换。确认开赛？', '开赛确认', { type: 'warning' })
  } catch (e) {
    return
  }
  await tournamentApi.start(props.tournamentId, authPayload())
  ElMessage.success('赛事已开始')
  await loadDetail()
}

function sourceText(source) {
  if (!source) return '晋级队'
  return `R${Math.floor(source.slotNo / 100)}-M${source.slotNo % 100} ${source.winnerTeamName ? `胜者（${source.winnerTeamName}）` : '胜者'}`
}

function statusTag(s) {
  if (s === 'FINISHED') return 'success'
  if (s === 'ONGOING') return 'warning'
  return 'info'
}
function matchTag(s) {
  if (s === 'FINISHED') return 'success'
  if (s === 'LIVE') return 'danger'
  return 'info'
}
function logType(a) {
  if (a === 'WITHDRAW_GAME') return 'warning'
  if (a === 'CHAMPION') return 'success'
  if (a === 'ADVANCE') return 'primary'
  return ''
}
function roleTag(r) {
  if (r === 'MANAGER') return 'warning'
  if (r === 'REFEREE') return 'success'
  if (r === 'SYSTEM') return 'primary'
  return 'info'
}

onMounted(async () => {
  members.value = await tournamentApi.memberOptions()
  if (props.embedded) await loadDetail()
  else await loadList()
})

watch(() => props.tournamentId, async (v) => {
  if (v) await loadDetail()
  else await loadList()
})
</script>

<style scoped>
.create-form {
  max-width: 760px;
}
.team-edit {
  margin-top: 12px;
}
.team-form {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.member-tag {
  margin: 2px 4px 2px 0;
}
.frozen {
  color: #b3261e;
}
.start-line {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.wait-link {
  font-size: 12px;
}
.trail {
  margin-top: 6px;
  padding-left: 4px;
}
.log-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.log-detail {
  margin-top: 2px;
  line-height: 1.6;
}
.overview {
  display: flex;
  flex-direction: column;
}
</style>
