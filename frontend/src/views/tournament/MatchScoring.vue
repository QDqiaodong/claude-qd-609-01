<template>
  <div class="score-console">
    <!-- 左：场次列表 -->
    <div class="panel match-list">
      <div class="panel-title">场次 <small>选择一场进入记分</small></div>
      <div
        v-for="m in matches"
        :key="m.id"
        class="match-row"
        :class="{ active: selectedId === m.id, live: m.status === 'LIVE' }"
        @click="select(m.id)"
      >
        <div class="match-row-top">
          <span class="mono match-no">R{{ m.roundNo }}-M{{ m.slotNo - m.roundNo * 100 }}</span>
          <el-tag size="small" :type="matchTag(m.status)" effect="plain">{{ m.statusName }}</el-tag>
        </div>
        <div class="match-row-teams">
          <span :class="{ win: m.winnerTeamId === m.teamAId }">{{ m.teamA ? m.teamA.teamName : '待定' }}</span>
          <em>{{ m.regScoreA }} : {{ m.regScoreB }}</em>
          <span :class="{ win: m.winnerTeamId === m.teamBId }">{{ m.teamB ? m.teamB.teamName : '待定' }}</span>
        </div>
      </div>
    </div>

    <!-- 右：单场计分区 -->
    <div class="score-main">
      <el-empty v-if="!match" description="从左侧选择一场比赛" :image-size="80" />

      <template v-else>
        <!-- 场次头 -->
        <div class="panel">
          <div class="panel-title">
            <span class="mono">R{{ match.roundNo }}-M{{ match.slotNo - match.roundNo * 100 }}</span>
            <el-tag :type="matchTag(match.status)" effect="dark">{{ match.statusName }}</el-tag>
            <small v-if="match.winnerTeamName">胜者：{{ match.winnerTeamName }}（{{ match.winModeName }}）</small>
          </div>

          <div class="teams-head">
            <div class="team-block" :class="{ champ: match.winnerTeamId === match.teamAId }">
              <div class="team-name">{{ match.teamA ? match.teamA.teamName : '待定' }}</div>
              <div class="team-members" v-if="match.teamA">
                <el-tag v-for="mem in match.teamA.members" :key="mem.memberId" size="small" effect="plain">
                  {{ mem.name }}
                </el-tag>
              </div>
              <div class="muted" v-else>等待 {{ sourceText(match.sourceA) }}</div>
            </div>
            <div class="vs-score">
              <b class="mono">{{ match.regScoreA }} : {{ match.regScoreB }}</b>
              <small>X {{ match.regXA }} : {{ match.regXB }}</small>
            </div>
            <div class="team-block right" :class="{ champ: match.winnerTeamId === match.teamBId }">
              <div class="team-name">{{ match.teamB ? match.teamB.teamName : '待定' }}</div>
              <div class="team-members" v-if="match.teamB">
                <el-tag v-for="mem in match.teamB.members" :key="mem.memberId" size="small" effect="plain">
                  {{ mem.name }}
                </el-tag>
              </div>
              <div class="muted" v-else>等待 {{ sourceText(match.sourceB) }}</div>
            </div>
          </div>

          <div class="actions">
            <el-button
              v-if="match.status === 'PENDING'"
              type="primary"
              :disabled="identity.role !== 'REFEREE' || !match.teamA || !match.teamB"
              @click="startMatch"
            >开赛场次</el-button>
            <el-alert
              v-if="match.status === 'PENDING' && (!match.teamA || !match.teamB)"
              type="info" :closable="false" show-icon
              :title="`双方队伍尚未齐：${missingText(match)}，等待前序场次晋级`"
              class="wait-alert"
            />
            <el-tag v-if="match.status === 'FINISHED'" type="success" effect="dark">比赛已确认，不能倒退或改分</el-tag>
            <el-tag v-if="match.status === 'LIVE'" type="warning" effect="plain">比赛进行中</el-tag>
            <span v-if="identity.role === 'MEMBER'" class="muted">普通会员仅可查看，记分与确认请切换到裁判身份</span>
            <span v-else-if="identity.role === 'MANAGER' && match.status !== 'FINISHED'" class="muted">值班经理只读，裁判可推进比赛</span>
          </div>
        </div>

        <!-- 局记分（LIVE 时） -->
        <div class="panel" v-if="match.status === 'LIVE'">
          <div class="panel-title">
            局记分
            <small>规定 {{ match.gamesPerMatch }} 局 · 每局每人 {{ match.arrowsPerMember }} 支 · 口径 X / 10~1 / M</small>
          </div>

          <!-- 已录各局一览 -->
          <div class="games-strip">
            <div
              v-for="g in match.games"
              :key="g.id"
              class="game-chip"
              :class="{ confirmed: g.status === 'CONFIRMED' }"
              @click="pickGame(g)"
            >
              <span>第 {{ g.gameNo }} 局</span>
              <b class="mono">{{ g.scoreA }} : {{ g.scoreB }}</b>
              <el-tag size="small" :type="g.status === 'CONFIRMED' ? 'success' : 'warning'" effect="plain">
                {{ g.statusName }}
              </el-tag>
            </div>
            <el-button
              size="small"
              type="primary"
              plain
              :disabled="identity.role !== 'REFEREE' || !canOpenGame"
              @click="openGame"
            >+ 新一局</el-button>
          </div>

          <!-- 当前局录入 -->
          <template v-if="currentGame">
            <div class="game-entry">
              <div class="entry-head">
                <b>第 {{ currentGame.gameNo }} 局</b>
                <el-tag size="small" :type="currentGame.status === 'CONFIRMED' ? 'success' : 'warning'" effect="dark">
                  {{ currentGame.statusName }}
                </el-tag>
                <span class="muted">{{ currentGame.arrows.length }} / {{ requiredArrows }} 支</span>
              </div>

              <!-- 两队队员的逐箭槽位 -->
              <div class="teams-arrows">
                <div v-for="side in sides" :key="side.key" class="side-arrows">
                  <div class="side-title">{{ side.teamName }}</div>
                  <div v-for="mem in side.members" :key="mem.memberId" class="member-arrows">
                    <span class="mem-name">{{ mem.name }}</span>
                    <span
                      v-for="i in match.arrowsPerMember"
                      :key="i"
                      class="slot"
                      @click="shoot(side, mem, i)"
                    >
                      <span class="ring-chip" :class="chipClass(arrowFor(mem, i))">{{ arrowFor(mem, i) || '·' }}</span>
                    </span>
                    <span class="mem-total mono">{{ memberTotal(mem, currentGame.arrows) }}</span>
                  </div>
                  <div class="side-total mono">{{ sideTotal(side, currentGame.arrows) }} 分 · X {{ sideX(side, currentGame.arrows) }}</div>
                </div>
              </div>

              <!-- 环数键盘 -->
              <div class="keypad-row" v-if="currentGame.status === 'ENTERING' && identity.role === 'REFEREE'">
                <span class="muted">先选队员槽位，再点环数：</span>
                <el-radio-group v-model="selectedSlot" size="small">
                  <el-radio-button
                    v-for="opt in shootableOptions"
                    :key="opt.key"
                    :value="opt.key"
                  >{{ opt.label }}</el-radio-button>
                </el-radio-group>
                <div class="keypad">
                  <button
                    v-for="ring in rings"
                    :key="ring"
                    type="button"
                    class="key"
                    :class="keyClass(ring)"
                    :disabled="!selectedSlot"
                    @click="shootRing(ring)"
                  >{{ ring }}</button>
                </div>
              </div>

              <el-alert
                v-if="currentGame.status === 'CONFIRMED'"
                type="success" :closable="false" show-icon
                title="本局已确认，不能直接改写；如录入有误，请在下方撤回本场最后一局后重新录入"
              />

              <div class="game-actions">
                <el-button
                  v-if="currentGame.status === 'ENTERING'"
                  type="primary"
                  :disabled="identity.role !== 'REFEREE' || currentGame.arrows.length < requiredArrows"
                  @click="confirmGame"
                >确认本局（箭数须录齐 {{ requiredArrows }} 支）</el-button>
                <span v-if="currentGame.status === 'ENTERING' && currentGame.arrows.length < requiredArrows" class="muted">
                  箭数未齐，还差 {{ requiredArrows - currentGame.arrows.length }} 支
                </span>
              </div>
            </div>
          </template>

          <!-- 撤回最后一局 -->
          <div class="withdraw-line">
            <el-button
              type="danger" plain
              :disabled="identity.role !== 'REFEREE' || match.games.length === 0"
              @click="withdrawLast"
            >撤回本场最后一局</el-button>
            <span class="muted">只能撤回当前未结束比赛的最后一局；撤回动作、撤回人、时间、原因进入完整轨迹</span>
          </div>

          <!-- 确认胜者 / 进入加赛 -->
          <div class="finish-line">
            <el-button
              type="success"
              :disabled="identity.role !== 'REFEREE' || !allGamesConfirmed"
              @click="confirmWinner"
            >确认胜者（正规局分高者晋级）</el-button>
            <el-button
              type="warning"
              :disabled="identity.role !== 'REFEREE' || !allGamesConfirmed || hasUndecidedPlayoff"
              @click="startPlayoff"
            >平分 → 进入加赛</el-button>
            <span class="muted" v-if="!allGamesConfirmed">规定 {{ match.gamesPerMatch }} 局全部确认后才能判定胜者</span>
          </div>
        </div>

        <!-- 加赛区 -->
        <div class="panel" v-if="match.playoffs.length">
          <div class="panel-title">加赛 <small>每轮每名队员各射 1 支：先比加赛总分，再比 X 数，仍平继续下一轮</small></div>
          <div v-for="(p, idx) in match.playoffs" :key="p.id" class="playoff-block">
            <div class="entry-head">
              <b>加赛第 {{ p.roundNo }} 轮</b>
              <el-tag size="small" :type="p.status === 'DECIDED' ? 'success' : p.status === 'TIED' ? 'warning' : 'danger'" effect="dark">
                {{ p.statusName }}
              </el-tag>
              <span class="mono">{{ p.scoreA }} : {{ p.scoreB }} · X {{ p.xA }} : {{ p.xB }}</span>
              <el-tag v-if="p.winnerTeamName" size="small" type="success" effect="dark">{{ p.winnerTeamName }} 胜</el-tag>
            </div>
            <div class="teams-arrows">
              <div v-for="side in sides" :key="'p' + side.key" class="side-arrows">
                <div class="side-title">{{ side.teamName }}</div>
                <div v-for="mem in side.members" :key="mem.memberId" class="member-arrows">
                  <span class="mem-name">{{ mem.name }}</span>
                  <span
                    class="slot"
                    :class="{ disabled: !isPlayoffShootable(p, mem) }"
                    @click="shootPlayoffArrow(p, mem)"
                  >
                    <span class="ring-chip" :class="chipClass(playoffArrowFor(p, mem))">{{ playoffArrowFor(p, mem) || '·' }}</span>
                  </span>
                </div>
              </div>
            </div>
            <div class="game-actions" v-if="p.status === 'ENTERING' && identity.role === 'REFEREE'">
              <el-button type="primary" :disabled="p.arrows.length < 6" @click="confirmPlayoff(p)">
                确认本轮（须 6 支录齐）
              </el-button>
              <span v-if="p.arrows.length < 6" class="muted">还差 {{ 6 - p.arrows.length }} 支</span>
            </div>
          </div>
          <el-button
            v-if="canStartNextPlayoff"
            type="warning"
            :disabled="identity.role !== 'REFEREE'"
            @click="startPlayoff"
          >仍平分 → 开始下一轮加赛</el-button>
        </div>
      </template>
    </div>

    <!-- 加赛选箭小弹层 -->
    <el-dialog v-model="ringDialog.visible" :title="`加赛记箭 · ${ringDialog.member?.name || ''}`" width="420px" append-to-body>
      <div class="dialog-keypad">
        <button
          v-for="ring in rings"
          :key="ring"
          type="button"
          class="key"
          :class="keyClass(ring)"
          @click="dialogPick(ring)"
        >{{ ring }}</button>
      </div>
      <template #footer>
        <el-button @click="ringDialog.visible = false">取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tournamentApi } from '../../api'
