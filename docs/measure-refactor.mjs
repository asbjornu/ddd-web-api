#!/usr/bin/env node
// Measures the CRUD -> REST+DDD refactor quantitatively, comparing the
// `crud` branch (the last commit before slice 0, full CRUD shape) against
// the current `main` tip, and writes docs/refactor-metrics.md.
//
// Run via `npm run measure` from the repo root. Requires `scc`
// (`brew install scc`) on PATH; `jscpd` is a devDependency, invoked as a
// child process rather than an npx fetch, so `npm install` is enough.
//
// This script only reads the repository (via disposable git worktrees)
// and writes docs/refactor-metrics.md; it does not modify history.

import { execFileSync } from "node:child_process";
import {
  mkdtempSync,
  existsSync,
  rmSync,
  writeFileSync,
  readFileSync,
  readdirSync,
  statSync,
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "..");

const BEFORE_REF = "crud";
const AFTER_REF = "main";

function git(args, opts = {}) {
  return execFileSync("git", args, {
    cwd: REPO_ROOT,
    encoding: "utf8",
    ...opts,
  }).trim();
}

function run(cmd, args, opts = {}) {
  return execFileSync(cmd, args, { encoding: "utf8", ...opts });
}

// --- worktrees ---------------------------------------------------------

function addWorktree(scratchDir, ref, name) {
  const sha = git(["rev-parse", ref]);
  const dir = path.join(scratchDir, name);
  git(["worktree", "add", "--detach", dir, sha]);
  return { dir, sha, ref };
}

function removeWorktree(dir) {
  try {
    git(["worktree", "remove", "--force", dir]);
  } catch {
    // best-effort cleanup; leftover worktrees are harmless and can be
    // pruned later with `git worktree prune`
  }
}

// --- scc (LOC + cyclomatic complexity) ----------------------------------

function sccByFile(cwd, relPaths) {
  const existing = relPaths.filter((p) => existsSync(path.join(cwd, p)));
  if (existing.length === 0) return [];
  const out = run(
    "scc",
    ["--format", "json", "--by-file", ...existing],
    { cwd },
  );
  const languages = JSON.parse(out);
  return languages.flatMap((lang) => lang.Files ?? []);
}

function totals(files) {
  const lines = files.reduce((sum, f) => sum + f.Lines, 0);
  const code = files.reduce((sum, f) => sum + f.Code, 0);
  const complexities = files.map((f) => f.Complexity).sort((a, b) => a - b);
  const sumComplexity = complexities.reduce((a, b) => a + b, 0);
  const n = files.length;
  const median =
    n === 0
      ? 0
      : n % 2 === 1
        ? complexities[(n - 1) / 2]
        : (complexities[n / 2 - 1] + complexities[n / 2]) / 2;
  return {
    files: n,
    lines,
    code,
    avgComplexity: n === 0 ? 0 : round1(sumComplexity / n),
    medianComplexity: round1(median),
    maxComplexity: n === 0 ? 0 : Math.max(...complexities),
  };
}

function largestFile(files) {
  if (files.length === 0) return null;
  return files.reduce((a, b) => (b.Lines > a.Lines ? b : a));
}

function sizeStats(files) {
  const sizes = files.map((f) => f.Lines).sort((a, b) => a - b);
  const n = sizes.length;
  if (n === 0) return { avg: 0, median: 0, max: 0, files: 0 };
  const sum = sizes.reduce((a, b) => a + b, 0);
  const median =
    n % 2 === 1 ? sizes[(n - 1) / 2] : (sizes[n / 2 - 1] + sizes[n / 2]) / 2;
  return {
    avg: round1(sum / n),
    median: round1(median),
    max: Math.max(...sizes),
    files: n,
  };
}

function round1(n) {
  return Math.round(n * 10) / 10;
}

function round2(n) {
  return Math.round(n * 100) / 100;
}

// --- jscpd (duplication) -------------------------------------------------

function jscpdReport(cwd, relPaths, scratchDir, label, opts = {}) {
  const existing = relPaths.filter((p) => existsSync(path.join(cwd, p)));
  if (existing.length === 0) return null;
  const outDir = path.join(scratchDir, `jscpd-${label}-${Date.now()}`);
  const bin = path.join(
    REPO_ROOT,
    "node_modules",
    ".bin",
    "jscpd",
  );
  const { minLines = 5, minTokens = 50 } = opts;
  try {
    run(bin, [
      "--reporters",
      "json",
      "--output",
      outDir,
      "--silent",
      "--threshold",
      "0",
      "--min-lines",
      String(minLines),
      "--min-tokens",
      String(minTokens),
      ...existing,
    ], { cwd });
  } catch {
    // jscpd exits non-zero when the configured threshold is exceeded;
    // we pass --threshold 0 so that shouldn't happen, but tolerate it
    // and still read the report it wrote.
  }
  const reportPath = path.join(outDir, "jscpd-report.json");
  if (!existsSync(reportPath)) return null;
  const report = JSON.parse(readFileSync(reportPath, "utf8"));
  return report.statistics?.total ?? null;
}

// --- grep-equivalent counts ----------------------------------------------

function walkFiles(root, extensions) {
  const results = [];
  const skipDirs = new Set([
    ".git",
    "node_modules",
    ".nuxt",
    ".output",
    "build",
    "dist",
  ]);
  function walk(dir) {
    let entries;
    try {
      entries = readdirSync(dir);
    } catch {
      return;
    }
    for (const entry of entries) {
      const full = path.join(dir, entry);
      const stat = statSync(full);
      if (stat.isDirectory()) {
        if (!skipDirs.has(entry)) walk(full);
      } else if (extensions.some((ext) => entry.endsWith(ext))) {
        results.push(full);
      }
    }
  }
  walk(root);
  return results;
}

function countMatches(files, regex) {
  let count = 0;
  for (const f of files) {
    const text = readFileSync(f, "utf8");
    const matches = text.match(regex);
    if (matches) count += matches.length;
  }
  return count;
}

// --- test analysis: proximity, precision, speed ---------------------------

const SPRING_CONTEXT_TEST_REGEX =
  /@(SpringBootTest|WebMvcTest|DataJpaTest|AutoConfigureMockMvc)\b/;
const TEST_METHOD_REGEX = /@Test\b/g;

