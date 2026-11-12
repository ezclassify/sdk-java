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
 * Three sources are checked, all reading the exact versions that would be used:
 *
 *   - The dependency closure of every reactor module in every scope, test
 *     included, from the license-maven-plugin aggregate report
 *     (`target/dependency-age/dependencies.txt`).
 *   - Every build plugin and each plugin's own dependencies, from the
 *     maven-dependency-plugin `resolve-plugins` listing
 *     (`target/dependency-age/plugins.txt`).
 *   - `tools/package-lock.json`, the pinned generator toolchain, against the npm
 *     registry's per-version publish times.
 *
 * CI writes both Maven reports with one resolve-only Maven run before the build
 * executes anything, then runs this gate. Maven artifacts are dated by the
 * Last-Modified time Maven Central serves for the version's .pom, which is the
 * time the version was published there; Maven has no lockfile, the poms pin
 * every version, and these reports are the resolved set.
 *
 * Lookups run a few at a time. Each request has a timeout, a request that fails
 * in a way that can pass on another try (no answer, 408, 429, 5xx) is retried a
 * bounded number of times, and the run as a whole has an overall bound. A
 * registry that cannot be read, or an artifact with no publish time, fails the
 * gate; nothing is assumed old. Each offender is listed with the time its window
 * clears.
 *
 * Usage:
 *
 *     node scripts/check-dependency-age.mjs [--days=N] [--closure=PATH]
 *         [--plugins=PATH] [--npm-lockfile=PATH] [--central=URL] [--registry=URL]
 *         [--first-party-registry=URL,URL,...]
 *
 * Each flag also has an environment form, which is what the tests use:
 * EZCLASSIFY_DEPENDENCY_AGE_DAYS, _CLOSURE, _PLUGINS, _NPM_LOCKFILE, _CENTRAL,
 * _REGISTRY, _FIRST_PARTY_REGISTRIES, _OVERALL_TIMEOUT_MS and _RETRY_DELAY_MS.
 */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';

const DEFAULT_DAYS = 7;
const MS_PER_DAY = 86_400_000;
const REQUEST_TIMEOUT_MS = 20_000;
const REQUEST_ATTEMPTS = 3;
const DEFAULT_RETRY_DELAY_MS = 1_000;
const LOOKUP_CONCURRENCY = 8;
const DEFAULT_OVERALL_TIMEOUT_MS = 5 * 60_000;
const DEFAULT_CENTRAL = 'https://repo1.maven.org/maven2';
const DEFAULT_NPM_REGISTRY = 'https://registry.npmjs.org';
const DEFAULT_FIRST_PARTY_REGISTRIES = ['https://npm.pkg.github.com'];
const FIRST_PARTY_MAVEN_GROUP = 'com.ezclassify';
const FIRST_PARTY_NPM_PREFIX = '@ezclassify/';
const ENV = 'EZCLASSIFY_DEPENDENCY_AGE_';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

const FLAGS = {
  days: 'DAYS',
  closure: 'CLOSURE',
  plugins: 'PLUGINS',
  'npm-lockfile': 'NPM_LOCKFILE',
  central: 'CENTRAL',
  registry: 'REGISTRY',
  'first-party-registry': 'FIRST_PARTY_REGISTRIES',
};

function envInteger(name, fallback, allowZero = false) {
  const raw = process.env[ENV + name];
  const value = raw === undefined ? fallback : Number(raw);
  if (!Number.isInteger(value) || value < 0 || (value === 0 && !allowZero)) {
    throw new Error(`${ENV}${name} must be a ${allowZero ? 'non-negative' : 'positive'} integer, got "${raw}".`);
  }
  return value;
}