import { authPayload, identity } from './identity'

const props = defineProps({ tournamentId: { type: Number, required: true } })
defineEmits(['changed'])

const RINGS = ['X', '10', '9', '8', '7', '6', 'M']
const rings = RINGS

const matches = ref([])
const selectedId = ref(null)
const match = ref(null)
const currentGameId = ref(null)
const selectedSlot = ref(null)

const currentGame = computed(() => {
  if (!match.value || currentGameId.value == null) return null
  return match.value.games.find((g) => g.id === currentGameId.value) || null
})

const sides = computed(() => {
  if (!match.value) return []
  return [
    { key: 'A', teamId: match.value.teamAId, teamName: match.value.teamA?.teamName, members: match.value.teamA?.members || [] },
    { key: 'B', teamId: match.value.teamBId, teamName: match.value.teamB?.teamName, members: match.value.teamB?.members || [] }
  ]
})

const requiredArrows = computed(() => (match.value ? match.value.arrowsPerMember * 3 * 2 : 0))

const allGamesConfirmed = computed(() =>
  match.value
  && match.value.status === 'LIVE'
  && match.value.games.length === match.value.gamesPerMatch
  && match.value.games.every((g) => g.status === 'CONFIRMED'))

const canOpenGame = computed(() => {
  if (!match.value || match.value.status !== 'LIVE') return false
  if (match.value.games.length >= match.value.gamesPerMatch) return false
  if (!match.value.games.length) return true
  return match.value.games[match.value.games.length - 1].status === 'CONFIRMED'
})

