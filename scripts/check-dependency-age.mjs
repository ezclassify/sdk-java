#!/usr/bin/env node
/**
 * Dependency quarantine gate.
 *
 * Refuses third-party artifacts that were published inside a quarantine window,
 * so a freshly compromised release has to sit on its registry for a while before
 * it can enter this repository's dependency tree. First-party artifacts are
 * exempt: a library published minutes ago must still be consumable by its
 * sibling repositories.
 *
 * Two sources are checked, both reading the exact versions that would be used:
 *
 *   - The production closure of every reactor module, from the
 *     license-maven-plugin report `target/generated-sources/license/THIRD-PARTY.txt`
 *     (the same file `scripts/gen-notices.mjs` reads). Each artifact version is
 *     looked up on Maven Central, whose search index carries the publish
 *     timestamp. Maven has no lockfile; the poms pin every version, and this
 *     report is the resolved set.
 *   - `tools/package-lock.json`, the pinned generator toolchain, against the npm
 *     registry's per-version publish times.
 *
 * Every lookup has a timeout and the run has an overall bound. A registry that
 * cannot be read, or an artifact with no publish time, fails the gate; nothing
 * is assumed old. Each offender is listed with the time its window clears.
 *
 * Usage:
 *
 *     node scripts/check-dependency-age.mjs [--days=N] [--closure=PATH]
 *         [--npm-lockfile=PATH] [--central=URL] [--registry=URL]
 *         [--first-party-registry=URL,URL,...]
 *
 * Each flag also has an environment form, which is what the tests use:
 * EZCLASSIFY_DEPENDENCY_AGE_DAYS, _CLOSURE, _NPM_LOCKFILE, _CENTRAL, _REGISTRY,
 * _FIRST_PARTY_REGISTRIES and _OVERALL_TIMEOUT_MS.
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_DAYS = 7;
const MS_PER_DAY = 86_400_000;
const REQUEST_TIMEOUT_MS = 20_000;
const DEFAULT_OVERALL_TIMEOUT_MS = 5 * 60_000;
const DEFAULT_CENTRAL = 'https://search.maven.org';
const DEFAULT_NPM_REGISTRY = 'https://registry.npmjs.org';
const DEFAULT_FIRST_PARTY_REGISTRIES = ['https://npm.pkg.github.com'];
const FIRST_PARTY_MAVEN_GROUP = 'com.ezclassify';
const FIRST_PARTY_NPM_PREFIX = '@ezclassify/';
const ENV = 'EZCLASSIFY_DEPENDENCY_AGE_';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const FLAGS = {
  days: 'DAYS',
  closure: 'CLOSURE',
  'npm-lockfile': 'NPM_LOCKFILE',
  central: 'CENTRAL',
  registry: 'REGISTRY',
  'first-party-registry': 'FIRST_PARTY_REGISTRIES',
};

function parseOptions(argv) {
  const flags = {};
  for (const arg of argv) {
    const match = /^--([a-z-]+)=(.+)$/.exec(arg);
    if (!match || !(match[1] in FLAGS)) {
      throw new Error(
        `Unrecognized argument "${arg}". Usage: check-dependency-age.mjs [--days=N] [--closure=PATH] ` +
          '[--npm-lockfile=PATH] [--central=URL] [--registry=URL] [--first-party-registry=URL,...]',
      );
    }
    flags[match[1]] = match[2];
  }
  const pick = (flag) => flags[flag] ?? process.env[ENV + FLAGS[flag]];
  const days = Number(pick('days') ?? DEFAULT_DAYS);
  if (!Number.isFinite(days) || days < 0) {
    throw new Error(`Quarantine window must be a non-negative number of days, got "${pick('days')}".`);
  }
  const overallRaw = process.env[ENV + 'OVERALL_TIMEOUT_MS'];
  const overallMs = overallRaw === undefined ? DEFAULT_OVERALL_TIMEOUT_MS : Number(overallRaw);
  if (!Number.isInteger(overallMs) || overallMs <= 0) {
    throw new Error(`${ENV}OVERALL_TIMEOUT_MS must be a positive integer, got "${overallRaw}".`);
  }
  const origin = (url) => url.replace(/\/+$/, '');
  const firstParty = (pick('first-party-registry')?.split(',') ?? DEFAULT_FIRST_PARTY_REGISTRIES)
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => new URL(entry).origin);
  return {
    days,
    overallMs,
    closure: resolve(pick('closure') ?? resolve(root, 'target/generated-sources/license/THIRD-PARTY.txt')),
    npmLockfile: resolve(pick('npm-lockfile') ?? resolve(root, 'tools/package-lock.json')),
    central: origin(pick('central') ?? DEFAULT_CENTRAL),
    registry: origin(pick('registry') ?? DEFAULT_NPM_REGISTRY),
    firstParty: new Set(firstParty),
  };
}

/**
 * Third-party Maven coordinates from the license report. Lines look like
 * `(Apache-2.0) Gson (com.google.code.gson:gson:2.14.0 - https://...)`.
 */
function readMavenClosure(path) {
  const found = new Map();
  for (const raw of readFileSync(path, 'utf8').split('\n')) {
    const match = raw.trim().match(/\(([\w.-]+):([\w.-]+):([\w.-]+)(?: - \S+)?\)$/);
    if (!match) continue;
    const [, group, artifact, version] = match;
    if (group === FIRST_PARTY_MAVEN_GROUP || group.startsWith(`${FIRST_PARTY_MAVEN_GROUP}.`)) continue;
    found.set(`${group}:${artifact}:${version}`, { kind: 'maven', group, artifact, version });
  }
  if (found.size === 0) {
    throw new Error(`Parsed no third-party artifacts from ${path}; refusing to pass an empty check.`);
  }
  return [...found.values()];
}

