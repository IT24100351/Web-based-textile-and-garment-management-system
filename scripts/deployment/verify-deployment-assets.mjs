import { readFileSync, existsSync, statSync } from 'node:fs';

const required = [
  'docs/deployment/TGMS-84-deployment-backup-recovery.md',
  'docs/deployment/production.env.example',
  'scripts/deployment/check-production-env.sh',
  'scripts/deployment/mysql-backup.sh',
  'scripts/deployment/mysql-restore.sh',
  'scripts/deployment/smoke-test.sh',
  'backend/src/main/resources/application-prod.properties',
  'database/migrations/db.changelog-master.xml',
];
for (const path of required) {
  if (!existsSync(path)) throw new Error(`Missing TGMS-84 deployment asset: ${path}`);
}
for (const path of required.filter((p) => p.endsWith('.sh'))) {
  if ((statSync(path).mode & 0o111) === 0) throw new Error(`Deployment script is not executable: ${path}`);
}

const prod = readFileSync('backend/src/main/resources/application-prod.properties', 'utf8');
if (!prod.includes('tgms.client-origin=${CLIENT_ORIGIN}')) throw new Error('Production client origin must be environment supplied');
if (!prod.includes('tgms.auth.cookie-secure=true')) throw new Error('Production cookies must be forced Secure');

const env = readFileSync('docs/deployment/production.env.example', 'utf8');
for (const requiredName of ['CLIENT_ORIGIN','DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET']) {
  if (!env.includes(`${requiredName}=`)) throw new Error(`Production env example is missing ${requiredName}`);
}
for (const forbidden of [/-----BEGIN .*PRIVATE KEY-----/, /AKIA[0-9A-Z]{16}/, /sk_live_[A-Za-z0-9]+/]) {
  if (forbidden.test(env)) throw new Error(`Production env example contains secret-like material: ${forbidden}`);
}

const smoke = readFileSync('scripts/deployment/smoke-test.sh', 'utf8');
for (const contract of ['/api/health', '/api/products', '/api/profile']) {
  if (!smoke.includes(contract)) throw new Error(`Smoke test is missing ${contract}`);
}

const runbook = readFileSync('docs/deployment/TGMS-84-deployment-backup-recovery.md', 'utf8');
for (const phrase of ['no committed or documented team-approved hosting platform', 'Liquibase', 'mysql-backup.sh', 'mysql-restore.sh', 'smoke-test.sh']) {
  if (!runbook.toLowerCase().includes(phrase.toLowerCase())) throw new Error(`Runbook is missing required release guidance: ${phrase}`);
}

console.log('[deploy:verify] PASS: TGMS-84 deployment, migration, backup, restore and smoke-test assets are internally consistent');