const hasUndecidedPlayoff = computed(() => {
  if (!match.value?.playoffs.length) return false
  const last = match.value.playoffs[match.value.playoffs.length - 1]
  return last.status === 'ENTERING'
})

const canStartNextPlayoff = computed(() =>
  match.value
  && match.value.status === 'LIVE'
  && match.value.playoffs.length
  && match.value.playoffs[match.value.playoffs.length - 1].status === 'TIED')

const shootableOptions = computed(() => {
  const opts = []
  if (!currentGame.value || currentGame.value.status !== 'ENTERING') return opts
  for (const side of sides.value) {
    for (const mem of side.members) {
      const used = currentGame.value.arrows.filter((a) => a.memberId === mem.memberId).length
      if (used < match.value.arrowsPerMember) {
        opts.push({ key: `g-${mem.memberId}`, label: `${side.key} ${mem.name}（${used}/${match.value.arrowsPerMember}）` })
      }
    }
  }
  return opts
})

async function loadMatches() {
  const detail = await tournamentApi.detail(props.tournamentId)
  matches.value = detail.matches
}

defineExpose({ select })

async function select(id) {
  selectedId.value = id
  await reloadMatch()
}

async function reloadMatch() {
  if (selectedId.value == null) return
  match.value = await tournamentApi.match(selectedId.value)
  // 自动选中最后一局
  if (match.value.games.length) {
    currentGameId.value = match.value.games[match.value.games.length - 1].id
  } else {
    currentGameId.value = null
  }
}

