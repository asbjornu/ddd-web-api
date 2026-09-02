// Flat ESLint config for elevator-ui.
//
// Plain TypeScript now -- no Vue, no Nuxt, so no framework-specific
// preset is needed to make auto-imported globals or SFC parsing work.
// typescript-eslint's own recommended config already disables `no-undef`
// in favor of the type checker catching the same class of mistake more
// precisely.
//
// eslint-config-prettier goes last and switches off every stylistic rule
// that Prettier already decides, so the two never disagree about the
// same line.
//
// Note for anyone tempted to tidy up: this repository contains deliberate
// code smells (see docs/architecture.md). Lint is here to catch mistakes,
// not to improve the design.
import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import prettier from 'eslint-config-prettier'

export default tseslint.config(
  { ignores: ['public/**/*.js'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // serve.mjs is the one file in this project that runs in Node
    // rather than the browser -- everything else is either compiled
    // to a browser <script type="module"> or, for tsconfig/eslint
    // config files, already covered by typescript-eslint's own
    // handling of no-undef via the type checker.
    files: ['**/*.mjs'],
    languageOptions: {
      globals: {
        URL: 'readonly',
        process: 'readonly',
        console: 'readonly'
      }
    }
  },
  prettier
)
