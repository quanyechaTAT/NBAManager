<template>
  <div class="team-logo-backdrop" aria-hidden="true">
    <div class="backdrop-grid">
      <div
        v-for="team in teams"
        :key="team.code"
        class="backdrop-cell"
      >
        <img
          :src="`/images/team-logos/${team.code}.png`"
          :alt="''"
          draggable="false"
          @error="onLogoError($event, team.code)"
        />
      </div>
    </div>
    <div class="backdrop-overlay-left"></div>
    <div class="backdrop-overlay-vignette"></div>
  </div>
</template>

<script setup lang="ts">
interface TeamEntry {
  code: string
}

const teams: TeamEntry[] = [
  { code: 'atl' },
  { code: 'bkn' },
  { code: 'bos' },
  { code: 'cha' },
  { code: 'chi' },
  { code: 'cle' },
  { code: 'dal' },
  { code: 'den' },
  { code: 'det' },
  { code: 'gs' },
  { code: 'hou' },
  { code: 'ind' },
  { code: 'lac' },
  { code: 'lal' },
  { code: 'mem' },
  { code: 'mia' },
  { code: 'mil' },
  { code: 'min' },
  { code: 'no' },
  { code: 'nyk' },
  { code: 'okc' },
  { code: 'orl' },
  { code: 'phi' },
  { code: 'phx' },
  { code: 'por' },
  { code: 'sac' },
  { code: 'sas' },
  { code: 'tor' },
  { code: 'utah' },
  { code: 'was' },
]

function onLogoError(e: Event, code: string) {
  console.warn(`[TeamLogoBackdrop] Failed to load logo: ${code}.png`)
  const img = e.target as HTMLImageElement
  img.style.visibility = 'hidden'
}
</script>

<style scoped>
.team-logo-backdrop {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
}

.backdrop-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  grid-template-rows: repeat(3, 1fr);
  gap: 0;
  width: 100%;
  height: 100%;
  padding: 18px 24px;
  box-sizing: border-box;
}

.backdrop-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
}

.backdrop-cell img {
  width: 64px;
  height: 64px;
  object-fit: contain;
  opacity: var(--dashboard-hero-logo-opacity);
  filter: var(--dashboard-hero-logo-filter);
  transition: opacity 0.3s ease;
  user-select: none;
}

/* Left text readability overlay */
.backdrop-overlay-left {
  position: absolute;
  inset: 0;
  background: var(--dashboard-hero-overlay);
  z-index: 1;
}

/* Vignette edges */
.backdrop-overlay-vignette {
  position: absolute;
  inset: 0;
  background: var(--dashboard-hero-vignette);
  z-index: 2;
}

/* Responsive: tablet */
@media (max-width: 1199px) and (min-width: 769px) {
  .backdrop-grid {
    grid-template-columns: repeat(6, 1fr);
    grid-template-rows: repeat(5, 1fr);
    padding: 16px 24px;
  }
  .backdrop-cell img {
    width: 60px;
    height: 60px;
  }
}

/* Responsive: mobile */
@media (max-width: 768px) {
  .backdrop-grid {
    grid-template-columns: repeat(5, 1fr);
    grid-template-rows: repeat(6, 1fr);
    padding: 12px 16px;
  }
  .backdrop-cell img {
    width: 44px;
    height: 44px;
    opacity: calc(var(--dashboard-hero-logo-opacity) * 0.7);
  }
}
</style>
