// Contract tests for check-dependency-age.mjs. A local HTTP server stands in for
// both Maven Central's repository and the npm registry, so nothing here reaches
// the network.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import { createServer } from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { after, before, test } from 'node:test';
import { fileURLToPath } from 'node:url';

const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'check-dependency-age.mjs');
const DAY = 86_400_000;
const daysAgo = (days) => Date.now() - days * DAY;

// Publish times the stand-in serves, by Maven "group:artifact:version" and by npm package.
const mavenTimes = { 'org.example:plugin-old:1.0': daysAgo(90) };
const npmTimes = {};
// Requests the stand-in has seen, by Maven "group:artifact:version".
const mavenRequests = {};
let server;
let base;

before(async () => {
  server = createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname.startsWith('/maven2/')) {
      // Repository layout: /maven2/<group path>/<artifact>/<version>/<artifact>-<version>.pom
      const segments = url.pathname.slice('/maven2/'.length).split('/');
      const [artifact, version] = segments.slice(-3, -1);
      const key = `${segments.slice(0, -3).join('.')}:${artifact}:${version}`;
      mavenRequests[key] = (mavenRequests[key] ?? 0) + 1;
      if (req.method !== 'HEAD' || !url.pathname.endsWith(`/${artifact}-${version}.pom`)) {
        res.writeHead(405).end();
        return;
      }
      if (artifact.startsWith('stalled-')) return; // accepts the request and never answers
      if (artifact.startsWith('flaky-') && mavenRequests[key] === 1) {
        res.writeHead(503).end();
        return;
      }
      if (artifact.startsWith('undated-')) {
        res.writeHead(200).end();
        return;
      }
      const timestamp = mavenTimes[key];
      if (timestamp === undefined) {
        res.writeHead(404).end();
        return;
      }
      res.writeHead(200, { 'last-modified': new Date(timestamp).toUTCString() }).end();
      return;
    }
    const name = decodeURIComponent(url.pathname.slice(1));
    if (name === 'broken') {
      res.writeHead(500).end();
      return;
    }
    const time = npmTimes[name];
    if (!time) {
      res.writeHead(404).end('{}');
      return;
    }
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ name, time }));
  });
  await new Promise((done) => server.listen(0, '127.0.0.1', done));
  base = `http://127.0.0.1:${server.address().port}`;
});

after(() => new Promise((done) => server.close(done)));

function fixture(name, text) {
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'java-age-')), name);
  fs.writeFileSync(file, text);
  return file;
}

// The license-maven-plugin aggregate report.
function closureFile(coords) {
  const lines = coords.map((c) => `     (Apache-2.0) Example (${c} - https://example.invalid)`);
  return fixture('dependencies.txt', `\nLists of 1 third-party dependencies.\n${lines.join('\n')}\n`);
}

// The maven-dependency-plugin resolve-plugins listing: each plugin, then its own artifacts.
function pluginsFile(plugins) {
  const lines = Object.entries(plugins).flatMap(([plugin, deps]) => [`   ${plugin}`, ...deps.map((d) => `      ${d}`)]);
  return fixture('plugins.txt', `\nThe following plugins have been resolved:\n${lines.join('\n')}\n\n`);
}

function lockfile(packages = {}) {
  return fixture(
    'package-lock.json',
    JSON.stringify({ lockfileVersion: 3, packages: { '': { name: 'tools' }, ...packages } }),
  );
}

function run(
  coords,
  { npm = lockfile(), plugins = { 'org.example:plugin-old:jar:1.0': [] }, env = {}, args = [] } = {},
) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [script, ...args], {
      env: {
        ...process.env,
        EZCLASSIFY_DEPENDENCY_AGE_CLOSURE: closureFile(coords),
        EZCLASSIFY_DEPENDENCY_AGE_PLUGINS: pluginsFile(plugins),
        EZCLASSIFY_DEPENDENCY_AGE_NPM_LOCKFILE: npm,
        EZCLASSIFY_DEPENDENCY_AGE_CENTRAL: `${base}/maven2`,
        EZCLASSIFY_DEPENDENCY_AGE_REGISTRY: base,
        EZCLASSIFY_DEPENDENCY_AGE_RETRY_DELAY_MS: '0',
        ...env,
      },
    });
    let output = '';
    child.stdout.on('data', (d) => (output += d));
    child.stderr.on('data', (d) => (output += d));
    child.on('close', (status) => resolve({ status, output }));
  });
}

test('refuses a Maven artifact published inside the window and says when it clears', async () => {
  mavenTimes['org.example:fresh:1.0'] = daysAgo(2);
  const { status, output } = await run(['org.example:fresh:1.0']);
  assert.equal(status, 1);
  assert.match(output, /org\.example:fresh:1\.0 published .*, clears \d{4}-/);
});

test('passes a Maven artifact published before the window', async () => {
  mavenTimes['org.example:old:2.0'] = daysAgo(30);
  const { status, output } = await run(['org.example:old:2.0']);
  assert.equal(status, 0, output);
  assert.match(output, /passed: 2 third-party/);
});

test('the window length is configurable', async () => {
  mavenTimes['org.example:mid:1.0'] = daysAgo(10);
  assert.equal((await run(['org.example:mid:1.0'])).status, 0);
  assert.equal((await run(['org.example:mid:1.0'], { env: { EZCLASSIFY_DEPENDENCY_AGE_DAYS: '14' } })).status, 1);
});