function parseOptions(argv) {
  const flags = {};
  for (const arg of argv) {
    const match = /^--([a-z-]+)=(.+)$/.exec(arg);
    if (!match || !(match[1] in FLAGS)) {
      throw new Error(
        `Unrecognized argument "${arg}". Usage: check-dependency-age.mjs [--days=N] [--closure=PATH] ` +
          '[--plugins=PATH] [--npm-lockfile=PATH] [--central=URL] [--registry=URL] [--first-party-registry=URL,...]',
      );
    }
    flags[match[1]] = match[2];
  }
  const pick = (flag) => flags[flag] ?? process.env[ENV + FLAGS[flag]];
  const days = Number(pick('days') ?? DEFAULT_DAYS);
  if (!Number.isFinite(days) || days < 0) {
    throw new Error(`Quarantine window must be a non-negative number of days, got "${pick('days')}".`);
  }
  const trimSlashes = (url) => url.replace(/\/+$/, '');
  const firstParty = (pick('first-party-registry')?.split(',') ?? DEFAULT_FIRST_PARTY_REGISTRIES)
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => new URL(entry).origin);
  return {
    days,
    overallMs: envInteger('OVERALL_TIMEOUT_MS', DEFAULT_OVERALL_TIMEOUT_MS),
    retryDelayMs: envInteger('RETRY_DELAY_MS', DEFAULT_RETRY_DELAY_MS, true),
    closure: resolve(pick('closure') ?? resolve(root, 'target/dependency-age/dependencies.txt')),
    plugins: resolve(pick('plugins') ?? resolve(root, 'target/dependency-age/plugins.txt')),
    npmLockfile: resolve(pick('npm-lockfile') ?? resolve(root, 'tools/package-lock.json')),
    central: trimSlashes(pick('central') ?? DEFAULT_CENTRAL),
    registry: trimSlashes(pick('registry') ?? DEFAULT_NPM_REGISTRY),
    firstParty: new Set(firstParty),
  };
}

function isFirstPartyGroup(group) {
  return group === FIRST_PARTY_MAVEN_GROUP || group.startsWith(`${FIRST_PARTY_MAVEN_GROUP}.`);
}

function addMaven(found, group, artifact, version) {
  if (isFirstPartyGroup(group)) return;
  found.set(`${group}:${artifact}:${version}`, { kind: 'maven', group, artifact, version });
}

/**
 * Third-party Maven coordinates from the license aggregate report. Lines look like
 * `(Apache-2.0) Gson (com.google.code.gson:gson:2.14.0 - https://...)`.
 */
function readMavenClosure(path, found) {
  const before = found.size;
  for (const raw of readFileSync(path, 'utf8').split('\n')) {
    const match = raw.trim().match(/\(([\w.-]+):([\w.-]+):([\w.-]+)(?: - \S+)?\)$/);
    if (match) addMaven(found, match[1], match[2], match[3]);
  }
  if (found.size === before) {
    throw new Error(`Parsed no third-party artifacts from ${path}; refusing to pass an empty check.`);
  }
}

/**
 * Plugins and their dependencies from `dependency:resolve-plugins`. Entries are
 * indented `group:artifact:type:version` or `group:artifact:type:classifier:version`
 * lines; the version is always the last field.
 */
