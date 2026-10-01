<template>
  <section class="profile-hero">
    <img
      class="profile-hero__art"
      src="/images/NBA-vector-logos-636x480.webp"
      alt=""
      aria-hidden="true"
      draggable="false"
    />
    <div class="profile-hero__overlay"></div>
    <div class="profile-hero__identity">
      <div class="profile-hero__avatar-wrap" @click="$emit('avatar-click')">
        <div class="profile-hero__avatar">
          <img v-if="avatarUrl" :src="avatarUrl" class="profile-hero__avatar-img" />
          <span v-else>{{ initial }}</span>
        </div>
        <button
          class="profile-hero__camera"
          type="button"
          aria-label="更换头像"
          title="更换头像"
          :disabled="uploading"
          @click.stop="$emit('upload-click')"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/>
            <circle cx="12" cy="13" r="4"/>
          </svg>
        </button>
      </div>
      <div class="profile-hero__text">
        <h1 class="profile-hero__name">{{ username }}</h1>
        <div class="profile-hero__meta">
          <span class="profile-hero__role" :class="{ 'is-admin': isAdmin }">
            {{ isAdmin ? '系统管理员' : '认证球迷' }}
          </span>
          <span class="profile-hero__label">Courtside 专属主场</span>
        </div>
        <button
          class="profile-hero__upload-btn"
          type="button"
          :disabled="uploading"
          @click="$emit('upload-click')"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          更换头像
        </button>
        <button
          v-if="avatarUrl"
          class="profile-hero__reset-btn"
          type="button"
          :disabled="uploading"
          @click="$emit('reset-avatar')"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
            <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/>
            <circle cx="9" cy="7" r="4"/>
            <line x1="17" y1="8" x2="23" y2="13"/>
            <line x1="23" y1="8" x2="17" y2="13"/>
          </svg>
          使用默认头像
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  username: string
  avatarUrl: string | null
  initial: string
  isAdmin: boolean
  uploading?: boolean
}>(), {
  uploading: false,
})

defineEmits<{
  'avatar-click': []
  'upload-click': []
  'reset-avatar': []
}>()
</script>

<style scoped>
.profile-hero {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  border-radius: var(--radius-xl);
  background: var(--profile-hero-bg);
  border: 1px solid var(--profile-hero-border);
  min-height: 280px;
  display: flex;
  align-items: center;
}

.profile-hero__art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  max-width: none;
  object-fit: cover;
  object-position: center center;
  opacity: var(--profile-hero-art-opacity);
  filter: var(--profile-hero-art-filter);
  mix-blend-mode: var(--profile-hero-art-blend);
  pointer-events: none;
  user-select: none;
  z-index: 0;
}

.profile-hero__overlay {
  position: absolute;
  inset: 0;
  background: var(--profile-hero-overlay);
  pointer-events: none;
  z-index: 1;
}

.profile-hero__identity {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 32px 36px;
}

.profile-hero__identity::before {
  content: '';
  position: absolute;
  inset: -32px -80px -32px -40px;
  z-index: -1;
  pointer-events: none;
  background: var(--profile-hero-content-glow);
}

/* Avatar */
.profile-hero__avatar-wrap {
  position: relative;
  cursor: pointer;
  flex-shrink: 0;
}

.profile-hero__avatar {
  width: 128px;
  height: 128px;
  border-radius: 50%;
  background: var(--accent-lighter);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  font-weight: 700;
  color: var(--accent);
  overflow: hidden;
  border: 3px solid var(--profile-hero-border);
  box-shadow: var(--profile-hero-avatar-shadow);
  transition: border-color var(--duration-fast) var(--ease-smooth);
}

.profile-hero__avatar-wrap:hover .profile-hero__avatar {
  border-color: var(--accent);
}

.profile-hero__avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-hero__camera {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--bg-card);
  border: 2px solid var(--profile-hero-border);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
  z-index: 3;
}

.profile-hero__camera:hover {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--text-inverse);
}

.profile-hero__camera:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Text */
.profile-hero__text {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-hero__name {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  color: var(--profile-hero-title);
  font-family: var(--font-heading);
  line-height: 1.2;
  text-shadow: var(--dashboard-hero-text-shadow);
}

.profile-hero__meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.profile-hero__role {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 980px;
  font-size: 12px;
  font-weight: 600;
  background: var(--accent-lighter);
  color: var(--accent);
}

.profile-hero__role.is-admin {
  background: rgba(139,92,246,0.15);
  color: var(--purple);
}

.profile-hero__label {
  font-size: 13px;
  color: var(--profile-hero-muted);
}

.profile-hero__upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 980px;
  font-size: 13px;
  font-weight: 500;
  background: var(--dashboard-hero-secondary-button);
  color: var(--profile-hero-title);
  border: 1px solid var(--profile-hero-border);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
  width: fit-content;
  margin-top: 4px;
}

.profile-hero__upload-btn:hover {
  background: var(--dashboard-hero-secondary-button-hover);
}

.profile-hero__upload-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.profile-hero__upload-btn:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.profile-hero__reset-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 980px;
  font-size: 13px;
  font-weight: 500;
  background: transparent;
  color: var(--profile-hero-muted);
  border: 1px solid var(--profile-hero-border);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-smooth);
  width: fit-content;
  margin-top: 4px;
}

.profile-hero__reset-btn:hover {
  color: var(--profile-hero-title);
  border-color: var(--profile-hero-title);
}

.profile-hero__reset-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.profile-hero__reset-btn:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

/* Responsive */
@media (max-width: 1199px) and (min-width: 769px) {
  .profile-hero {
    min-height: 250px;
  }
  .profile-hero__art {
    object-position: center 45%;
  }
  .profile-hero__avatar {
    width: 112px;
    height: 112px;
    font-size: 42px;
  }
  .profile-hero__name {
    font-size: 28px;
  }
}

@media (max-width: 768px) {
  .profile-hero {
    min-height: 300px;
    align-items: flex-end;
  }
  .profile-hero__identity {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
    padding: 20px;
  }
  .profile-hero__avatar {
    width: 88px;
    height: 88px;
    font-size: 36px;
  }
  .profile-hero__camera {
    width: 32px;
    height: 32px;
  }
  .profile-hero__name {
    font-size: 24px;
  }
  .profile-hero__upload-btn {
    font-size: 12px;
    padding: 6px 14px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .profile-hero__avatar-wrap:hover .profile-hero__avatar {
    transform: none;
  }
}
</style>