test('exempts first-party Maven groups without asking the registry', async () => {
  mavenTimes['org.example:old:2.0'] = daysAgo(30);
  const { status, output } = await run(['com.ezclassify:ezclassify-rest-client:1.0.0', 'org.example:old:2.0'], {
    plugins: { 'org.example:plugin-old:jar:1.0': ['com.ezclassify.tools:helper:jar:0.1'] },
  });
  assert.equal(status, 0, output);
  assert.match(output, /passed: 2 third-party/);
  assert.equal(mavenRequests['com.ezclassify:ezclassify-rest-client:1.0.0'], undefined);
  assert.equal(mavenRequests['com.ezclassify.tools:helper:0.1'], undefined);
});

test('refuses an npm package published inside the window and passes an old one', async () => {
  npmTimes.freshpkg = { '1.0.0': new Date(daysAgo(1)).toISOString() };
  npmTimes.oldpkg = { '1.0.0': new Date(daysAgo(60)).toISOString() };
  mavenTimes['org.example:old:2.0'] = daysAgo(30);
  const fresh = lockfile({ 'node_modules/freshpkg': { version: '1.0.0' } });
  const old = lockfile({ 'node_modules/oldpkg': { version: '1.0.0' } });
  const refused = await run(['org.example:old:2.0'], { npm: fresh });
  assert.equal(refused.status, 1);
  assert.match(refused.output, /freshpkg@1\.0\.0 published/);
  assert.equal((await run(['org.example:old:2.0'], { npm: old })).status, 0);
});

test('exempts first-party npm packages but not a same-named package from another registry', async () => {
  npmTimes['@ezclassify/thing'] = { '1.0.0': new Date(daysAgo(1)).toISOString() };
  mavenTimes['org.example:old:2.0'] = daysAgo(30);
  const exempt = lockfile({ 'node_modules/@ezclassify/thing': { version: '1.0.0' } });
  assert.equal((await run(['org.example:old:2.0'], { npm: exempt })).status, 0);
  const elsewhere = lockfile({
    'node_modules/@ezclassify/thing': { version: '1.0.0', resolved: `${base}/@ezclassify/thing/-/thing-1.0.0.tgz` },
  });
  assert.equal((await run(['org.example:old:2.0'], { npm: elsewhere })).status, 1);
});

test('fails closed when Central does not have the version, without retrying the answer', async () => {
  const { status, output } = await run(['org.example:unknown:9.9']);
  assert.equal(status, 1);
  assert.match(output, /no confirmed publish date for org\.example:unknown:9\.9/);
  assert.equal(mavenRequests['org.example:unknown:9.9'], 1);
});

test('fails closed when Central serves the pom without a publish time', async () => {
  const { status, output } = await run(['org.example:undated-a:1.0']);
  assert.equal(status, 1);
  assert.match(output, /no confirmed publish date for org\.example:undated-a:1\.0/);
});

test('retries a lookup that fails transiently and then uses the answer', async () => {
  mavenTimes['org.example:flaky-a:1.0'] = daysAgo(30);
  const { status, output } = await run(['org.example:flaky-a:1.0']);
  assert.equal(status, 0, output);
  assert.equal(mavenRequests['org.example:flaky-a:1.0'], 2);
});

test('refuses a build plugin dependency published inside the window', async () => {
  mavenTimes['org.example:old:2.0'] = daysAgo(30);
  mavenTimes['org.example:plugin-dep:3.1'] = daysAgo(1);
  const { status, output } = await run(['org.example:old:2.0'], {
    plugins: { 'org.example:plugin-old:jar:1.0': ['org.example:plugin-old:jar:1.0', 'org.example:plugin-dep:jar:runtime:3.1'] },
  });
  assert.equal(status, 1);
  assert.match(output, /org\.example:plugin-dep:3\.1 published .*, clears/);
});

test('refuses to pass when the plugin listing names no plugins', async () => {
  mavenTimes['org.example:old:2.0'] = daysAgo(30);
  const { status, output } = await run(['org.example:old:2.0'], { plugins: {} });
  assert.equal(status, 1);
  assert.match(output, /Parsed no plugins/);
});

test('fails closed when the npm registry cannot be read', async () => {
  mavenTimes['org.example:old:2.0'] = daysAgo(30);
  const broken = lockfile({ 'node_modules/broken': { version: '1.0.0' } });
  const { status, output } = await run(['org.example:old:2.0'], { npm: broken });
  assert.equal(status, 1);
  assert.match(output, /no confirmed publish date for broken@1\.0\.0/);
});

test('fails closed when the registry is unreachable', async () => {
  const { status } = await run(['org.example:old:2.0'], { env: { EZCLASSIFY_DEPENDENCY_AGE_CENTRAL: 'http://127.0.0.1:1' } });
  assert.equal(status, 1);
});

test('the overall bound ends a registry that never answers and fails the run', async () => {
  const { status, output } = await run(['org.example:stalled-a:1.0'], {
    env: { EZCLASSIFY_DEPENDENCY_AGE_OVERALL_TIMEOUT_MS: '300' },
  });
  assert.equal(status, 1);
  assert.match(output, /no confirmed publish date for org\.example:stalled-a:1\.0/);
});

test('refuses to pass when the closure report lists no third-party artifacts', async () => {
  const { status, output } = await run([]);
  assert.equal(status, 1);
  assert.match(output, /Parsed no third-party artifacts/);
});