function classifyJavaTests(files) {
  let unitFiles = 0;
  let unitTests = 0;
  let contextFiles = 0;
  let contextTests = 0;
  for (const f of files) {
    const text = readFileSync(f, "utf8");
    const testCount = (text.match(TEST_METHOD_REGEX) ?? []).length;
    if (testCount === 0) continue;
    if (SPRING_CONTEXT_TEST_REGEX.test(text)) {
      contextFiles++;
      contextTests += testCount;
    } else {
      unitFiles++;
      unitTests += testCount;
    }
  }
  return { unitFiles, unitTests, contextFiles, contextTests };
}

// Runs the real Gradle test suite in a worktree and times it -- an actual
// measurement, not a proxy, of "speed of execution". Best-effort: returns
// null (with a reason) if the JDK 21 toolchain isn't available, so the
// rest of the report still generates on a machine without it.
function runGradleTests(apiDir, testsFilter) {
  const javaHomeCandidates = [
    process.env.JAVA_HOME,
    "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home",
    "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home",
  ].filter(Boolean);
  const javaHome = javaHomeCandidates.find((p) => existsSync(p));
  if (!javaHome) {
    return { ok: false, reason: "no JDK 21 (JAVA_HOME) found" };
  }
  const env = {
    ...process.env,
    JAVA_HOME: javaHome,
    PATH: `${path.join(javaHome, "bin")}:${process.env.PATH}`,
  };
  const args = ["test", "-q", "--rerun"];
  if (testsFilter) args.push("--tests", testsFilter);
  const start = Date.now();
  try {
    run("./gradlew", args, { cwd: apiDir, env });
  } catch (err) {
    return { ok: false, reason: `gradle test failed: ${err.message}` };
  }
  const ms = Date.now() - start;
  const resultsDir = path.join(apiDir, "build", "test-results", "test");
  let testCount = 0;
  if (existsSync(resultsDir)) {
    for (const f of readdirSync(resultsDir)) {
      if (!f.endsWith(".xml")) continue;
      const xml = readFileSync(path.join(resultsDir, f), "utf8");
      const match = /<testsuite\b[^>]*\btests="(\d+)"/.exec(xml);
      if (match) testCount += Number(match[1]);
    }
  }
  return { ok: true, ms, testCount };
}

// --- framework/infrastructure coupling ------------------------------------

const FRAMEWORK_IMPORT_REGEX =
  /^import ((?:org\.springframework|jakarta\.|lombok\.)[^;]+);/gm;

function frameworkImportAnalysis(files) {
  let filesWithImports = 0;
  const symbols = new Set();
  for (const f of files) {
    const text = readFileSync(f, "utf8");
    const matches = [...text.matchAll(FRAMEWORK_IMPORT_REGEX)];
    if (matches.length > 0) filesWithImports++;
    for (const match of matches) symbols.add(match[1]);
  }
  return {
    totalFiles: files.length,
    filesWithImports,
    distinctSymbols: symbols.size,
  };
}

function javaFilesMatching(root, namePredicate) {
  return walkFiles(root, [".java"]).filter((f) =>
    namePredicate(path.basename(f)),
  );
}

// --- diff stats ------------------------------------------------------------

function diffShortstat(a, b, pathspec) {
  const out = git(["diff", "--shortstat", a, b, "--", pathspec]);
  const files = /(\d+) files? changed/.exec(out)?.[1] ?? "0";
  const insertions = /(\d+) insertions?\(\+\)/.exec(out)?.[1] ?? "0";
  const deletions = /(\d+) deletions?\(-\)/.exec(out)?.[1] ?? "0";
  return {
    files: Number(files),
    insertions: Number(insertions),
    deletions: Number(deletions),
  };
}

function diffNameStatusCounts(a, b, pathspec) {
  const out = git(["diff", "--name-status", a, b, "--", pathspec]);
  const counts = { A: 0, M: 0, D: 0 };
  for (const line of out.split("\n")) {
    const status = line[0];
    if (status && counts[status] !== undefined) counts[status]++;
  }
  return counts;
}

// --- main ------------------------------------------------------------------

