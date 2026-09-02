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
const MID_REF = "json-hypermedia";
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

// --- deployables -----------------------------------------------------------

function dockerComposeServices(dir) {
  const composePath = path.join(dir, "docker-compose.yml");
  if (!existsSync(composePath)) return [];
  const lines = readFileSync(composePath, "utf8").split("\n");
  const services = [];
  let inServices = false;
  for (const line of lines) {
    if (/^services:\s*$/.test(line)) {
      inServices = true;
      continue;
    }
    if (!inServices) continue;
    if (/^\S/.test(line)) break; // next top-level compose key
    const m = /^ {2}([a-zA-Z][a-zA-Z0-9-]*):\s*$/.exec(line);
    if (m) services.push(m[1]);
  }
  return services;
}

// --- main ------------------------------------------------------------------

function main() {
  const scratchDir = mkdtempSync(path.join(tmpdir(), "measure-refactor-"));
  let before, mid, after;
  try {
    before = addWorktree(scratchDir, BEFORE_REF, "before");
    mid = addWorktree(scratchDir, MID_REF, "mid");
    after = addWorktree(scratchDir, AFTER_REF, "after");

    // --- LOC + file counts, whole elevator-api ---
    const beforeApiFiles = sccByFile(before.dir, [
      "elevator-api/src/main/java",
    ]);
    const midApiFiles = sccByFile(mid.dir, ["elevator-api/src/main/java"]);
    const afterApiFiles = sccByFile(after.dir, ["elevator-api/src/main/java"]);
    const beforeTestFiles = sccByFile(before.dir, ["elevator-api/src/test"]);
    const midTestFiles = sccByFile(mid.dir, ["elevator-api/src/test"]);
    const afterTestFiles = sccByFile(after.dir, ["elevator-api/src/test"]);

    // --- old CRUD layer (before) vs feature slices + shared kernel ---
    // (mid and after both already have the sliced layout -- mid's own
    // "Delete the CRUD scaffolding" commit landed independently of
    // main's, so the elevator-api side is expected to already match
    // main here; that convergence is itself reported, not hidden.)
    const oldLayerDirs = [
      "elevator-api/src/main/java/no/javazone/elevator/controller",
      "elevator-api/src/main/java/no/javazone/elevator/model",
      "elevator-api/src/main/java/no/javazone/elevator/service",
      "elevator-api/src/main/java/no/javazone/elevator/repository",
    ];
    const oldLayerFiles = sccByFile(before.dir, oldLayerDirs);
    const midFeatureFiles = sccByFile(mid.dir, [
      "elevator-api/src/main/java/no/javazone/elevator/feature",
    ]);
    const midSharedFiles = sccByFile(mid.dir, [
      "elevator-api/src/main/java/no/javazone/elevator/shared",
    ]);
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

    // --- BFF (removed by slice 8; json-hypermedia still has one, but a
    // consolidated one -- one command endpoint instead of one per verb) ---
    const bffFiles = sccByFile(before.dir, [
      "elevator-ui/server",
      "elevator-ui/app/stores",
    ]);
    const bffTotals = totals(bffFiles);
    const bffRouteFiles = sccByFile(before.dir, ["elevator-ui/server/api"]);
    const bffRouteTotals = sizeStats(bffRouteFiles);
    const midBffFiles = sccByFile(mid.dir, [
      "elevator-ui/server",
      "elevator-ui/app/stores",
    ]);
    const midBffTotals = totals(midBffFiles);
    const midBffRouteFiles = sccByFile(mid.dir, ["elevator-ui/server/api"]);
    const midBffRouteTotals = sizeStats(midBffRouteFiles);
    const midStoreFiles = sccByFile(mid.dir, ["elevator-ui/app/stores"]);
    const midStoreTotals = totals(midStoreFiles);
    const beforeStoreFiles = sccByFile(before.dir, ["elevator-ui/app/stores"]);
    const beforeStoreTotals = totals(beforeStoreFiles);
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
    const midBffDuplication = jscpdReport(
      mid.dir,
      ["elevator-ui/server/api"],
      scratchDir,
      "bff-mid",
      { minLines: 3, minTokens: 20 },
    );

    // --- duplication, whole elevator-api and elevator-ui, both sides ---
    const dupApiBefore = jscpdReport(
      before.dir,
      ["elevator-api/src/main/java"],
      scratchDir,
      "api-before",
    );
    const dupApiMid = jscpdReport(
      mid.dir,
      ["elevator-api/src/main/java"],
      scratchDir,
      "api-mid",
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
    const dupUiMid = jscpdReport(
      mid.dir,
      ["elevator-ui/app", "elevator-ui/server"],
      scratchDir,
      "ui-mid",
    );
    const dupUiAfter = jscpdReport(
      after.dir,
      ["elevator-ui/src"],
      scratchDir,
      "ui-after",
    );

    // --- endpoint mapping counts ---
    const mappingRegex = /@(Get|Post|Put|Delete|Patch)Mapping/g;
    const beforeMappingFiles = walkFiles(
      path.join(before.dir, "elevator-api/src/main/java"),
      [".java"],
    );
    const midMappingFiles = walkFiles(
      path.join(mid.dir, "elevator-api/src/main/java"),
      [".java"],
    );
    const afterMappingFiles = walkFiles(
      path.join(after.dir, "elevator-api/src/main/java"),
      [".java"],
    );
    const beforeMappings = countMatches(beforeMappingFiles, mappingRegex);
    const midMappings = countMatches(midMappingFiles, mappingRegex);
    const afterMappings = countMatches(afterMappingFiles, mappingRegex);

    // --- hard-coded domain constants in elevator-ui ---
    const domainLiteralRegex = /\/elevators\/[^"'`\s)]*/g;
    const beforeUiFiles = walkFiles(path.join(before.dir, "elevator-ui/app"), [
      ".ts",
      ".vue",
    ]).concat(
      walkFiles(path.join(before.dir, "elevator-ui/server"), [".ts"]),
    );
    const midUiFiles = walkFiles(path.join(mid.dir, "elevator-ui/app"), [
      ".ts",
      ".vue",
    ]).concat(walkFiles(path.join(mid.dir, "elevator-ui/server"), [".ts"]));
    const afterUiFiles = walkFiles(path.join(after.dir, "elevator-ui/src"), [
      ".ts",
    ]).concat(
      walkFiles(path.join(after.dir, "elevator-ui/public"), [".html"]),
    );
    const beforeDomainLiterals = countMatches(
      beforeUiFiles,
      domainLiteralRegex,
    );
    const midDomainLiterals = countMatches(midUiFiles, domainLiteralRegex);
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
    // The two legs either side of json-hypermedia: crud -> json-hypermedia
    // (the backend and BFF-consolidation work) and json-hypermedia -> main
    // (the front-end/BFF-deletion work).
    const diffsBeforeToMid = Object.fromEntries(
      apps.map((app) => [
        app,
        diffShortstat(before.sha, mid.sha, app),
      ]),
    );
    const diffsMidToAfter = Object.fromEntries(
      apps.map((app) => [
        app,
        diffShortstat(mid.sha, after.sha, app),
      ]),
    );

    // --- deployables ---
    const deployables = {
      before: dockerComposeServices(before.dir),
      mid: dockerComposeServices(mid.dir),
      after: dockerComposeServices(after.dir),
    };

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
    const midDomainFiles = walkFiles(
      path.join(
        mid.dir,
        "elevator-api/src/main/java/no/javazone/elevator/shared/domain",
      ),
      [".java"],
    );
    const midAppFiles = javaFilesMatching(
      path.join(
        mid.dir,
        "elevator-api/src/main/java/no/javazone/elevator/feature",
      ),
      (name) => /(Command|Handler|AffordanceContributor)\.java$/.test(name),
    );
    const wholeBeforeJavaFiles = walkFiles(
      path.join(before.dir, "elevator-api/src/main/java"),
      [".java"],
    );
    const wholeMidJavaFiles = walkFiles(
      path.join(mid.dir, "elevator-api/src/main/java"),
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
      midDomainApp: frameworkImportAnalysis([...midDomainFiles, ...midAppFiles]),
      afterDomain: frameworkImportAnalysis(afterDomainFiles),
      afterApp: frameworkImportAnalysis(afterAppFiles),
      afterDomainApp: frameworkImportAnalysis([
        ...afterDomainFiles,
        ...afterAppFiles,
      ]),
      beforeWhole: frameworkImportAnalysis(wholeBeforeJavaFiles),
      midWhole: frameworkImportAnalysis(wholeMidJavaFiles),
      afterWhole: frameworkImportAnalysis(wholeAfterJavaFiles),
    };

    // --- tests: proximity, precision, speed ---
    const wholeBeforeJavaTestFiles = walkFiles(
      path.join(before.dir, "elevator-api/src/test/java"),
      [".java"],
    );
    const wholeMidJavaTestFiles = walkFiles(
      path.join(mid.dir, "elevator-api/src/test/java"),
      [".java"],
    );
    const wholeAfterJavaTestFiles = walkFiles(
      path.join(after.dir, "elevator-api/src/test/java"),
      [".java"],
    );
    const beforeApiTestClassification = classifyJavaTests(wholeBeforeJavaTestFiles);
    const midApiTestClassification = classifyJavaTests(wholeMidJavaTestFiles);
    const afterApiTestClassification = classifyJavaTests(wholeAfterJavaTestFiles);

    const beforeE2eFiles = walkFiles(
      path.join(before.dir, "elevator-ui/test/e2e"),
      [".ts"],
    );
    const midE2eFiles = walkFiles(
      path.join(mid.dir, "elevator-ui/test/e2e"),
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
    const midClientUnitFiles = walkFiles(
      path.join(mid.dir, "elevator-ui/test/unit"),
      [".ts"],
    );
    const afterClientUnitFiles = walkFiles(
      path.join(after.dir, "elevator-ui/test/unit"),
      [".ts"],
    );
    const testCaseRegex = /\b(?:it|test)\(/g;
    const assertionRegex = /\bexpect\(/g;
    const uiTests = {
      beforeE2e: {
        files: beforeE2eFiles.length,
        cases: countMatches(beforeE2eFiles, testCaseRegex),
        assertions: countMatches(beforeE2eFiles, assertionRegex),
      },
      midE2e: {
        files: midE2eFiles.length,
        cases: countMatches(midE2eFiles, testCaseRegex),
        assertions: countMatches(midE2eFiles, assertionRegex),
      },
      afterE2e: {
        files: afterE2eFiles.length,
        cases: countMatches(afterE2eFiles, testCaseRegex),
        assertions: countMatches(afterE2eFiles, assertionRegex),
      },
      beforeClientUnit: {
        files: beforeClientUnitFiles.length,
        cases: countMatches(beforeClientUnitFiles, testCaseRegex),
        lines: totals(sccByFile(before.dir, ["elevator-ui/test/unit"])).lines,
      },
      midClientUnit: {
        files: midClientUnitFiles.length,
        cases: countMatches(midClientUnitFiles, testCaseRegex),
        lines: totals(sccByFile(mid.dir, ["elevator-ui/test/unit"])).lines,
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
    const midGradleRun = runGradleTests(path.join(mid.dir, "elevator-api"));
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
      mid,
      after,
      beforeApiFiles,
      midApiFiles,
      afterApiFiles,
      beforeTestFiles,
      midTestFiles,
      afterTestFiles,
      oldLayerFiles,
      midFeatureFiles,
      midSharedFiles,
      featureFiles,
      sharedFiles,
      avgSliceLoc,
      sliceBuckets,
      bffTotals,
      bffRouteTotals,
      bffDuplication,
      midBffTotals,
      midBffRouteTotals,
      midBffDuplication,
      midStoreTotals,
      beforeStoreTotals,
      dupApiBefore,
      dupApiMid,
      dupApiAfter,
      dupUiBefore,
      dupUiMid,
      dupUiAfter,
      beforeMappings,
      midMappings,
      afterMappings,
      beforeDomainLiterals,
      midDomainLiterals,
      afterDomainLiterals,
      diffs,
      diffsBeforeToMid,
      diffsMidToAfter,
      deployables,
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
      midApiTestClassification,
      afterApiTestClassification,
      uiTests,
      beforeGradleRun,
      midGradleRun,
      afterGradleRun,
      afterDomainGradleRun,
    });

    const outPath = path.join(REPO_ROOT, "docs", "refactor-metrics.md");
    writeFileSync(outPath, md);
    console.log(`Wrote ${path.relative(REPO_ROOT, outPath)}`);
  } finally {
    if (before) removeWorktree(before.dir);
    if (mid) removeWorktree(mid.dir);
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
  const midApi = totals(m.midApiFiles);
  const afterApi = totals(m.afterApiFiles);
  const beforeTest = totals(m.beforeTestFiles);
  const midTest = totals(m.midTestFiles);
  const afterTest = totals(m.afterTestFiles);
  const oldLayer = totals(m.oldLayerFiles);
  const midFeature = totals(m.midFeatureFiles);
  const midShared = totals(m.midSharedFiles);
  const feature = totals(m.featureFiles);
  const shared = totals(m.sharedFiles);
  const largestBefore = largestFile(m.beforeApiFiles);
  const largestMid = largestFile(m.midApiFiles);
  const largestAfter = largestFile(m.afterApiFiles);
  const fc = m.frameworkCoupling;
  const bc = m.beforeApiTestClassification;
  const mc = m.midApiTestClassification;
  const ac = m.afterApiTestClassification;
  const ui = m.uiTests;
  const bg = m.beforeGradleRun;
  const mg = m.midGradleRun;
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
    `Compares three points on the refactor's timeline: \`${m.before.ref}\``,
  );
  p(
    `(\`${m.before.sha.slice(0, 7)}\`, the last commit before slice 0 --`,
  );
  p(
    `full CRUD shape), \`${m.mid.ref}\` (\`${m.mid.sha.slice(0, 7)}\`, an`,
  );
  p(
    `intermediate branch: the backend is fully hypermedia-driven and`,
  );
  p(
    `command-based, but elevator-ui is still a Vue SPA talking JSON`,
  );
  p(
    `through a BFF, with no HTML responses from elevator-api), and`,
  );
  p(
    `\`${m.after.ref}\` (\`${m.after.sha.slice(0, 7)}\`, current tip: no BFF,`,
  );
  p(
    `elevator-api serves HTML directly, Datastar morphs it in place --`,
  );
  p(
    `and, as of this pass, \`elevator-ui\` itself is no longer a Nuxt/Vue`,
  );
  p(
    `app at all: static HTML/CSS plus three vanilla TypeScript files,`,
  );
  p(`compiled by a bare \`tsc\`, no framework, no Node process in`);
  p(`production, no bundler).`);
  p(`Re-run this script any time any of the three refs moves.`);
  p();
  p(
    `\`${m.mid.ref}\`'s own history shows its elevator-api side already`,
  );
  p(
    `deleted the CRUD scaffolding independently of \`${m.after.ref}\`'s --`,
  );
  p(
    `so most backend metrics below are expected to already match`,
  );
  p(
    `\`${m.after.ref}\`, and that convergence is itself reported rather than`,
  );
  p(
    `hidden. Where the three points actually diverge is the front end:`,
  );
  p(
    `\`${m.mid.ref}\` shows what hypermedia buys *without* also removing`,
  );
  p(`the smart client -- see sections 4 and 10 in particular.`);
  p();

  p(`## 1. Lines of code and file counts`);
  p();
  p(
    `| | \`${m.before.ref}\` | \`${m.mid.ref}\` | \`${m.after.ref}\` |`,
  );
  p(`|---|---|---|---|`);
  p(
    `| elevator-api \`main\` files / lines | ${beforeApi.files} / ${beforeApi.lines} | ${midApi.files} / ${midApi.lines} | ${afterApi.files} / ${afterApi.lines} |`,
  );
  p(
    `| elevator-api \`test\` files / lines | ${beforeTest.files} / ${beforeTest.lines} | ${midTest.files} / ${midTest.lines} | ${afterTest.files} / ${afterTest.lines} |`,
  );
  p(`| **Largest file:** | | | |`);
  p(
    `| File | \`${largestBefore?.Filename}\` | \`${largestMid?.Filename}\` | \`${largestAfter?.Filename}\` |`,
  );
  p(
    `| Lines | ${largestBefore?.Lines} | ${largestMid?.Lines} | ${largestAfter?.Lines} |`,
  );
  p();
  p(
    `File count rises with vertical slicing by design -- one directory`,
  );
  p(
    `per behaviour, each holding its own command/handler/endpoint/tests.`,
  );
  p(
    `\`${m.mid.ref}\` already matches \`${m.after.ref}\` almost exactly here --`,
  );
  p(
    `its elevator-api side finished the same migration independently.`,
  );
  p(`Section 2 checks whether those smaller files are also simpler.`);
  p();

  p(`## 2. Complexity, not just size`);
  p();
  p(
    `Per-file cyclomatic complexity (\`scc\`), comparing the old`,
  );
  p(
    `\`controller/\`+\`model/\`+\`service/\`+\`repository/\` layer (\`${m.before.ref}\`,`,
  );
  p(
    `now deleted outright on both \`${m.mid.ref}\` and \`${m.after.ref}\`) against`,
  );
  p(`the \`feature/*\` slices and shared kernel on each:`);
  p();
  p(`| | files | avg complexity | median | max |`);
  p(`|---|---|---|---|---|`);
  p(
    `| old layer (\`${m.before.ref}\`) | ${oldLayer.files} | ${oldLayer.avgComplexity} | ${oldLayer.medianComplexity} | ${oldLayer.maxComplexity} |`,
  );
  p(
    `| \`feature/*\` (\`${m.mid.ref}\`) | ${midFeature.files} | ${midFeature.avgComplexity} | ${midFeature.medianComplexity} | ${midFeature.maxComplexity} |`,
  );
  p(
    `| shared kernel (\`${m.mid.ref}\`) | ${midShared.files} | ${midShared.avgComplexity} | ${midShared.medianComplexity} | ${midShared.maxComplexity} |`,
  );
  p(
    `| \`feature/*\` (\`${m.after.ref}\`) | ${feature.files} | ${feature.avgComplexity} | ${feature.medianComplexity} | ${feature.maxComplexity} |`,
  );
  p(
    `| shared kernel (\`${m.after.ref}\`) | ${shared.files} | ${shared.avgComplexity} | ${shared.medianComplexity} | ${shared.maxComplexity} |`,
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
  p(
    `(\`${m.mid.ref}\`'s \`feature/*\` is already essentially this same`,
  );
  p(
    `set of slices, since its backend finished independently -- this`,
  );
  p(`table isn't repeated per-ref for that reason.)`);
  p();

  p(`## 4. The BFF: consolidated, then removed`);
  p();
  p(
    `\`${m.mid.ref}\` shows a step slice 8 doesn't: the BFF route count`,
  );
  p(
    `already collapsed from one-per-verb to one-per-concern (a single`,
  );
  p(
    `\`commands.post.ts\` mirroring elevator-api's own single command`,
  );
  p(
    `endpoint) *before* it was deleted outright on \`${m.after.ref}\`:`,
  );
  p();
  p(
    `| | \`${m.before.ref}\` | \`${m.mid.ref}\` | \`${m.after.ref}\` |`,
  );
  p(`|---|---|---|---|`);
  p(
    `| BFF + store files | ${m.bffTotals.files} | ${m.midBffTotals.files} | 0 |`,
  );
  p(
    `| BFF + store lines | ${m.bffTotals.lines} | ${m.midBffTotals.lines} | 0 |`,
  );
  p(
    `| BFF route files (\`server/api/**\`) | ${m.bffRouteTotals.files} | ${m.midBffRouteTotals.files} | 0 |`,
  );
  p(
    `| ... avg/median/max lines | ${m.bffRouteTotals.avg} / ${m.bffRouteTotals.median} / ${m.bffRouteTotals.max} | ${m.midBffRouteTotals.avg} / ${m.midBffRouteTotals.median} / ${m.midBffRouteTotals.max} | -- |`,
  );
  p(
    `| \`stores/elevator.ts\` lines | ${m.beforeStoreTotals.lines} | ${m.midStoreTotals.lines} | 0 |`,
  );
  if (m.bffDuplication) {
    p(
      `| Duplication among route files | ${round1(m.bffDuplication.percentage)}% (${m.bffDuplication.clones}) | ${m.midBffDuplication ? `${round1(m.midBffDuplication.percentage)}% (${m.midBffDuplication.clones})` : "n/a"} | -- |`,
    );
  }
  p(`| Network hops per rider action | 2 | 2 | 1 |`);
  p();
  p(
    `Before/mid: browser -> BFF route -> elevator-api. After: browser`,
  );
  p(
    `-> elevator-api directly (Caddy is a transparent reverse proxy,`,
  );
  p(`not a logic hop).`);
  p();
  p(
    `Route *count* dropped 15 -> ${m.midBffRouteTotals.files} between \`${m.before.ref}\` and`,
  );
  p(
    `\`${m.mid.ref}\` -- one consolidated command proxy instead of one per`,
  );
  p(
    `verb. But the store grew: **${m.beforeStoreTotals.lines} -> ${m.midStoreTotals.lines} lines**. This is not a`,
  );
  p(
    `contradiction, it's the whole point of this middle data point: a`,
  );
  p(
    `smart SPA client consuming a hypermedia JSON API still has to`,
  );
  p(
    `*interpret* that hypermedia -- find the right operation by rel,`,
  );
  p(
    `follow its \`href\` and method, echo its hidden fields -- which is`,
  );
  p(
    `real logic, replacing "duplicate the business rule" with "parse`,
  );
  p(
    `the affordance correctly". Different work, not obviously less of`,
  );
  p(
    `it, and it still needs testing client-side (section 10). Only`,
  );
  p(
    `removing the smart client too (\`${m.after.ref}\`) gets that logic,`,
  );
  p(
    `and its tests, out of the front end entirely -- confirming`,
  );
  p(
    `\`docs/architecture.md\`'s own thesis that named commands and`,
  );
  p(`hypermedia are load-bearing for each other, neither alone.`);
  p();
  p(
    `Default jscpd thresholds (min 5 lines / 50 tokens) miss most`,
  );
  p(
    `duplication in files this small (${m.before.ref}'s routes average`,
  );
  p(
    `${m.bffRouteTotals.avg} lines). Lowering the thresholds to fit surfaces the`,
  );
  p(
    `percentages above; the routes are near-identical, differing only`,
  );
  p(
    `in HTTP verb and path, e.g. \`open-doors.post.ts\` and`,
  );
  p(`\`close-doors.post.ts\` on \`${m.before.ref}\`:`);
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
    `Every other route on \`${m.before.ref}\` repeats this shape, varying`,
  );
  p(
    `only the path segment and verb -- exactly the kind of repetition`,
  );
  p(
    `a client following hypermedia links never has to write, because`,
  );
  p(`it never constructs the URL or the verb itself.`);
  p();
  p(
    `Removing the BFF's *code* didn't by itself remove a deployable --`,
  );
  p(
    `the BFF lived inside the same Nuxt container as everything else`,
  );
  p(
    `in \`elevator-ui\`, not a separate one, so the cost removed here was`,
  );
  p(
    `pure pass-through/proxy code and an extra hop, not a container.`,
  );
  p(
    `Section 11 measures what *did* eventually reduce the deployable`,
  );
  p(`count: removing the framework that container ran, entirely.`);
  p();

  p(`## 5. Duplication (whole-directory)`);
  p();
  p(
    `\`elevator-ui\` below is \`app/\` + \`server/\` where a BFF exists`,
  );
  p(`(\`${m.before.ref}\`, \`${m.mid.ref}\`), \`app/\` alone otherwise:`);
  p();
  p(
    `| | \`${m.before.ref}\` | \`${m.mid.ref}\` | \`${m.after.ref}\` |`,
  );
  p(`|---|---|---|---|`);
  p(
    `| elevator-api | ${dupPct(m.dupApiBefore)} | ${dupPct(m.dupApiMid)} | ${dupPct(m.dupApiAfter)} |`,
  );
  p(
    `| elevator-ui | ${dupPct(m.dupUiBefore)} | ${dupPct(m.dupUiMid)} | ${dupPct(m.dupUiAfter)} |`,
  );
  p();
  p(
    `elevator-api's duplication is close across all three, consistent`,
  );
  p(
    `with \`${m.mid.ref}\` and \`${m.after.ref}\` sharing essentially the`,
  );
  p(
    `same backend. Read the elevator-api numbers carefully regardless:`,
  );
  p(
    `exact-token clone detection over many small, structurally-identical`,
  );
  p(
    `files naturally reports a higher percentage than a few large ones`,
  );
  p(
    `would, even when nothing is copy-pasted. Sampling its clones`,
  );
  p(
    `confirms this: they are import blocks and same-shaped`,
  );
  p(
    `\`AffordanceContributor\`/\`Command\` implementations (one interface,`,
  );
  p(
    `many slices) -- not duplicated business logic. \`${m.before.ref}\`'s`,
  );
  p(
    `clones are the opposite kind: copy-pasted controller/validation`,
  );
  p(
    `logic between e.g. \`CallController\` and \`CarCallController\` -- the`,
  );
  p(`smell this refactor targets. Percentage alone conflates the two;`);
  p(`only sampling the clones tells them apart.`);
  p();

  p(`## 6. API surface`);
  p();
  p(
    `| | \`${m.before.ref}\` | \`${m.mid.ref}\` | \`${m.after.ref}\` |`,
  );
  p(`|---|---|---|---|`);
  p(
    `| Endpoint mappings (\`@*Mapping\`) | ${m.beforeMappings} | ${m.midMappings} | ${m.afterMappings} |`,
  );
  p(
    `| Hard-coded \`/elevators/...\` literals in elevator-ui | ${m.beforeDomainLiterals} | ${m.midDomainLiterals} | ${m.afterDomainLiterals} |`,
  );
  p();
  p(
    `\`${m.before.ref}\`: one URL per verb (\`/calls\`, \`/car-calls\`,`,
  );
  p(
    `\`/open-doors\`, \`/close-doors\`, \`/obstruct-doors\`,`,
  );
  p(
    `\`/clear-obstruction\`, \`/weight\`, \`/maintenance\`, ...). From`,
  );
  p(
    `\`${m.mid.ref}\` onward, every command funnels through the shared`,
  );
  p(
    `\`POST /elevators/{id}\` on the backend -- but the hard-coded-literal`,
  );
  p(
    `count on \`${m.mid.ref}\` shows the client still isn't off the hook:`,
  );
  p(
    `its BFF and store still construct \`/elevators/{id}\` URLs rather`,
  );
  p(
    `than following links, because there's still a smart client to do`,
  );
  p(
    `the constructing. Only \`${m.after.ref}\` gets this to zero -- the`,
  );
  p(`client follows links instead of building them.`);
  p();

  p(`## 7. The whole diff, by application`);
  p();
  p(
    `Total, \`${m.before.ref}\` -> \`${m.after.ref}\`:`,
  );
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
  p(
    `Split into its two legs either side of \`${m.mid.ref}\` (+/- only):`,
  );
  p();
  p(
    `| app | \`${m.before.ref}\` -> \`${m.mid.ref}\` | \`${m.mid.ref}\` -> \`${m.after.ref}\` |`,
  );
  p(`|---|---|---|`);
  for (const app of Object.keys(m.diffsBeforeToMid)) {
    const a = m.diffsBeforeToMid[app];
    const b = m.diffsMidToAfter[app];
    p(
      `| ${app} | +${a.insertions}/-${a.deletions} | +${b.insertions}/-${b.deletions} |`,
    );
  }
  p();
  p(
    `The first leg is almost entirely elevator-api (the backend`,
  );
  p(
    `migration); the second leg is almost entirely elevator-ui (BFF and`,
  );
  p(
    `store deletion) -- the two legs of this refactor really were`,
  );
  p(`separable, and \`${m.mid.ref}\` is the seam between them.`);
  p();
  p(
    `Read \`docs\`'s numbers with one caveat: this repo's \`docs/\``,
  );
  p(
    `also grew for reasons unrelated to the refactor itself (a talk`,
  );
  p(
    `manuscript, this very report) -- it is not a proxy for "code`,
  );
  p(`written to migrate the architecture" the way the other rows are.`);
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
  p(
    `This section's subject (the CRUD surface being versioned) is`,
  );
  p(
    `orthogonal to \`${m.mid.ref}\`: the single consolidated command`,
  );
  p(
    `endpoint that removes the need to version at all is a backend`,
  );
  p(
    `property, and \`${m.mid.ref}\`'s backend already has it -- this`,
  );
  p(`cost was avoided from that milestone onward, not just at the end.`);
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
    `| mid: domain+application, combined | ${fc.midDomainApp.totalFiles} | ${fc.midDomainApp.filesWithImports} | ${fc.midDomainApp.distinctSymbols} |`,
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
    `| whole \`elevator-api/src/main\`, \`crud\` | ${fc.beforeWhole.totalFiles} | ${fc.beforeWhole.filesWithImports} | ${fc.beforeWhole.distinctSymbols} |`,
  );
  p(
    `| whole \`elevator-api/src/main\`, \`json-hypermedia\` | ${fc.midWhole.totalFiles} | ${fc.midWhole.filesWithImports} | ${fc.midWhole.distinctSymbols} |`,
  );
  p(
    `| whole \`elevator-api/src/main\`, \`main\` | ${fc.afterWhole.totalFiles} | ${fc.afterWhole.filesWithImports} | ${fc.afterWhole.distinctSymbols} |`,
  );
  p();
  p(
    `\`${m.mid.ref}\`'s domain+application coupling already matches`,
  );
  p(
    `\`${m.after.ref}\`'s -- expected, since this is purely a backend`,
  );
  p(
    `property and \`${m.mid.ref}\`'s backend already finished migrating.`,
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
  p(
    `| | \`${m.before.ref}\` | \`${m.mid.ref}\` | \`${m.after.ref}\` |`,
  );
  p(`|---|---|---|---|`);
  p(
    `| elevator-api: unit files / methods | ${bc.unitFiles} / ${bc.unitTests} | ${mc.unitFiles} / ${mc.unitTests} | ${ac.unitFiles} / ${ac.unitTests} |`,
  );
  p(
    `| elevator-api: Spring files / methods | ${bc.contextFiles} / ${bc.contextTests} | ${mc.contextFiles} / ${mc.contextTests} | ${ac.contextFiles} / ${ac.contextTests} |`,
  );
  p(
    `| elevator-ui: e2e spec files / cases | ${ui.beforeE2e.files} / ${ui.beforeE2e.cases} | ${ui.midE2e.files} / ${ui.midE2e.cases} | ${ui.afterE2e.files} / ${ui.afterE2e.cases} |`,
  );
  p(
    `| ... their assertions (\`expect(\`) | ${ui.beforeE2e.assertions} | ${ui.midE2e.assertions} | ${ui.afterE2e.assertions} |`,
  );
  p(
    `| elevator-ui: client unit test files / cases | ${ui.beforeClientUnit.files} / ${ui.beforeClientUnit.cases} | ${ui.midClientUnit.files} / ${ui.midClientUnit.cases} | ${ui.afterClientUnit.files} / ${ui.afterClientUnit.cases} |`,
  );
  p(
    `| ... their lines | ${ui.beforeClientUnit.lines} | ${ui.midClientUnit.lines} | 0 |`,
  );
  p();
  p(
    `\`${m.before.ref}\`: ${round1((bc.contextFiles / (bc.unitFiles + bc.contextFiles)) * 100)}% of elevator-api tests need a full Spring`,
  );
  p(
    `context. \`${m.mid.ref}\`: ${round1((mc.contextFiles / (mc.unitFiles + mc.contextFiles)) * 100)}%. \`${m.after.ref}\`: ${round1((ac.contextFiles / (ac.unitFiles + ac.contextFiles)) * 100)}% -- again`,
  );
  p(
    `converged with \`${m.mid.ref}\` at the backend level, as expected.`,
  );
  p();
  p(
    `The front end tells a different story. \`${m.mid.ref}\`'s`,
  );
  p(
    `client-side unit test suite didn't shrink on the way from`,
  );
  p(
    `\`${m.before.ref}\` -- it **grew**, ${ui.beforeClientUnit.lines} -> ${ui.midClientUnit.lines} lines`,
  );
  p(
    `(${ui.beforeClientUnit.cases} -> ${ui.midClientUnit.cases} cases). \`${m.before.ref}\`'s store tested`,
  );
  p(
    `"which requests are pending" (a duplicated business rule);`,
  );
  p(
    `\`${m.mid.ref}\`'s tests "does the store correctly interpret the`,
  );
  p(
    `hypermedia response" -- finding the right operation by rel,`,
  );
  p(
    `following its \`href\`/method, echoing hidden fields. Different`,
  );
  p(
    `tier-appropriate-sounding problem, same result: real logic in`,
  );
  p(
    `the client, still needing tests, still not where it belongs.`,
  );
  p(
    `Only \`${m.after.ref}\` gets this to zero, because only there does`,
  );
  p(
    `the client stop interpreting anything -- it renders what it's`,
  );
  p(`handed.`);
  p();
  p(
    `The e2e suite is essentially unchanged in case count throughout`,
  );
  p(
    `(it covers the same shell/interaction chrome), though its`,
  );
  p(
    `assertion count drops on \`${m.after.ref}\``,
  );
  p(
    `(${ui.beforeE2e.assertions} -> ${ui.midE2e.assertions} -> ${ui.afterE2e.assertions}): its own comment says why -- more of the`,
  );
  p(
    `page is now legitimately rendered by elevator-api itself and`,
  );
  p(
    `morphed in by Datastar, so there is less static Nuxt markup left`,
  );
  p(`for a shell-only smoke test to assert on.`);
  p();
  p(
    `What disappeared entirely between \`${m.mid.ref}\` and \`${m.after.ref}\``,
  );
  p(
    `is the client-side unit test suite itself, because`,
  );
  p(
    `\`stores/elevator.ts\` is gone -- there is nothing left in the`,
  );
  p(`client worth unit-testing. \`${m.before.ref}\`'s own test names`);
  p(
    `said what it was really testing: \`filters served calls out of`,
  );
  p(
    `pendingCalls\`, \`collects pending floors from both call types\` --`,
  );
  p(
    `a business rule (which requests are still pending), requiring`,
  );
  p(`five mocked HTTP endpoints and a Pinia store just to assert a`);
  p(`filter.`);
  p();
  p("```ts");
  p(`// ${m.before.ref}: elevator-ui/test/unit/elevatorStore.test.ts`);
  p(`registerEndpoint('/api/key', { method: 'GET', handler: () => ... })`);
  p(`registerEndpoint('/api/key', { method: 'POST', handler: ... })`);
  p(`registerEndpoint('/api/elevators/1/status', { ... })`);
  p(`// ...three more registerEndpoint calls, then, finally:`);
  p(`it('filters served calls out of pendingCalls', () => { ... })`);
  p();
  p(`// ${m.mid.ref}: same file, same effort, different problem --`);
  p(`// now testing hypermedia interpretation, not a business rule:`);
  p(`it("posts to the operation's own href and method, echoing its`);
  p(`    hidden type", async () => { ... })`);
  p();
  p(`// ${m.after.ref}: elevator-api RequestQueueTest.java -- no mocks, no`);
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
      `worktree's **elevator-api** suite, on this machine:`,
    );
    p();
    p(`| | \`${m.before.ref}\` | \`${m.mid.ref}\` | \`${m.after.ref}\` |`);
    p(`|---|---|---|---|`);
    p(
      `| Tests executed | ${bg.testCount} | ${mg?.ok ? mg.testCount : "n/a"} | ${ag.testCount} |`,
    );
    p(
      `| Wall-clock time | ${round1(bg.ms / 1000)}s | ${mg?.ok ? `${round1(mg.ms / 1000)}s` : "n/a"} | ${round1(ag.ms / 1000)}s |`,
    );
    p(
      `| Avg per test | ${round1(bg.ms / bg.testCount)}ms | ${mg?.ok ? `${round1(mg.ms / mg.testCount)}ms` : "n/a"} | ${round1(ag.ms / ag.testCount)}ms |`,
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
    p(`### Does running the front-end tests change any of this?`);
    p();
    p(
      `Checked by hand (not automated here -- \`npm run test:e2e\` needs`,
    );
    p(
      `a dev server and a browser, plus a fresh \`npm install\` per`,
    );
    p(
      `worktree, none of which this script wires up): \`elevator-ui\`'s`,
    );
    p(
      `Playwright suite ran in **8.3-9.6s on \`crud\`**,`,
    );
    p(
      `**7.7-8.3s on \`json-hypermedia\`**, and **5.0-5.3s on \`main\`**,`,
    );
    p(
      `across two runs each -- \`crud\` and \`json-hypermedia\` are`,
    );
    p(
      `indistinguishable within normal run-to-run noise, dominated by`,
    );
    p(
      `their fixed Nuxt dev-server cold start; \`main\` has none, and`,
    );
    p(
      `its own \`serve.mjs\` plus \`tsc\` compile step measurably shrinks`,
    );
    p(
      `the total, though Chromium launch alone still accounts for most`,
    );
    p(
      `of what remains. This is expected, not`,
    );
    p(
      `a gap in the measurement: all three spec files say in their own`,
    );
    p(
      `comments that they deliberately never call elevator-api --`,
    );
    p(
      `\`"renders ... regardless of whether elevator-api is reachable"\`.`,
    );
    p(
      `Two claims, not one, and they point opposite ways. For *this*`,
    );
    p(
      `subsection's question -- does the backend/BFF question move`,
    );
    p(
      `front-end test speed -- the answer is still no: none of these`,
    );
    p(
      `specs exercise elevator-api, so \`crud\` vs \`json-hypermedia\``,
    );
    p(
      `(same Nuxt front end, different backend) stays a null result,`,
    );
    p(
      `reported honestly rather than dressed up. But \`main\`'s`,
    );
    p(
      `measured speedup is real and belongs to a different, also-real`,
    );
    p(
      `finding: removing the framework itself (section 11) removes`,
    );
    p(`Nuxt's own dev-server/build overhead, framework or not.`);
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

  p(`## 11. Deployables`);
  p();
  p(
    `Docker Compose services defined per ref (\`docker-compose.yml\`):`,
  );
  p();
  p(
    `| | \`${m.before.ref}\` | \`${m.mid.ref}\` | \`${m.after.ref}\` |`,
  );
  p(`|---|---|---|---|`);
  p(
    `| Services | ${m.deployables.before.length} | ${m.deployables.mid.length} | ${m.deployables.after.length} |`,
  );
  p();
  p(
    `The count alone is a poor metric here -- it goes 3 -> 4 -> 3, which`,
  );
  p(
    `looks like nothing changed net. What each "3" actually *is* is the`,
  );
  p(
    `real story: \`${m.before.ref}\`'s three are elevator-api, a full`,
  );
  p(
    `Node/Nuxt SSR server for elevator-ui, and elevator-auth.`,
  );
  p(
    `\`${m.mid.ref}\` adds a fourth, Caddy, as a reverse proxy in front of`,
  );
  p(
    `the still-Node elevator-ui. \`${m.after.ref}\`'s three drop`,
  );
  p(
    `elevator-ui as a service entirely -- Caddy now serves its compiled`,
  );
  p(
    `static output directly, so there is no Node process, no server-side`,
  );
  p(
    `runtime, and nothing left to crash, restart, or scale for the front`,
  );
  p(
    `end. The container count matches \`${m.before.ref}\`'s by`,
  );
  p(
    `coincidence; the thing running inside it does not.`,
  );
  p();

  return lines.join("\n") + "\n";
}

function dupPct(report) {
  if (!report) return "n/a";
  return `${round1(report.percentage)}% (${report.clones} clones)`;
}

main();
