const assert = require('node:assert/strict');
const { test } = require('node:test');
const { getExitCode, parseOnlyCategories, resolveOnlyCategories } = require('./run-status.cjs');

test('returns zero when all executed checks pass', () => {
  assert.equal(getExitCode([{ status: 'PASS' }]), 0);
});

test('returns zero for documented uncovered checks without failures', () => {
  assert.equal(getExitCode([{ status: 'UNCOVERED' }]), 0);
});

test('returns non-zero when any check fails or is blocked', () => {
  assert.equal(getExitCode([{ status: 'FAIL' }]), 1);
  assert.equal(getExitCode([{ status: 'BLOCK' }]), 1);
});

test('returns non-zero when a test category throws before recording a result', () => {
  assert.equal(getExitCode([], true), 1);
});

test('runs all categories when no filter is supplied', () => {
  assert.equal(parseOnlyCategories(undefined), null);
});

test('accepts known categories and rejects empty or unknown filters', () => {
  assert.deepEqual([...parseOnlyCategories('01, 18,20')], ['01', '18', '20']);
  assert.throws(() => parseOnlyCategories(''), /at least one/);
  assert.throws(() => parseOnlyCategories('01,99'), /unknown/);
  assert.throws(() => parseOnlyCategories('01,,02'), /empty/);
});

test('rejects a bare --only flag instead of silently running every category', () => {
  assert.throws(() => resolveOnlyCategories(['node', 'run-all.cjs', '--only']), /--only requires/);
});