function readMavenPlugins(path, found) {
  let plugins = 0;
  for (const raw of readFileSync(path, 'utf8').split('\n')) {
    if (!/^\s/.test(raw)) continue;
    const fields = raw.trim().split(':');
    if (fields.length < 4 || fields.length > 5 || !fields.every((field) => /^[\w.-]+$/.test(field))) continue;
    plugins += 1;
    addMaven(found, fields[0], fields[1], fields.at(-1));
  }
  if (plugins === 0) {
    throw new Error(`Parsed no plugins from ${path}; refusing to pass an empty check.`);
  }
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

const describe = (cause) => (cause instanceof Error ? cause.message : String(cause));
const retryableStatus = (status) => status === 408 || status === 429 || status >= 500;

/**
 * Fetches `url`, retrying an unanswered request or a retryable status up to
 * REQUEST_ATTEMPTS times. Retries stop as soon as the overall deadline fires.
 * Resolves to an ok response; anything else throws with `what` in the message.
 */
async function request(url, { method, accept }, options, deadline, what) {
  let failure;
  for (let attempt = 1; attempt <= REQUEST_ATTEMPTS; attempt += 1) {
    if (attempt > 1) {
      try {
        await sleep(options.retryDelayMs * (attempt - 1), undefined, { signal: deadline });
      } catch {
        break;
      }
    }
    let response;
    try {
      response = await fetch(url, {
        method,
        headers: { accept },
        signal: AbortSignal.any([AbortSignal.timeout(REQUEST_TIMEOUT_MS), deadline]),
      });
    } catch (cause) {
      failure = `Could not reach the registry to check ${what}: ${describe(cause)}`;
      if (deadline.aborted) break;
      continue;
    }
    if (response.ok) return response;
    failure = `Registry answered ${response.status} for ${what}; cannot confirm its publish date.`;
    if (!retryableStatus(response.status)) break;
  }
  throw new Error(failure);
}

/**
 * Publish time in epoch milliseconds, or null when the registry does not say.
 * Central serves each published file with a Last-Modified time equal to the
 * time it was published, which is the timestamp its search index reports.
 */
async function mavenPublishTime(entry, options, deadline) {
  const { group, artifact, version } = entry;
  const url = `${options.central}/${group.replaceAll('.', '/')}/${artifact}/${version}/${artifact}-${version}.pom`;
  const response = await request(url, { method: 'HEAD', accept: '*/*' }, options, deadline, label(entry));
  const parsed = Date.parse(response.headers.get('last-modified') ?? '');
  return Number.isNaN(parsed) ? null : parsed;
}

async function npmPublishTime(entry, options, deadline, cache) {
  const key = `${entry.registry} ${entry.name}`;
  if (!cache.has(key)) {
    const url = `${entry.registry}/${entry.name.replace('/', '%2f')}`;
    cache.set(
      key,
      (async () => {
        const response = await request(url, { method: 'GET', accept: 'application/json' }, options, deadline, entry.name);
        try {
          return await response.json();
        } catch {
          throw new Error(`Registry returned unreadable JSON for ${entry.name}; cannot confirm its publish date.`);
        }
      })(),
    );
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
  const maven = new Map();
  readMavenClosure(options.closure, maven);
  readMavenPlugins(options.plugins, maven);
  const entries = [...maven.values(), ...readNpmLockfile(options.npmLockfile, options)];
  if (options.days === 0) {
    console.log('Dependency quarantine window is 0 days; nothing to check.');
    return;
  }
  const overall = new AbortController();
  const timer = setTimeout(() => overall.abort(new Error('overall time limit reached')), options.overallMs);
  timer.unref();

  const cutoff = Date.now() - options.days * MS_PER_DAY;
  const npmCache = new Map();
  const published = new Map();
  let next = 0;
  const worker = async () => {
    while (next < entries.length) {
      const entry = entries[next++];
      try {
        published.set(
          entry,
          entry.kind === 'maven'
            ? await mavenPublishTime(entry, options, overall.signal)
            : await npmPublishTime(entry, options, overall.signal, npmCache),
        );
      } catch (error) {
        console.error(describe(error));
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(LOOKUP_CONCURRENCY, entries.length) }, worker));
  clearTimeout(timer);

  const violations = [];
  const unconfirmed = [];
  for (const entry of entries) {
    const time = published.get(entry) ?? null;
    if (time === null) {
      unconfirmed.push(entry);
    } else if (time > cutoff) {
      violations.push({
        entry,
        published: new Date(time).toISOString(),
        clears: new Date(time + options.days * MS_PER_DAY).toISOString(),
      });
    }
  }

  for (const entry of unconfirmed) {
    console.error(`Dependency quarantine: no confirmed publish date for ${label(entry)}.`);
  }
  if (violations.length > 0) {
    console.error(
      `Dependency quarantine: ${violations.length} third-party artifact(s) were published less than ${options.days} day(s) ago.`,
    );
    for (const { entry, published: at, clears } of violations) {
      console.error(`  ${label(entry)} published ${at}, clears ${clears}`);
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
  console.error(describe(error));
  process.exitCode = 1;
});