/** Third-party npm packages a lockfile would install, one entry per name@version. */
function readNpmLockfile(path, options) {
  const lock = JSON.parse(readFileSync(path, 'utf8'));
  const found = new Map();
  for (const [key, entry] of Object.entries(lock.packages ?? {})) {
    if (key === '' || entry.link === true || !key.includes('node_modules/')) continue;
    if (typeof entry.version !== 'string') continue;
    const name = entry.name ?? key.slice(key.lastIndexOf('node_modules/') + 'node_modules/'.length);
    let registry = options.registry;
    if (typeof entry.resolved === 'string') {
      let url;
      try {
        url = new URL(entry.resolved);
      } catch {
        continue;
      }
      // A git or file dependency has no registry to ask.
      if (url.protocol !== 'http:' && url.protocol !== 'https:') continue;
      registry = url.origin;
    }
    if (name.startsWith(FIRST_PARTY_NPM_PREFIX) && (entry.resolved === undefined || options.firstParty.has(registry))) {
      continue;
    }
    found.set(`${name}@${entry.version}`, { kind: 'npm', name, version: entry.version, registry });
  }
  return [...found.values()];
}

async function getJson(url, deadline, what) {
  let response;
  try {
    response = await fetch(url, {
      headers: { accept: 'application/json' },
      signal: AbortSignal.any([AbortSignal.timeout(REQUEST_TIMEOUT_MS), deadline]),
    });
  } catch (cause) {
    throw new Error(`Could not reach the registry to check ${what}: ${cause instanceof Error ? cause.message : cause}`);
  }
  if (!response.ok) throw new Error(`Registry answered ${response.status} for ${what}; cannot confirm its publish date.`);
  try {
    return await response.json();
  } catch {
    throw new Error(`Registry returned unreadable JSON for ${what}; cannot confirm its publish date.`);
  }
}

/** Publish time in epoch milliseconds, or null when the registry does not say. */
async function mavenPublishTime(entry, options, deadline) {
  const query = `g:"${entry.group}" AND a:"${entry.artifact}" AND v:"${entry.version}"`;
  const url = `${options.central}/solrsearch/select?q=${encodeURIComponent(query)}&core=gav&rows=1&wt=json`;
  const document = await getJson(url, deadline, `${entry.group}:${entry.artifact}:${entry.version}`);
  const timestamp = document?.response?.docs?.[0]?.timestamp;
  return Number.isFinite(timestamp) ? timestamp : null;
}

async function npmPublishTime(entry, options, deadline, cache) {
  const key = `${entry.registry} ${entry.name}`;
  if (!cache.has(key)) {
    const url = `${entry.registry}/${entry.name.replace('/', '%2f')}`;
    cache.set(key, getJson(url, deadline, entry.name));
  }
  const times = (await cache.get(key))?.time;
  const parsed = Date.parse(times?.[entry.version]);
  return Number.isNaN(parsed) ? null : parsed;
}

function label(entry) {
  return entry.kind === 'maven'
    ? `${entry.group}:${entry.artifact}:${entry.version}`
    : `${entry.name}@${entry.version}`;
}

async function main() {
  const options = parseOptions(process.argv.slice(2));
  const entries = [...readMavenClosure(options.closure), ...readNpmLockfile(options.npmLockfile, options)];
  if (options.days === 0) {
    console.log('Dependency quarantine window is 0 days; nothing to check.');
    return;
  }
  const overall = new AbortController();
  const timer = setTimeout(() => overall.abort(new Error('overall time limit reached')), options.overallMs);
  timer.unref();

  const cutoff = Date.now() - options.days * MS_PER_DAY;
  const npmCache = new Map();
  const violations = [];
  const unconfirmed = [];

  for (const entry of entries) {
    let published = null;
    try {
      published =
        entry.kind === 'maven'
          ? await mavenPublishTime(entry, options, overall.signal)
          : await npmPublishTime(entry, options, overall.signal, npmCache);
    } catch (error) {
      console.error(error instanceof Error ? error.message : String(error));
    }
    if (published === null) {
      unconfirmed.push(entry);
    } else if (published > cutoff) {
      violations.push({
        entry,
        published: new Date(published).toISOString(),
        clears: new Date(published + options.days * MS_PER_DAY).toISOString(),
      });
    }
  }
  clearTimeout(timer);

  for (const entry of unconfirmed) {
    console.error(`Dependency quarantine: no confirmed publish date for ${label(entry)}.`);
  }
  if (violations.length > 0) {
    console.error(
      `Dependency quarantine: ${violations.length} third-party artifact(s) were published less than ${options.days} day(s) ago.`,
    );
    for (const { entry, published, clears } of violations) {
      console.error(`  ${label(entry)} published ${published}, clears ${clears}`);
    }
    console.error('Wait out the window, or pin an older version, before taking these in.');
  }
  if (unconfirmed.length > 0 || violations.length > 0) {
    process.exitCode = 1;
    return;
  }
  console.log(
    `Dependency quarantine passed: ${entries.length} third-party artifact(s) are all older than ${options.days} day(s).`,
  );
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
