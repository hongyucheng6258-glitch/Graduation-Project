const assert = require('node:assert/strict');
const { test } = require('node:test');
const os = require('node:os');
const path = require('node:path');
const { resolveEndpoints, resolveTestAssetPath } = require('./config.cjs');

test('uses the existing localhost addresses by default', () => {
  assert.deepEqual(resolveEndpoints({}), {
    BACKEND: 'http://localhost:8080',
    STUDENT_WEB: 'http://localhost:5173',
    ADMIN_WEB: 'http://localhost:5174'
  });
});

test('allows an isolated browser environment to override every address', () => {
  assert.deepEqual(resolveEndpoints({
    E2E_BACKEND_URL: 'http://localhost:18080/',
    E2E_STUDENT_WEB_URL: 'http://localhost:15173/',
    E2E_ADMIN_WEB_URL: 'http://localhost:15174/'
  }), {
    BACKEND: 'http://localhost:18080',
    STUDENT_WEB: 'http://localhost:15173',
    ADMIN_WEB: 'http://localhost:15174'
  });
});

test('uses a repository-relative directory for local E2E image fixtures', () => {
  assert.equal(resolveTestAssetPath('02-机械键盘.png', {}), path.resolve(__dirname, '../../../测试素材/02-机械键盘.png'));
});

test('allows E2E image fixtures to live outside the repository', () => {
  const externalAssetDir = path.resolve(os.tmpdir(), 'e2e-assets');
  assert.equal(resolveTestAssetPath('05-校园晚霞.png', { E2E_TEST_ASSET_DIR: externalAssetDir }), path.join(externalAssetDir, '05-校园晚霞.png'));
});
