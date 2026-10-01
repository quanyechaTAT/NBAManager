<template>
  <!-- 布局层不做过渡：切页时导航/赛况条保持稳定，页面级过渡见 MainLayout 内层 router-view -->
  <router-view />
</template>

<script setup lang="ts"></script>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
  background: var(--bg-page);
  color: var(--text-primary);
  font-family: var(--font-body);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.route-shell {
  min-height: 100%;
}

#app {
  position: relative;
  isolation: isolate;
}

#app::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: -1;
  background:
    radial-gradient(900px 420px at 12% -8%, rgba(34, 211, 238, 0.07), transparent 55%),
    radial-gradient(760px 360px at 88% 0%, rgba(6, 182, 212, 0.05), transparent 50%);
  opacity: 0.9;
}

[data-theme="light"] #app::before {
  background:
    radial-gradient(900px 420px at 12% -8%, rgba(34, 211, 238, 0.05), transparent 55%),
    radial-gradient(760px 360px at 88% 0%, rgba(14, 165, 233, 0.04), transparent 50%);
}
</style>
