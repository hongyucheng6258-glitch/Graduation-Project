const path = require('node:path');

const trimTrailingSlashes = (value, fallback) => {
  const candidate = typeof value === 'string' ? value.trim() : '';
  return candidate ? candidate.replace(/\/+$/, '') : fallback;
};

function resolveEndpoints(env = process.env) {
  return {
    BACKEND: trimTrailingSlashes(env.E2E_BACKEND_URL, 'http://localhost:8080'),
    STUDENT_WEB: trimTrailingSlashes(env.E2E_STUDENT_WEB_URL, 'http://localhost:5173'),
    ADMIN_WEB: trimTrailingSlashes(env.E2E_ADMIN_WEB_URL, 'http://localhost:5174')
  };
}

function resolveTestAssetPath(fileName, env = process.env) {
  const configuredDir = typeof env.E2E_TEST_ASSET_DIR === 'string' ? env.E2E_TEST_ASSET_DIR.trim() : '';
  const assetDir = configuredDir
    ? path.resolve(configuredDir)
    : path.resolve(__dirname, '../../../测试素材');
  return path.join(assetDir, fileName);
}

module.exports = { resolveEndpoints, resolveTestAssetPath };