function pickGame(g) {
  currentGameId.value = g.id
}

async function startMatch() {
  match.value = await tournamentApi.startMatch(selectedId.value, authPayload())
  ElMessage.success('场次已开赛')
  await reloadMatch()
}

async function openGame() {
  match.value = await tournamentApi.createGame(selectedId.value, authPayload())
  currentGameId.value = match.value.games[match.value.games.length - 1].id
  selectedSlot.value = null
}

function arrowFor(mem, index) {
  if (!currentGame.value) return ''
  const list = currentGame.value.arrows.filter((a) => a.memberId === mem.memberId)
  return list[index - 1]?.ring || ''
}

function memberTotal(mem, arrows) {
  return arrows.filter((a) => a.memberId === mem.memberId).reduce((s, a) => s + a.ringValue, 0)
}

function sideTotal(side, arrows) {
  return arrows.filter((a) => a.teamId === side.teamId).reduce((s, a) => s + a.ringValue, 0)
}

function sideX(side, arrows) {
  return arrows.filter((a) => a.teamId === side.teamId && a.ring === 'X').length
}

/** 点队员槽位：记录该队员为目标（实际落库由随后点环数触发） */
function shoot(side, mem, index) {
  if (!currentGame.value || currentGame.value.status !== 'ENTERING' || identity.role !== 'REFEREE') return
  const used = currentGame.value.arrows.filter((a) => a.memberId === mem.memberId).length
  if (used >= match.value.arrowsPerMember) {
    ElMessage.info(`队员「${mem.name}」本局规定箭数已录齐`)
    return
  }
  selectedSlot.value = `g-${mem.memberId}`
}

