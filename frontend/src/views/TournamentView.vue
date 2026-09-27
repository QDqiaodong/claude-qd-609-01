<template>
  <div class="page">
    <div class="page-head">
      <h2 class="page-title">团体淘汰赛计分台 <em>独立比赛计分 · 服务端维护对阵与晋级</em></h2>
      <div class="page-tools">
        <IdentityBar />
      </div>
    </div>

    <TournamentOverview v-if="!activeTournamentId" @open="openTournament" />

    <template v-else>
      <div class="sub-tabs">
        <el-button :type="tab === 'overview' ? 'primary' : ''" @click="tab = 'overview'">📋 赛事总览</el-button>
        <el-button :type="tab === 'bracket' ? 'primary' : ''" @click="tab = 'bracket'">🌳 对阵树</el-button>
        <el-button :type="tab === 'scoring' ? 'primary' : ''" @click="goScoringTab()">🏹 单场计分区</el-button>
        <el-button text @click="back">← 返回赛事列表</el-button>
        <el-tag v-if="activeSummary" effect="plain" :type="statusTag(activeSummary.status)">
          {{ activeSummary.name }} · {{ activeSummary.statusName }} · 第 {{ activeSummary.currentRound }}/{{ activeSummary.totalRounds }} 轮
        </el-tag>
      </div>

      <TournamentOverview v-if="tab === 'overview'" :tournament-id="activeTournamentId" embedded />
      <BracketTree v-else-if="tab === 'bracket'" :tournament-id="activeTournamentId" @score="scoreMatch" />
      <MatchScoring v-show="tab === 'scoring'" ref="scoringRef" :tournament-id="activeTournamentId" @changed="loadSummaries" />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import IdentityBar from './tournament/IdentityBar.vue'
import TournamentOverview from './tournament/TournamentOverview.vue'
import BracketTree from './tournament/BracketTree.vue'
import MatchScoring from './tournament/MatchScoring.vue'
import { tournamentApi } from '../api'

const activeTournamentId = ref(null)
const tab = ref('overview')
const summaries = ref([])
const scoringRef = ref(null)

const activeSummary = computed(() => summaries.value.find((t) => t.id === activeTournamentId.value) || null)

function openTournament(id) {
  activeTournamentId.value = id
  tab.value = 'overview'
}

function back() {
  activeTournamentId.value = null
  tab.value = 'overview'
}

function scoreMatch(matchId) {
  tab.value = 'scoring'
  scoringRef.value?.select(matchId)
}

function goScoringTab() {
  tab.value = 'scoring'
}

function statusTag(status) {
  if (status === 'FINISHED') return 'success'
  if (status === 'ONGOING') return 'warning'
  return 'info'
}

async function loadSummaries() {
  summaries.value = await tournamentApi.list()
}

onMounted(loadSummaries)
</script>

<style scoped>
.sub-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
</style>