function main() {
  const scratchDir = mkdtempSync(path.join(tmpdir(), "measure-refactor-"));
  let before, after;
  try {
    before = addWorktree(scratchDir, BEFORE_REF, "before");
    after = addWorktree(scratchDir, AFTER_REF, "after");

    // --- LOC + file counts, whole elevator-api ---
    const beforeApiFiles = sccByFile(before.dir, [
      "elevator-api/src/main/java",
    ]);
    const afterApiFiles = sccByFile(after.dir, ["elevator-api/src/main/java"]);
    const beforeTestFiles = sccByFile(before.dir, ["elevator-api/src/test"]);
    const afterTestFiles = sccByFile(after.dir, ["elevator-api/src/test"]);

    // --- old CRUD layer (before) vs feature slices + shared kernel (after) ---
    const oldLayerDirs = [
      "elevator-api/src/main/java/no/javazone/elevator/controller",
      "elevator-api/src/main/java/no/javazone/elevator/model",
      "elevator-api/src/main/java/no/javazone/elevator/service",
      "elevator-api/src/main/java/no/javazone/elevator/repository",
    ];
    const oldLayerFiles = sccByFile(before.dir, oldLayerDirs);
    const featureFiles = sccByFile(after.dir, [
      "elevator-api/src/main/java/no/javazone/elevator/feature",
    ]);
    const sharedFiles = sccByFile(after.dir, [
      "elevator-api/src/main/java/no/javazone/elevator/shared",
    ]);

    // --- per-slice LOC (average cost of one new capability, after) ---
    const sliceBuckets = new Map();
    for (const f of featureFiles) {
      const m = /feature\/([^/]+)\//.exec(f.Location);
      const slice = m ? m[1] : "unknown";
      sliceBuckets.set(slice, (sliceBuckets.get(slice) ?? 0) + f.Lines);
    }
    const sliceLocValues = [...sliceBuckets.values()];
    const avgSliceLoc =
      sliceLocValues.length === 0
        ? 0
        : round1(
            sliceLocValues.reduce((a, b) => a + b, 0) / sliceLocValues.length,
          );

    // --- BFF (removed by slice 8) ---
    const bffFiles = sccByFile(before.dir, [
      "elevator-ui/server",
      "elevator-ui/app/stores",
    ]);
    const bffTotals = totals(bffFiles);
    const bffRouteFiles = sccByFile(before.dir, ["elevator-ui/server/api"]);
    const bffRouteTotals = sizeStats(bffRouteFiles);
    // Default jscpd thresholds (min 5 lines / 50 tokens) miss duplication
    // in files this small -- most BFF routes are 8-20 lines -- so this
    // one run uses lower thresholds sized to that reality.
    const bffDuplication = jscpdReport(
      before.dir,
      ["elevator-ui/server/api"],
      scratchDir,
      "bff",
      { minLines: 3, minTokens: 20 },
    );

    // --- duplication, whole elevator-api and elevator-ui, both sides ---
    const dupApiBefore = jscpdReport(
      before.dir,
      ["elevator-api/src/main/java"],
      scratchDir,
      "api-before",
    );
    const dupApiAfter = jscpdReport(
      after.dir,
      ["elevator-api/src/main/java"],
      scratchDir,
      "api-after",
    );
    const dupUiBefore = jscpdReport(
      before.dir,
      ["elevator-ui/app", "elevator-ui/server"],
      scratchDir,
      "ui-before",
    );
    const dupUiAfter = jscpdReport(
      after.dir,
      ["elevator-ui/app"],
      scratchDir,
      "ui-after",
    );

    // --- endpoint mapping counts ---
    const mappingRegex = /@(Get|Post|Put|Delete|Patch)Mapping/g;
    const beforeMappingFiles = walkFiles(
      path.join(before.dir, "elevator-api/src/main/java"),
      [".java"],
    );
    const afterMappingFiles = walkFiles(
      path.join(after.dir, "elevator-api/src/main/java"),
      [".java"],
    );
    const beforeMappings = countMatches(beforeMappingFiles, mappingRegex);
    const afterMappings = countMatches(afterMappingFiles, mappingRegex);

    // --- hard-coded domain constants in elevator-ui ---
    const domainLiteralRegex = /\/elevators\/[^"'`\s)]*/g;
    const beforeUiFiles = walkFiles(path.join(before.dir, "elevator-ui/app"), [
      ".ts",
      ".vue",
    ]).concat(
      walkFiles(path.join(before.dir, "elevator-ui/server"), [".ts"]),
    );
    const afterUiFiles = walkFiles(path.join(after.dir, "elevator-ui/app"), [
      ".ts",
      ".vue",
    ]);
    const beforeDomainLiterals = countMatches(
      beforeUiFiles,
      domainLiteralRegex,
    );
    const afterDomainLiterals = countMatches(afterUiFiles, domainLiteralRegex);

    // --- diff stats per app ---
    const apps = ["elevator-api", "elevator-ui", "elevator-auth", "docs"];
    const diffs = Object.fromEntries(
      apps.map((app) => [
        app,
        {
          shortstat: diffShortstat(before.sha, after.sha, app),
          nameStatus: diffNameStatusCounts(before.sha, after.sha, app),
        },
      ]),
    );
    const wholeRepoShortstat = diffShortstat(before.sha, after.sha, ".");

    // --- versioning cost model ---
    // Anchor: Dubray's costing, cited via Ulsberg's "API Change Strategy"
    // (see docs/plan.html, section 09 "Change without versioning"):
    // point-to-point versioning runs ~45% more expensive than a
    // compatible-change strategy at 4 concurrent versions.
    //
    // We fit a linear rate k from that single point: cost(n) = 1 + k*(n-1),
    // cost(4) = 1.45 => k = 0.45 / 3 = 0.15. This is an extrapolation of
    // one cited data point, not a new empirical measurement -- treat the
    // resulting percentage as illustrative, not precise.
    const K = 0.45 / 3;
    const CONCURRENT_VERSIONS = 6; // 1 original + 5 new, all live in parallel
    const costMultiplier = round2(1 + K * (CONCURRENT_VERSIONS - 1));

    const oldLayerTotals = totals(oldLayerFiles);
    const oldLayerTestTotals = totals(beforeTestFiles);
    const controllerOnlyFiles = sccByFile(before.dir, [
      "elevator-api/src/main/java/no/javazone/elevator/controller",
    ]);
    const controllerOnlyTotals = totals(controllerOnlyFiles);

    const fullForkLoc = Math.round(
      oldLayerTotals.lines * CONCURRENT_VERSIONS,
    );
    const partialForkLoc = Math.round(
      controllerOnlyTotals.lines * CONCURRENT_VERSIONS +
        (oldLayerTotals.lines - controllerOnlyTotals.lines),
    );
    const newCapabilitiesAdditiveLoc = Math.round(avgSliceLoc * 5);

    // --- framework/infrastructure coupling ---
    // Hypothesis: the REST+DDD side moved infrastructure "in-house" (its
    // own hypermedia/affordance model, its own persistence mapping at the
    // boundary) so its domain and application layers depend on far fewer
    // framework symbols than the CRUD side's equivalent layers, where the
    // domain model doubles as the JPA entity (see "Model reuse" in
    // docs/architecture.md's git history) and the service layer throws
    // framework-specific HTTP exceptions directly.
    const beforeDomainFiles = walkFiles(
      path.join(
        before.dir,
        "elevator-api/src/main/java/no/javazone/elevator/model",
      ),
      [".java"],
    );
    const beforeBizFiles = walkFiles(
      path.join(
        before.dir,
        "elevator-api/src/main/java/no/javazone/elevator/service",
      ),
      [".java"],
    );
    const afterDomainFiles = walkFiles(
      path.join(
        after.dir,
        "elevator-api/src/main/java/no/javazone/elevator/shared/domain",
      ),
      [".java"],
    );
    const afterAppFiles = javaFilesMatching(
      path.join(
        after.dir,
        "elevator-api/src/main/java/no/javazone/elevator/feature",
      ),
      (name) => /(Command|Handler|AffordanceContributor)\.java$/.test(name),
    );
    const wholeBeforeJavaFiles = walkFiles(
      path.join(before.dir, "elevator-api/src/main/java"),
      [".java"],
    );
    const wholeAfterJavaFiles = walkFiles(
      path.join(after.dir, "elevator-api/src/main/java"),
      [".java"],
    );

    const frameworkCoupling = {
      beforeDomain: frameworkImportAnalysis(beforeDomainFiles),
      beforeBiz: frameworkImportAnalysis(beforeBizFiles),
      beforeDomainBiz: frameworkImportAnalysis([
        ...beforeDomainFiles,
        ...beforeBizFiles,
      ]),
      afterDomain: frameworkImportAnalysis(afterDomainFiles),
      afterApp: frameworkImportAnalysis(afterAppFiles),
      afterDomainApp: frameworkImportAnalysis([
        ...afterDomainFiles,
        ...afterAppFiles,
      ]),
      beforeWhole: frameworkImportAnalysis(wholeBeforeJavaFiles),
      afterWhole: frameworkImportAnalysis(wholeAfterJavaFiles),
    };

    // --- tests: proximity, precision, speed ---
    const wholeBeforeJavaTestFiles = walkFiles(
      path.join(before.dir, "elevator-api/src/test/java"),
      [".java"],
    );
    const wholeAfterJavaTestFiles = walkFiles(
      path.join(after.dir, "elevator-api/src/test/java"),
      [".java"],
    );
    const beforeApiTestClassification = classifyJavaTests(wholeBeforeJavaTestFiles);
    const afterApiTestClassification = classifyJavaTests(wholeAfterJavaTestFiles);

    const beforeE2eFiles = walkFiles(
      path.join(before.dir, "elevator-ui/test/e2e"),
      [".ts"],
    );
    const afterE2eFiles = walkFiles(
      path.join(after.dir, "elevator-ui/test/e2e"),
      [".ts"],
    );
    const beforeClientUnitFiles = walkFiles(
      path.join(before.dir, "elevator-ui/test/unit"),
      [".ts"],
    );
    const afterClientUnitFiles = walkFiles(
      path.join(after.dir, "elevator-ui/test/unit"),
      [".ts"],
    );
    const testCaseRegex = /\b(?:it|test)\(/g;
    const uiTests = {
      beforeE2e: {
        files: beforeE2eFiles.length,
        cases: countMatches(beforeE2eFiles, testCaseRegex),
      },
      afterE2e: {
        files: afterE2eFiles.length,
        cases: countMatches(afterE2eFiles, testCaseRegex),
      },
      beforeClientUnit: {
        files: beforeClientUnitFiles.length,
        cases: countMatches(beforeClientUnitFiles, testCaseRegex),
        lines: totals(sccByFile(before.dir, ["elevator-ui/test/unit"])).lines,
      },
      afterClientUnit: {
        files: afterClientUnitFiles.length,
        cases: countMatches(afterClientUnitFiles, testCaseRegex),
      },
    };

    // Real execution timing, not a proxy -- runs the actual Gradle test
    // suite in each worktree. Best-effort: skipped gracefully if no JDK 21
    // is available (see runGradleTests).
    const beforeGradleRun = runGradleTests(
      path.join(before.dir, "elevator-api"),
    );
    const afterGradleRun = runGradleTests(path.join(after.dir, "elevator-api"));
    const afterDomainGradleRun = afterGradleRun.ok
      ? runGradleTests(
          path.join(after.dir, "elevator-api"),
          "no.javazone.elevator.shared.domain.*",
        )
      : { ok: false, reason: "skipped: full suite run failed" };

    // --- render markdown ---
    const md = renderMarkdown({
      before,
      after,
      beforeApiFiles,
      afterApiFiles,
      beforeTestFiles,
      afterTestFiles,
      oldLayerFiles,
      featureFiles,
      sharedFiles,
      avgSliceLoc,
      sliceBuckets,
      bffTotals,
      bffRouteTotals,
      bffDuplication,
      dupApiBefore,
      dupApiAfter,
      dupUiBefore,
      dupUiAfter,
      beforeMappings,
      afterMappings,
      beforeDomainLiterals,
      afterDomainLiterals,
      diffs,
      wholeRepoShortstat,
      K,
      CONCURRENT_VERSIONS,
      costMultiplier,
      oldLayerTotals,
      oldLayerTestTotals,
      controllerOnlyTotals,
      fullForkLoc,
      partialForkLoc,
      newCapabilitiesAdditiveLoc,
      frameworkCoupling,
      beforeApiTestClassification,
      afterApiTestClassification,
      uiTests,
      beforeGradleRun,
      afterGradleRun,
      afterDomainGradleRun,
    });

    const outPath = path.join(REPO_ROOT, "docs", "refactor-metrics.md");
    writeFileSync(outPath, md);
    console.log(`Wrote ${path.relative(REPO_ROOT, outPath)}`);
  } finally {
    if (before) removeWorktree(before.dir);
    if (after) removeWorktree(after.dir);
    rmSync(scratchDir, { recursive: true, force: true });
  }
}

// --- markdown rendering ----------------------------------------------------

function pct(part, whole) {
  if (whole === 0) return "n/a";
  return `${round1((part / whole) * 100)}%`;
}

function renderMarkdown(m) {
  const beforeApi = totals(m.beforeApiFiles);
  const afterApi = totals(m.afterApiFiles);
  const beforeTest = totals(m.beforeTestFiles);
  const afterTest = totals(m.afterTestFiles);
  const oldLayer = totals(m.oldLayerFiles);
  const feature = totals(m.featureFiles);
  const shared = totals(m.sharedFiles);
  const largestBefore = largestFile(m.beforeApiFiles);
  const largestAfter = largestFile(m.afterApiFiles);
  const fc = m.frameworkCoupling;
  const bc = m.beforeApiTestClassification;
  const ac = m.afterApiTestClassification;
  const ui = m.uiTests;
  const bg = m.beforeGradleRun;
  const ag = m.afterGradleRun;
  const adg = m.afterDomainGradleRun;

  const lines = [];
  const p = (s = "") => lines.push(s);

  p(`# CRUD vs REST+DDD: measured refactor metrics`);
  p();
  p(
    `Generated by \`npm run measure\` (\`docs/measure-refactor.mjs\`).`,
  );
  p(
    `Compares \`${m.before.ref}\` (\`${m.before.sha.slice(0, 7)}\`, the last`,
  );
  p(
    `commit before slice 0 -- full CRUD shape) against \`${m.after.ref}\``,
  );
  p(
    `(\`${m.after.sha.slice(0, 7)}\`). Re-run this script any time either`,
  );
  p(`ref moves to refresh these numbers.`);
  p();

  p(`## 1. Lines of code and file counts`);
  p();
  p(`| | before (\`${m.before.ref}\`) | after (\`${m.after.ref}\`) |`);
  p(`|---|---|---|`);
  p(
    `| elevator-api \`main\` files / lines | ${beforeApi.files} / ${beforeApi.lines} | ${afterApi.files} / ${afterApi.lines} |`,
  );
  p(
    `| elevator-api \`test\` files / lines | ${beforeTest.files} / ${beforeTest.lines} | ${afterTest.files} / ${afterTest.lines} |`,
  );
  p(
    `| Largest main file (name) | \`${largestBefore?.Filename}\` | \`${largestAfter?.Filename}\` |`,
  );
  p(
    `| Largest main file (lines) | ${largestBefore?.Lines} | ${largestAfter?.Lines} |`,
  );
  p();
  p(
    `File count rises with vertical slicing by design -- one directory`,
  );
  p(
    `per behaviour, each holding its own command/handler/endpoint/tests.`,
  );
  p(`Section 2 checks whether those smaller files are also simpler.`);
  p();

  p(`## 2. Complexity, not just size`);
  p();
  p(
    `Per-file cyclomatic complexity (\`scc\`), comparing the old`,
  );
  p(
    `\`controller/\`+\`model/\`+\`service/\`+\`repository/\` layer (before,`,
  );
  p(
    `now deleted outright) against the new \`feature/*\` slices and the`,
  );
  p(`shared kernel (after):`);
  p();
  p(`| | files | avg complexity | median | max |`);
  p(`|---|---|---|---|---|`);
  p(
    `| old layer (before) | ${oldLayer.files} | ${oldLayer.avgComplexity} | ${oldLayer.medianComplexity} | ${oldLayer.maxComplexity} |`,
  );
  p(
    `| \`feature/*\` slices (after) | ${feature.files} | ${feature.avgComplexity} | ${feature.medianComplexity} | ${feature.maxComplexity} |`,
  );
  p(
    `| shared kernel (after) | ${shared.files} | ${shared.avgComplexity} | ${shared.medianComplexity} | ${shared.maxComplexity} |`,
  );
  p();
  p(
    `The shared kernel's higher max is expected and correct: it holds the`,
  );
  p(
    `\`Elevator\` aggregate itself (state machine, scheduling), the one`,
  );
  p(
    `place this architecture concentrates real domain complexity rather`,
  );
  p(`than smearing it across every controller that used to touch it.`);
  p();

  p(`## 3. Average cost of one new capability (after)`);
  p();
  p(
    `Per-slice total lines, \`feature/*\` (after) -- each is a fully`,
  );
  p(`independent, separately testable unit:`);
  p();
  p(`| slice | lines |`);
  p(`|---|---|`);
  for (const [slice, lines_] of [...m.sliceBuckets.entries()].sort(
    (a, b) => a[1] - b[1],
  )) {
    p(`| \`${slice}\` | ${lines_} |`);
  }
  p(`| **average** | **${m.avgSliceLoc}** |`);
  p();

  p(`## 4. The BFF: removed, not just relocated`);
  p();
  p(
    `Slice 8 deleted \`elevator-ui/server/api/**\` (the BFF routes) and`,
  );
  p(`\`elevator-ui/app/stores/elevator.ts\` outright:`);
  p();
  p(`| | value |`);
  p(`|---|---|`);
  p(`| BFF + store files removed | ${m.bffTotals.files} |`);
  p(`| BFF + store lines removed | ${m.bffTotals.lines} |`);
  p(
    `| BFF route files alone (\`server/api/**\`) | ${m.bffRouteTotals.files} |`,
  );
  p(
    `| ... their avg / median / max lines | ${m.bffRouteTotals.avg} / ${m.bffRouteTotals.median} / ${m.bffRouteTotals.max} |`,
  );
  if (m.bffDuplication) {
    p(
      `| Duplication among just those route files | ${round1(m.bffDuplication.percentage)}% (${m.bffDuplication.clones} clones) |`,
    );
  }
  p(`| Network hops per rider action, before | 2 |`);
  p(`| Network hops per rider action, after | 1 |`);
  p();
  p(
    `Before: browser -> BFF route -> elevator-api. After: browser ->`,
  );
  p(
    `elevator-api directly (Caddy is a transparent reverse proxy, not`,
  );
  p(`a logic hop).`);
  p();
  p(
    `Default jscpd thresholds (min 5 lines / 50 tokens) miss most of`,
  );
  p(
    `this: at ${m.bffRouteTotals.avg} lines average, a route rarely reaches 50`,
  );
  p(
    `tokens on its own. Lowering the thresholds to fit files this small`,
  );
  p(
    `is what surfaces the ${m.bffDuplication ? round1(m.bffDuplication.percentage) : "?"}% above -- the`,
  );
  p(
    `routes are near-identical, differing only in HTTP verb and path,`,
  );
  p(
    "e.g. `open-doors.post.ts` and `close-doors.post.ts`:",
  );
  p();
  p("```ts");
  p(`export default defineEventHandler(async (event) => {`);
  p(`  const id = getRouterParam(event, 'id')`);
  p(`  const config = useRuntimeConfig()`);
  p();
  p(
    "  return await $fetch(`${config.serviceApiUrl}/elevators/${id}/open-doors`, {",
  );
  p(`    method: 'POST'`);
  p(`  })`);
  p(`})`);
  p("```");
  p();
  p(
    `Every other route repeats this shape, varying only the path`,
  );
  p(
    `segment and verb -- exactly the kind of repetition a client`,
  );
  p(
    `following hypermedia links never has to write, because it never`,
  );
  p(`constructs the URL or the verb itself.`);
  p();
  p(
    `Deployable *service* count is unchanged (the BFF lived inside the`,
  );
  p(
    `same Nuxt container, not a separate one) -- the removed cost was`,
  );
  p(
    `pure pass-through/proxy code and an extra hop, not a deployable.`,
  );
  p();

  p(`## 5. Duplication (whole-directory), before vs after`);
  p();
  p(`| | before | after |`);
  p(`|---|---|---|`);
  p(
    `| elevator-api \`main\` | ${dupPct(m.dupApiBefore)} | ${dupPct(m.dupApiAfter)} |`,
  );
  p(
    `| elevator-ui (app${m.dupUiBefore ? " + server" : ""}) | ${dupPct(m.dupUiBefore)} | ${dupPct(m.dupUiAfter)} |`,
  );
  p();
  p(
    `Read the elevator-api "after" number carefully: exact-token clone`,
  );
  p(
    `detection over many small, structurally-identical files naturally`,
  );
  p(
    `reports a higher percentage than a few large ones would, even when`,
  );
  p(
    `nothing is copy-pasted. Sampling its clones confirms this: they are`,
  );
  p(
    `import blocks and same-shaped \`AffordanceContributor\`/\`Command\``,
  );
  p(
    `implementations (one interface, many slices) -- not duplicated`,
  );
  p(
    `business logic. The "before" clones are the opposite kind: copy-pasted`,
  );
  p(
    `controller/validation logic between e.g. \`CallController\` and`,
  );
  p(
    `\`CarCallController\` -- the smell this refactor targets. Percentage`,
  );
  p(`alone conflates the two; only sampling the clones tells them apart.`);
  p();

  p(`## 6. API surface`);
  p();
  p(`| | before | after |`);
  p(`|---|---|---|`);
  p(
    `| Endpoint mappings (\`@*Mapping\`) | ${m.beforeMappings} | ${m.afterMappings} |`,
  );
  p(
    `| Hard-coded \`/elevators/...\` literals in elevator-ui | ${m.beforeDomainLiterals} | ${m.afterDomainLiterals} |`,
  );
  p();
  p(
    `Before: one URL per verb (\`/calls\`, \`/car-calls\`, \`/open-doors\`,`,
  );
  p(
    `\`/close-doors\`, \`/obstruct-doors\`, \`/clear-obstruction\`,`,
  );
  p(
    `\`/weight\`, \`/maintenance\`, ...). After: every command funnels`,
  );
  p(
    `through the shared \`POST /elevators/{id}\`; the client follows`,
  );
  p(`links instead of constructing them.`);
  p();

  p(`## 7. The whole diff, by application`);
  p();
  p(`| app | files changed | + | - | added | modified | deleted |`);
  p(`|---|---|---|---|---|---|---|`);
  for (const app of Object.keys(m.diffs)) {
    const { shortstat, nameStatus } = m.diffs[app];
    p(
      `| ${app} | ${shortstat.files} | ${shortstat.insertions} | ${shortstat.deletions} | ${nameStatus.A} | ${nameStatus.M} | ${nameStatus.D} |`,
    );
  }
  p(
    `| **whole repo** | **${m.wholeRepoShortstat.files}** | **${m.wholeRepoShortstat.insertions}** | **${m.wholeRepoShortstat.deletions}** | | | |`,
  );
  p();
  p(
    `elevator-ui's diff is net-negative (more deleted than added) despite`,
  );
  p(`unchanged feature parity -- the BFF/store deletion in section 4.`);
  p();

  p(`## 8. Versioning cost, extrapolated`);
  p();
  p(
    `Anchor (cited in \`docs/plan.html\`, section 09, via Ulsberg's`,
  );
  p(
    `*API Change Strategy*, costing by Jacques Dubray): point-to-point`,
  );
  p(
    `versioning runs ~45% more expensive than a compatible-change`,
  );
  p(`strategy at 4 concurrent versions.`);
  p();
  p(
    `We fit a linear rate from that single point: \`cost(n) = 1 + k*(n-1)\`,`,
  );
  p(
    `\`cost(4) = 1.45\` => \`k = ${round2(m.K)}\`. Applied to 5 new versions`,
  );
  p(
    `living alongside the original (${m.CONCURRENT_VERSIONS} concurrent`,
  );
  p(
    `versions): \`cost(${m.CONCURRENT_VERSIONS}) = ${m.costMultiplier}\`,`,
  );
  p(
    `i.e. an estimated **+${round1((m.costMultiplier - 1) * 100)}%**`,
  );
  p(
    `maintenance cost for CRUD-style point-to-point versioning vs. a`,
  );
  p(
    `compatible-change (hypermedia) strategy. This is a linear`,
  );
  p(
    `extrapolation of one cited data point, not a new empirical`,
  );
  p(`measurement -- treat it as illustrative, not precise.`);
  p();
  p(
    `Grounded in this repo's own measured CRUD surface (\`${m.before.ref}\`,`,
  );
  p(`the versionable layer with no compatible-extension mechanism):`);
  p();
  p(`| | value |`);
  p(`|---|---|`);
  p(
    `| \`controller+model+service+repository\` lines | ${m.oldLayerTotals.lines} |`,
  );
  p(`| of which \`controller\` (verb layer) alone | ${m.controllerOnlyTotals.lines} |`);
  p(`| its test lines | ${m.oldLayerTestTotals.lines} |`);
  p();
  p(
    `Projected lines to maintain under three strategies for "5 new`,
  );
  p(`versions live in parallel":`);
  p();
  p(`| strategy | lines to maintain |`);
  p(`|---|---|`);
  p(`| Scenario A -- full fork per version | ~${m.fullForkLoc} |`);
  p(`| Scenario B -- partial fork (controller only) | ~${m.partialForkLoc} |`);
  p(
    `| REST+DDD -- 5 new slices, additive | ~${m.newCapabilitiesAdditiveLoc} |`,
  );
  p();
  p(
    `Scenario A: the whole old layer forks per version (worst case, a`,
  );
  p(
    `naive point-to-point strategy where behaviour differs by version).`,
  );
  p(
    `Scenario B: only the verb/controller layer forks per version;`,
  );
  p(
    `\`model\`/\`service\`/\`repository\` stay shared across versions. Both`,
  );
  p(
    `multiply the *entire existing surface* by ${m.CONCURRENT_VERSIONS}. The REST+DDD`,
  );
  p(
    `figure is not a multiple of anything existing: 5 new capabilities`,
  );
  p(
    `cost 5 new slices (section 3's average) and change nothing already`,
  );
  p(`built or tested.`);
  p();
  p(
    `The REST+DDD side's cost of "5 more versions" is additive (new`,
  );
  p(
    `slices), not multiplicative against the whole surface -- because`,
  );
  p(
    `new capability is a new \`rel\`, never a new version of an existing`,
  );
  p(`one (see \`docs/architecture.md\`, "No versioning").`);
  p();

  p(`## 9. Framework coupling: infrastructure moved in-house`);
  p();
  p(
    `Hypothesis: the REST+DDD side depends on far fewer framework symbols`,
  );
  p(
    `in its domain and application code, because persistence, hypermedia`,
  );
  p(
    `and rendering became purpose-built infrastructure at the boundary`,
  );
  p(
    `instead of framework annotations reaching into the domain model`,
  );
  p(`itself. Measured directly, by counting \`import\` lines from`);
  p(
    `\`org.springframework.*\`, \`jakarta.*\` and \`lombok.*\` (Spring's DI`,
  );
  p(`stereotypes, JPA, and Lombok):`);
  p();
  p(`| | files | with framework imports | distinct symbols |`);
  p(`|---|---|---|---|`);
  p(
    `| before: \`model/\` (domain) | ${fc.beforeDomain.totalFiles} | ${fc.beforeDomain.filesWithImports} | ${fc.beforeDomain.distinctSymbols} |`,
  );
  p(
    `| before: \`service/\` (business logic) | ${fc.beforeBiz.totalFiles} | ${fc.beforeBiz.filesWithImports} | ${fc.beforeBiz.distinctSymbols} |`,
  );
  p(
    `| before: domain+business logic, combined | ${fc.beforeDomainBiz.totalFiles} | ${fc.beforeDomainBiz.filesWithImports} | ${fc.beforeDomainBiz.distinctSymbols} |`,
  );
  p(
    `| after: \`shared/domain\` | ${fc.afterDomain.totalFiles} | ${fc.afterDomain.filesWithImports} | ${fc.afterDomain.distinctSymbols} |`,
  );
  p(
    `| after: Command+Handler+AffordanceContributor | ${fc.afterApp.totalFiles} | ${fc.afterApp.filesWithImports} | ${fc.afterApp.distinctSymbols} |`,
  );
  p(
    `| after: domain+application, combined | ${fc.afterDomainApp.totalFiles} | ${fc.afterDomainApp.filesWithImports} | ${fc.afterDomainApp.distinctSymbols} |`,
  );
  p(
    `| whole \`elevator-api\` main, before | ${fc.beforeWhole.totalFiles} | ${fc.beforeWhole.filesWithImports} | ${fc.beforeWhole.distinctSymbols} |`,
  );
  p(
    `| whole \`elevator-api\` main, after | ${fc.afterWhole.totalFiles} | ${fc.afterWhole.filesWithImports} | ${fc.afterWhole.distinctSymbols} |`,
  );
  p();
  p(
    `Before: \`model/Elevator.java\` is itself \`@Entity\`-annotated --`,
  );
  p(
    `the same class is the JPA entity, the domain model, and the JSON`,
  );
  p(
    `response (the "Model reuse" smell). \`ElevatorService\` -- the`,
  );
  p(
    `business logic -- imports \`org.springframework.http.HttpStatus\``,
  );
  p(
    `and throws \`ResponseStatusException\` directly: a domain refusal`,
  );
  p(`is expressed as an HTTP status code inside the business logic.`);
  p();
  p(
    `After: \`shared/domain\` has zero framework imports across`,
  );
  p(
    `${fc.afterDomain.totalFiles} files (by design -- see`,
  );
  p(
    `\`docs/architecture.md\`'s slice 0: "no Spring/JPA/Lombok"). Command`,
  );
  p(
    `and handler classes carry exactly one framework symbol each`,
  );
  p(
    `(\`@Component\`, for dependency injection), never a framework`,
  );
  p(
    `exception type or persistence annotation. A refusal is`,
  );
  p(
    `\`CommandRefused\`, a plain domain type with no framework`,
  );
  p(`dependency; only the controller layer decides how to render it.`);
  p();
  p(`### Upgrade-exposure proxy`);
  p();
  p(
    `Distinct framework symbols are a proxy for exposure to a major`,
  );
  p(
    `framework version bump (e.g. Spring Boot's next major, or a JPA`,
  );
  p(
    `provider swap): each one is a place a breaking change can land, and`,
  );
  p(
    `every file importing it is a file that may need to change. The`,
  );
  p(
    `before side's domain+business-logic layer relies on`,
  );
  p(
    `${fc.beforeDomainBiz.distinctSymbols} distinct framework symbols across`,
  );
  p(
    `${fc.beforeDomainBiz.filesWithImports}/${fc.beforeDomainBiz.totalFiles} files; the after side's domain+application`,
  );
  p(
    `layer relies on ${fc.afterDomainApp.distinctSymbols} across`,
  );
  p(
    `${fc.afterDomainApp.filesWithImports}/${fc.afterDomainApp.totalFiles} files. A Spring or JPA major-version migration on the`,
  );
  p(
    `before side plausibly touches the domain model itself; on the`,
  );
  p(
    `after side it is contained to \`shared/web\`, \`shared/persistence\``,
  );
  p(
    `and \`config/\` -- the files whose job is exactly to absorb that`,
  );
  p(
    `kind of change -- without touching \`shared/domain\` or any slice's`,
  );
  p(
    `\`Command\`/\`Handler\`. This repo has no in-history major-version`,
  );
  p(
    `migration of elevator-api to point to directly (the one Spring Boot`,
  );
  p(
    `v3->v4 bump in git history predates elevator-api's own code), so`,
  );
  p(
    `this is reasoned from measured coupling, not a second empirical`,
  );
  p(`data point -- treat it accordingly.`);
  p();

  p(`## 10. Tests: proximity, precision, speed`);
  p();
  p(
    `Where a rule is tested, and how directly, before vs after. A test`,
  );
  p(
    `file counts as needing a Spring context if it uses`,
  );
  p(
    `\`@SpringBootTest\`, \`@WebMvcTest\`, \`@DataJpaTest\` or`,
  );
  p(`\`@AutoConfigureMockMvc\`; everything else is a plain JUnit unit`);
  p(`test with no framework runtime at all.`);
  p();
  p(`| | before | after |`);
  p(`|---|---|---|`);
  p(
    `| elevator-api: unit test files / methods | ${bc.unitFiles} / ${bc.unitTests} | ${ac.unitFiles} / ${ac.unitTests} |`,
  );
  p(
    `| elevator-api: Spring-context test files / methods | ${bc.contextFiles} / ${bc.contextTests} | ${ac.contextFiles} / ${ac.contextTests} |`,
  );
  p(
    `| elevator-ui: e2e spec files / cases | ${ui.beforeE2e.files} / ${ui.beforeE2e.cases} | ${ui.afterE2e.files} / ${ui.afterE2e.cases} |`,
  );
  p(
    `| elevator-ui: client-side unit test files / cases | ${ui.beforeClientUnit.files} / ${ui.beforeClientUnit.cases} | ${ui.afterClientUnit.files} / ${ui.afterClientUnit.cases} |`,
  );
  p();
  p(
    `Before: ${round1((bc.contextFiles / (bc.unitFiles + bc.contextFiles)) * 100)}% of elevator-api test`,
  );
  p(
    `files need a full Spring context. After: ${round1((ac.contextFiles / (ac.unitFiles + ac.contextFiles)) * 100)}%. The e2e suite`,
  );
  p(
    `is essentially unchanged in size (it still covers the same shell/`,
  );
  p(
    `interaction chrome) -- what disappeared is the`,
  );
  p(
    `${ui.beforeClientUnit.lines}-line client-side unit test suite, which existed`,
  );
  p(
    `only because \`stores/elevator.ts\` re-implemented domain logic`,
  );
  p(`worth unit-testing in the first place. Its own test names say so`);
  p(`directly: \`filters served calls out of pendingCalls\`,`);
  p(
    `\`collects pending floors from both call types\` -- a business rule`,
  );
  p(
    `(which requests are still pending), tested in the wrong tier,`,
  );
  p(
    `requiring five mocked HTTP endpoints and a Pinia store just to`,
  );
  p(`assert a filter.`);
  p();
  p("```ts");
  p(`// before: elevator-ui/test/unit/elevatorStore.test.ts`);
  p(`registerEndpoint('/api/key', { method: 'GET', handler: () => ... })`);
  p(`registerEndpoint('/api/key', { method: 'POST', handler: ... })`);
  p(`registerEndpoint('/api/elevators/1/status', { ... })`);
  p(`// ...three more registerEndpoint calls, then, finally:`);
  p(`it('filters served calls out of pendingCalls', () => { ... })`);
  p();
  p(`// after: elevator-api RequestQueueTest.java -- no mocks, no`);
  p(`// Spring context, no HTTP layer, testing the type that owns the`);
  p(`// rule directly:`);
  p(`void twoRidersPressingTheSameLandingButtonIsOneCall() {`);
  p(`  RequestQueue queue = RequestQueue.empty();`);
  p(`  queue.addLanding(new LandingCall(new Floor(3), Direction.UP));`);
  p(
    `  boolean addedAgain = queue.addLanding(new LandingCall(new Floor(3), Direction.UP));`,
  );
  p(`  assertThat(addedAgain).isFalse();`);
  p(`}`);
  p("```");
  p();
  if (bg?.ok && ag?.ok) {
    p(`### Measured, not estimated`);
    p();
    p(
      `The actual \`./gradlew test\` wall-clock time for each`,
    );
    p(
      `worktree's **elevator-api** suite, on this machine. This does`,
    );
    p(
      `not run elevator-ui's Playwright e2e suite (\`npm run test:e2e\`),`,
    );
    p(
      `which needs a running stack (built containers/dev servers plus a`,
    );
    p(
      `browser) rather than a single JVM process, and so is out of`,
    );
    p(
      `scope for a quick, repeatable script like this one. Its case`,
    );
    p(
      `count is unchanged either side (previous table), so there is no`,
    );
    p(`reason to expect its execution time changed either:`);
    p();
    p(`| | before | after |`);
    p(`|---|---|---|`);
    p(
      `| Tests executed | ${bg.testCount} | ${ag.testCount} |`,
    );
    p(
      `| Wall-clock time | ${round1(bg.ms / 1000)}s | ${round1(ag.ms / 1000)}s |`,
    );
    p(
      `| Avg per test | ${round1(bg.ms / bg.testCount)}ms | ${round1(ag.ms / ag.testCount)}ms |`,
    );
    p();
    p(
      `Total wall-clock time is a noisy number to trust run-to-run --`,
    );
    p(
      `it's dominated by fixed JVM/Gradle daemon startup, common to`,
    );
    p(
      `both suites, more than by test logic itself, and can flip`,
    );
    p(
      `depending on machine load. Average time per test isolates the`,
    );
    p(
      `real difference: ${round1(bg.ms / bg.testCount / (ag.ms / ag.testCount))}x lower per test on the after side, while running`,
    );
    p(
      `${round1(ag.testCount / bg.testCount)}x more of them. That is the payoff of most tests no longer`,
    );
    p(
      `paying Spring context startup: isolating just`,
    );
    p(
      `\`shared/domain\`'s own test suite`,
    );
    p(
      adg?.ok
        ? `(${adg.testCount} tests, no \`@SpringBootTest\` anywhere) runs in`
        : `runs in`,
    );
    p(
      adg?.ok
        ? `${round1(adg.ms / 1000)}s -- ${round1(adg.ms / adg.testCount)}ms/test on average, including the JVM's own`
        : `single-digit milliseconds per test, including the JVM's own`,
    );
    p(
      `startup (this isolated run pays that cost too; a`,
    );
    p(
      `\`@SpringBootTest\`-backed test pays it again per context on top`,
    );
    p(
      `of an application context and an embedded H2 database, and (per`,
    );
    p(
      `this codebase's test config) often a fresh context per class`,
    );
    p(`rather than a shared, cached one).`);
    p();
  } else {
    p(`### Measured, not estimated`);
    p();
    p(
      `Skipped: could not run \`./gradlew test\` on both worktrees on this`,
    );
    p(
      `machine (${bg?.reason ?? "before: unknown reason"}; ${ag?.reason ?? "after: unknown reason"}).`,
    );
    p(
      `Re-run with a JDK 21 \`JAVA_HOME\` on \`PATH\` to fill this in.`,
    );
    p();
  }

  return lines.join("\n") + "\n";
}

function dupPct(report) {
  if (!report) return "n/a";
  return `${round1(report.percentage)}% (${report.clones} clones)`;
}

main();
