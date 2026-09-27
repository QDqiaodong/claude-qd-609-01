<template>
  <div>
    <div class="panel" v-if="data">
      <div class="panel-title">
        对阵树
        <small>{{ data.tournamentName }} · 当前第 {{ data.currentRound }}/{{ data.totalRounds }} 轮（晋级关系与胜者由服务端给出）</small>
      </div>
      <p class="rule-tip">
        首轮按签位对阵；后续场次的两侧标注了「下一场的晋级来源」。某场确认胜者后，服务端自动把晋级队写入下一场，
        刷新或重启都不丢，前端不负责拼接对阵结果。点击卡片可进入该场计分区。
      </p>

      <div class="bracket" :style="{ gridTemplateColumns: `repeat(${data.totalRounds}, minmax(260px, 1fr))` }">
        <div v-for="r in data.rounds" :key="r.roundNo" class="bracket-col">
          <div class="col-head">
            第 {{ r.roundNo }} 轮
            <small v-if="r.roundNo === data.totalRounds">（决赛）</small>
            <small v-else-if="data.totalRounds === 3 && r.roundNo === 2">（半决赛）</small>
          </div>
          <div class="col-body" :style="{ gap: r.roundNo === 1 ? '14px' : r.roundNo === 2 ? '70px' : '160px' }">
            <div
              v-for="m in r.matches"
              :key="m.id"
              class="match-card"
              :class="cardClass(m)"
              @click="$emit('score', m.id)"
            >
              <div class="match-head">
                <span class="match-no mono">R{{ m.roundNo }}-M{{ m.slotNo - m.roundNo * 100 }}</span>
                <el-tag size="small" :type="matchTag(m.status)" effect="plain">{{ m.statusName }}</el-tag>
              </div>

              <div class="side" :class="{ winner: m.winnerTeamId && m.teamA && m.winnerTeamId === m.teamA.teamId }">
                <span class="side-name">{{ m.teamA ? m.teamA.teamName : sourceText(m.sourceA) }}</span>
                <span class="side-score mono">{{ m.teamA ? m.regScoreA : '—' }}</span>
              </div>
              <div class="side" :class="{ winner: m.winnerTeamId && m.teamB && m.winnerTeamId === m.teamB.teamId }">
                <span class="side-name">{{ m.teamB ? m.teamB.teamName : sourceText(m.sourceB) }}</span>
                <span class="side-score mono">{{ m.teamB ? m.regScoreB : '—' }}</span>
              </div>

              <div class="match-foot">
                <el-tag v-if="m.playoffRoundCount" size="small" type="warning" effect="dark">加赛 {{ m.playoffRoundCount }} 轮</el-tag>
                <el-tag v-if="m.winMode === 'PLAYOFF'" size="small" type="danger" effect="plain">加赛取胜</el-tag>
                <span class="muted">{{ m.teamA && m.teamB ? '点击记分' : '等待晋级' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { tournamentApi } from '../../api'

const props = defineProps({ tournamentId: { type: Number, required: true } })
defineEmits(['score'])

const data = ref(null)

async function load() {
  data.value = await tournamentApi.bracket(props.tournamentId)
}

function sourceText(source) {
  if (!source) return '等待晋级'
  const no = `R${Math.floor(source.slotNo / 100)}-M${source.slotNo % 100}`
  if (source.winnerTeamName) return source.winnerTeamName
  return `等待 ${no} 胜者`
}

function matchTag(s) {
  if (s === 'FINISHED') return 'success'
  if (s === 'LIVE') return 'danger'
  return 'info'
}

function cardClass(m) {
  return {
    'is-live': m.status === 'LIVE',
    'is-finished': m.status === 'FINISHED',
    'is-pending': m.status === 'PENDING'
  }
}

onMounted(load)
watch(() => props.tournamentId, load)
defineExpose({ reload: load })
</script>

<style scoped>
.bracket {
  display: grid;
  gap: 20px;
  align-items: start;
  overflow-x: auto;
}
.bracket-col {
  display: flex;
  flex-direction: column;
}
.col-head {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 10px;
  color: var(--el-color-primary-dark-2);
}
.col-head small {
  margin-left: 6px;
  color: var(--ink-mute);
  font-weight: 400;
}
.col-body {
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  min-height: 320px;
}
.match-card {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff;
  cursor: pointer;
  transition: box-shadow 0.15s, transform 0.1s, border-color 0.15s;
}
.match-card:hover {
  box-shadow: 0 4px 12px rgba(0, 105, 92, 0.15);
  border-color: var(--el-color-primary-light-5);
}
.match-card.is-live {
  border-color: #e6a23c;
  background: #fffaf0;
}
.match-card.is-finished {
  background: #f7fbfa;
}
.match-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.match-no {
  font-size: 11px;
  color: var(--ink-mute);
}
.side {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 6px;
  border-radius: 6px;
  font-size: 13px;
}
.side + .side {
  border-top: 1px dashed var(--line);
}
.side.winner {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary-dark-2);
  font-weight: 700;
}
.side-score {
  font-weight: 700;
}
.match-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 11px;
}
</style>
