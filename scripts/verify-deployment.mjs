import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const args = ['compose', '-p', 'copartverify', '-f', fileURLToPath(new URL('./compose.verify.yml', import.meta.url))];
const compose = (...command) => execFileSync('docker', [...args, ...command], {encoding: 'utf8'}).trim();
const docker = (...command) => execFileSync('docker', command, {encoding: 'utf8'}).trim();
const sql = (db, query) => compose('exec', '-T', 'db', 'psql', '-U', 'verification', '-d', db, '-v', 'ON_ERROR_STOP=1', '-Atc', query);
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function waitFor(name, shouldStart) {
  for (let i = 0; i < 120; i++) {
    const logs = docker('logs', name);
    const state = JSON.parse(docker('inspect', '--format', '{{json .State}}', name));
    if (shouldStart && /Started EmployeeManagementApplication/.test(logs)) return logs;
    if (!state.Running) {
      if (!shouldStart && state.ExitCode !== 0) return logs;
      throw Error(`Unexpected startup result for ${name}: ${logs.slice(-4000)}`);
    }
    await sleep(500);
  }
  throw Error(`Timed out waiting for ${name}`);
}
let passed = 0;
assert.equal(sql('verification', 'select current_database()'), 'verification');
for (const version of [3, 4, 5]) {
  const database = `upgrade_v${version}`;
  const name = `copartverify-upgrade-v${version}`;
  sql('verification', `drop database if exists ${database} with (force)`);
  sql('verification', `create database ${database}`);
  try {
    compose('run', '-d', '--no-deps', '--name', name, 'backend',
      `--spring.datasource.url=jdbc:postgresql://db:5432/${database}`, `--spring.flyway.target=${version}`,
      '--spring.jpa.hibernate.ddl-auto=none', '--spring.profiles.active=');
    await waitFor(name, true);
    assert.equal(sql(database, 'select count(*) from flyway_schema_history where success and version is not null'), String(version));
    docker('rm', '-f', name);
    compose('run', '-d', '--no-deps', '--name', name, 'backend',
      `--spring.datasource.url=jdbc:postgresql://db:5432/${database}`, '--spring.profiles.active=');
    await waitFor(name, true);
    assert.equal(sql(database, 'select count(*) from flyway_schema_history where success and version is not null'), '6');
    assert.equal(sql(database, 'select count(*) from employees'), '0');
    console.log(`PASS V${version}-V6 upgrade and no demo users outside demo profile`); passed++;
  } finally {docker('rm', '-f', name);}
}
for (const kind of ['missing', 'weak']) {
  const name = `copartverify-secret-${kind}`;
  try {
    if (kind === 'missing') compose('run', '-d', '--no-deps', '--name', name, '--entrypoint', 'sh', 'backend',
      '-c', 'unset JWT_SECRET; exec java -jar app.jar --spring.profiles.active=');
    else compose('run', '-d', '--no-deps', '--name', name, '-e', 'JWT_SECRET=short', 'backend', '--spring.profiles.active=');
    const logs = await waitFor(name, false);
    assert.match(logs, /JWT_SECRET|signing key|secret|WeakKeyException/i);
    console.log(`PASS ${kind} JWT secret prevents startup`); passed++;
  } finally {docker('rm', '-f', name);}
}
console.log(`Deployment verification: ${passed} passed`);
