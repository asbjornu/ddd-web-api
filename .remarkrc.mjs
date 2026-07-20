/**
 * remark-lint configuration for this repository's Markdown files.
 *
 * Deviations from remark-lint's defaults:
 *
 * - `maximum-line-length` is set to 75 (the plugin's own default is 80),
 *   to match the 75-character hard-wrap convention documented in
 *   AGENTS.md's "Coding conventions" section.
 *
 * Everything else comes straight from the `recommended` and `consistent`
 * presets: `recommended` catches likely mistakes, and `consistent`
 * enforces that whichever style (heading markers, list markers,
 * emphasis/strong markers, etc.) is used first in a document is used
 * throughout that same document, without forcing an arbitrary global
 * choice.
 */
export default {
  plugins: [
    "remark-preset-lint-recommended",
    "remark-preset-lint-consistent",
    ["remark-lint-maximum-line-length", 75],
  ],
};
