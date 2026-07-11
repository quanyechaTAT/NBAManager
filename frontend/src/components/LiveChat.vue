<template>
  <GameChatRoom :game-id="gameId" :home-team="homeTeam" :away-team="awayTeam" />
</template>

<script setup lang="ts">
import GameChatRoom from './game-chat/GameChatRoom.vue'

defineProps<{
  gameId: string
  homeTeam?: string
  awayTeam?: string
}>()
</script>