async function shootRing(ring) {
  if (!selectedSlot.value || !selectedSlot.value.startsWith('g-')) return
  const memberId = Number(selectedSlot.value.slice(2))
  try {
    match.value = await tournamentApi.shootGame(currentGameId.value, authPayload({ memberId, ring }))
    // 保持当前局选中
  } catch (e) {
    /* 拦截器提示 */
  }
}

async function confirmGame() {
  match.value = await tournamentApi.confirmGame(currentGameId.value, authPayload())
  ElMessage.success('本局已确认')
}

async function withdrawLast() {
  let reason
  try {
    const res = await ElMessageBox.prompt('请填写撤回最后一局的原因（撤回人、时间、原因将进入轨迹）', '撤回最后一局', {
      confirmButtonText: '确认撤回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim() ? true : '必须填写撤回原因')
    })
    reason = res.value
  } catch (e) {
    return
  }
  match.value = await tournamentApi.withdrawLast(selectedId.value, authPayload({ reason }))
  ElMessage.success('已撤回最后一局，可重新录入')
  if (match.value.games.length) currentGameId.value = match.value.games[match.value.games.length - 1].id
  else currentGameId.value = null
}

async function confirmWinner() {
  match.value = await tournamentApi.confirmWinner(selectedId.value, authPayload())
  ElMessage.success('胜者已确认，后续对阵已自动带入晋级队')
  await loadMatches()
}

async function startPlayoff() {
  match.value = await tournamentApi.startPlayoff(selectedId.value, authPayload())
  ElMessage.success('进入加赛')
}

function playoffArrowFor(p, mem) {
  return p.arrows.find((a) => a.memberId === mem.memberId)?.ring || ''
}

function isPlayoffShootable(p, mem) {
  return p.status === 'ENTERING' && identity.role === 'REFEREE'
    && !p.arrows.some((a) => a.memberId === mem.memberId)
}

async function shootPlayoffArrow(p, mem) {
  if (p.status !== 'ENTERING' || identity.role !== 'REFEREE') return
  if (p.arrows.some((a) => a.memberId === mem.memberId)) {
    ElMessage.info('该队员本轮加赛已射一支')
    return
  }
  openRingDialog('playoff', p.id, mem)
}

import { reactive as _r } from 'vue'
const ringDialog = _r({ visible: false, kind: null, refId: null, member: null })

function openRingDialog(kind, refId, mem) {
  ringDialog.visible = true
  ringDialog.kind = kind
  ringDialog.refId = refId
  ringDialog.member = mem
}

async function dialogPick(ring) {
  if (ringDialog.kind === 'playoff') {
    match.value = await tournamentApi.shootPlayoff(ringDialog.refId, authPayload({ memberId: ringDialog.member.memberId, ring }))
  }
  ringDialog.visible = false
}

async function confirmPlayoff(p) {
  match.value = await tournamentApi.confirmPlayoff(p.id, authPayload())
  const fresh = match.value.playoffs.find((x) => x.id === p.id)
  if (fresh?.status === 'TIED') ElMessage.info('加赛仍平分，请开始下一轮')
  else ElMessage.success('本轮加赛分出胜负')
  await loadMatches()
}

