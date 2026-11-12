// Contract tests for check-dependency-age.mjs. A local HTTP server stands in for
// both Maven Central's search index and the npm registry, so nothing here
// reaches the network.
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
const mavenTimes = {};
const npmTimes = {};
let server;
let base;

before(async () => {
  server = createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/solrsearch/select') {
      const q = url.searchParams.get('q');
      const [, g, a, v] = /g:"(.+)" AND a:"(.+)" AND v:"(.+)"/.exec(q);
      if (a.startsWith('stalled-')) return; // accepts the request and never answers
      const timestamp = mavenTimes[`${g}:${a}:${v}`];
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ response: { docs: timestamp === undefined ? [] : [{ timestamp }] } }));
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

function closureFile(coords) {
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'java-age-')), 'THIRD-PARTY.txt');
  const lines = coords.map((c) => `     (Apache-2.0) Example (${c} - https://example.invalid)`);
  fs.writeFileSync(file, `\nLists of 1 third-party dependencies.\n${lines.join('\n')}\n`);
  return file;
}

function lockfile(packages = {}) {
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'java-age-')), 'package-lock.json');
  fs.writeFileSync(file, JSON.stringify({ lockfileVersion: 3, packages: { '': { name: 'tools' }, ...packages } }));
  return file;
}

function run(coords, { npm = lockfile(), env = {}, args = [] } = {}) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [script, ...args], {
      env: {
        ...process.env,
        EZCLASSIFY_DEPENDENCY_AGE_CLOSURE: closureFile(coords),
        EZCLASSIFY_DEPENDENCY_AGE_NPM_LOCKFILE: npm,
        EZCLASSIFY_DEPENDENCY_AGE_CENTRAL: base,
        EZCLASSIFY_DEPENDENCY_AGE_REGISTRY: base,
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
  assert.match(output, /passed: 1 third-party/);
});

test('the window length is configurable', async () => {
  mavenTimes['org.example:mid:1.0'] = daysAgo(10);
  assert.equal((await run(['org.example:mid:1.0'])).status, 0);
  assert.equal((await run(['org.example:mid:1.0'], { env: { EZCLASSIFY_DEPENDENCY_AGE_DAYS: '14' } })).status, 1);
});

test('exempts first-party Maven groups without asking the registry', async () => {
  mavenTimes['org.example:old:2.0'] = daysAgo(30);
  const { status, output } = await run(['com.ezclassify:ezclassify-rest-client:1.0.0', 'org.example:old:2.0']);
  assert.equal(status, 0, output);
  assert.match(output, /passed: 1 third-party/);
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

test('fails closed when Central has no publish time for the version', async () => {
  const { status, output } = await run(['org.example:unknown:9.9']);
  assert.equal(status, 1);
  assert.match(output, /no confirmed publish date for org\.example:unknown:9\.9/);
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
