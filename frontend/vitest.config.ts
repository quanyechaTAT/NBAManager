import { realpathSync } from 'node:fs'
import { mergeConfig } from 'vite'
import { defineConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig,
  defineConfig({
    root: realpathSync(process.cwd()),
    test: {
      environment: 'happy-dom',
      include: ['src/**/*.test.ts'],
      clearMocks: true,
      restoreMocks: true,
    },
  }),
)
