// Flat ESLint config for elevator-ui.
//
// withNuxt() layers Nuxt's own rules on top: Vue SFC parsing, TypeScript,
// and the auto-imported globals ($fetch, defineEventHandler, useRuntimeConfig
// and friends) that would otherwise all read as no-undef.
//
// eslint-config-prettier goes last and switches off every stylistic rule that
// Prettier already decides, so the two never disagree about the same line.
//
// Note for anyone tempted to tidy up: this repository contains deliberate code
// smells (see docs/architecture.md). Lint is here to catch mistakes, not to
// improve the design.
import withNuxt from './.nuxt/eslint.config.mjs'
import prettier from 'eslint-config-prettier'

export default withNuxt(prettier)
