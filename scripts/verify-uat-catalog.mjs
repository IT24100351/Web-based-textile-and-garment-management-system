#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const root = process.cwd();
const csvPath = path.join(root, 'docs/testing/TGMS-83-uat-evidence.csv');
const defectPath = path.join(root, 'docs/testing/TGMS-83-defect-backlog.md');
const checklistPath = path.join(root, 'docs/testing/TGMS-83-user-acceptance-testing.md');

for (const required of [csvPath, defectPath, checklistPath]) {
  if (!fs.existsSync(required)) {
    throw new Error(`Missing TGMS-83 UAT artifact: ${path.relative(root, required)}`);
  }
}

const lines = fs.readFileSync(csvPath, 'utf8').trim().split(/\r?\n/);
if (lines.length < 2) throw new Error('UAT evidence catalog has no cases.');
const header = lines.shift().split(',');
const expectedHeader = ['case_id','module','actor','scenario','expected_result','automated_evidence','current_status'];
if (header.join('|') !== expectedHeader.join('|')) {
  throw new Error(`Unexpected UAT CSV header: ${header.join(',')}`);
}

const requiredModules = new Set(['Product','Supplier','Inventory','Order','Production','Delivery']);
const representedModules = new Set();
const ids = new Set();
const allowedStatuses = new Set(['PASS','FAIL','READY FOR EXECUTION','BLOCKED']);

const javaFiles = [];
function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full);
    else if (entry.isFile() && entry.name.endsWith('.java')) javaFiles.push(full);
  }
}
walk(path.join(root, 'backend/src/test/java'));

const javaByClass = new Map(javaFiles.map((file) => [path.basename(file, '.java'), file]));
const problems = [];
for (const [index, line] of lines.entries()) {
  const fields = line.split(',');
  if (fields.length !== expectedHeader.length) {
    problems.push(`line ${index + 2}: expected ${expectedHeader.length} fields, found ${fields.length}`);
    continue;
  }
  const [caseId, module, , , , evidence, status] = fields;
  if (!/^UAT-[A-Z0-9]+-\d{2}$/.test(caseId)) problems.push(`${caseId}: invalid case ID format`);
  if (ids.has(caseId)) problems.push(`${caseId}: duplicate case ID`);
  ids.add(caseId);
  representedModules.add(module);
  if (!allowedStatuses.has(status)) problems.push(`${caseId}: unsupported status '${status}'`);

  const dot = evidence.lastIndexOf('.');
  if (dot <= 0 || dot === evidence.length - 1) {
    problems.push(`${caseId}: evidence must be ClassName.methodName`);
    continue;
  }
  const className = evidence.slice(0, dot);
  const methodName = evidence.slice(dot + 1);
  const sourceFile = javaByClass.get(className);
  if (!sourceFile) {
    problems.push(`${caseId}: evidence class ${className} not found`);
    continue;
  }
  const source = fs.readFileSync(sourceFile, 'utf8');
  const methodPattern = new RegExp(`\\bvoid\\s+${methodName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*\\(`);
  if (!methodPattern.test(source)) problems.push(`${caseId}: evidence method ${evidence} not found`);
}

for (const module of requiredModules) {
  if (!representedModules.has(module)) problems.push(`required module '${module}' has no UAT evidence case`);
}
if (!representedModules.has('Cross-module')) problems.push('missing full cross-module UAT evidence');
if (!representedModules.has('Security')) problems.push('missing security UAT evidence');

if (problems.length) {
  console.error('[uat-catalog] FAILED');
  for (const problem of problems) console.error(` - ${problem}`);
  process.exit(1);
}

console.log(`[uat-catalog] PASS: ${ids.size} evidence cases verified.`);
console.log(`[uat-catalog] Covered modules: ${[...requiredModules].join(', ')}.`);
console.log('[uat-catalog] Cross-module and security release-gate evidence are present.');
