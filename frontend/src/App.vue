<template>
  <router-view v-slot="{ Component }">
    <transition name="page-fade-slide">
      <div>
        <component :is="Component" />
      </div>
    </transition>
  </router-view>
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
</style>