function missingText(m) {
  const parts = []
  if (!m.teamA) parts.push('A 侧等待晋级')
  if (!m.teamB) parts.push('B 侧等待晋级')
  return parts.join('；')
}

function sourceText(source) {
  if (!source) return '晋级'
  return `R${Math.floor(source.slotNo / 100)}-M${source.slotNo % 100} 胜者`
}

function matchTag(s) {
  if (s === 'FINISHED') return 'success'
  if (s === 'LIVE') return 'danger'
  return 'info'
}

function chipClass(ring) {
  if (ring === 'X') return 'is-x'
  if (ring === '10') return 'is-ten'
  if (ring === 'M') return 'is-miss'
  return ''
}

function keyClass(ring) {
  return 'key-' + (ring === 'X' ? 'x' : ring === '10' ? 'ten' : ring === 'M' ? 'miss' : 'normal')
}

onMounted(loadMatches)

watch(() => props.tournamentId, async () => {
  selectedId.value = null
  match.value = null
  currentGameId.value = null
  await loadMatches()
})
</script>

<style scoped>
.score-console {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 14px;
  align-items: start;
}
.match-list {
  position: sticky;
  top: 0;
}
.match-row {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 8px;
  cursor: pointer;
}
.match-row:hover {
  border-color: var(--el-color-primary-light-5);
}
.match-row.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.match-row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.match-no {
  font-size: 11px;
  color: var(--ink-mute);
}
.match-row-teams {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
}
.match-row-teams em {
  font-style: normal;
  margin: 0 auto;
  color: var(--ink-mute);
}
.match-row-teams .win {
  color: var(--el-color-primary-dark-2);
  font-weight: 700;
}
.teams-head {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 10px;
  margin: 8px 0;
}
.team-block.right {
  text-align: right;
}
.team-block.champ .team-name {
  color: var(--el-color-primary-dark-2);
}
.team-name {
  font-size: 16px;
  font-weight: 700;
}
.team-members {
  margin-top: 4px;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.team-block.right .team-members {
  justify-content: flex-end;
}
.vs-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 20px;
}
.vs-score small {
  font-size: 11px;
  color: var(--ink-mute);
}
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.wait-alert {
  flex: 1;
  min-width: 240px;
}
.games-strip {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.game-chip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 4px 10px;
  cursor: pointer;
  min-width: 96px;
}
.game-chip.confirmed {
  background: #f3faf7;
  border-color: var(--el-color-primary-light-7);
}
.game-entry {
  border: 1px dashed var(--line);
  border-radius: 10px;
  padding: 12px;
}
.entry-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.teams-arrows {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.side-arrows {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 8px 10px;
}
.side-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.member-arrows {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 4px 0;
}
.mem-name {
  width: 70px;
  font-size: 12px;
}
.slot {
  cursor: pointer;
}
.slot.disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.mem-total {
  margin-left: auto;
  font-size: 12px;
  color: var(--ink-mute);
}
.side-total {
  margin-top: 6px;
  font-size: 13px;
  color: var(--el-color-primary-dark-2);
}
.keypad-row {
  margin: 12px 0;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.keypad {
  display: flex;
  gap: 6px;
}
.key {
  width: 42px;
  height: 38px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: #fff;
  font-size: 16px;
  font-weight: 700;
  font-family: ui-monospace, monospace;
  cursor: pointer;
}
.key:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.key-x {
  background: linear-gradient(135deg, #00695c, #26a69a);
  color: #fff;
  border-color: #00503f;
}
.key-ten {
  background: #fff6d8;
  border-color: #f0d47c;
  color: #8a6100;
}
.key-miss {
  background: #f1f3f4;
  color: #98a3a8;
}
.game-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.withdraw-line,
.finish-line {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}
.playoff-block {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 10px;
}
@media (max-width: 1100px) {
  .score-console {
    grid-template-columns: 1fr;
  }
  .teams-arrows {
    grid-template-columns: 1fr;
  }
}
.dialog-keypad {
  display: flex;
  gap: 8px;
  justify-content: center;
}
.dialog-keypad .key {
  width: 48px;
  height: 44px;
}
</style>
