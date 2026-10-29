const CATEGORY_IDS = Array.from({ length: 20 }, (_, index) => String(index + 1).padStart(2, '0'));

function parseOnlyCategories(value) {
  if (value == null) return null;
  const raw = String(value);
  if (!raw.trim()) throw new Error('--only must select at least one E2E category.');

  const requested = raw.split(',').map(category => category.trim());
  if (requested.some(category => !category)) throw new Error('--only contains an empty category ID.');
  const unknown = [...new Set(requested.filter(category => !CATEGORY_IDS.includes(category)))];
  if (unknown.length) throw new Error(`--only contains unknown E2E category ID(s): ${unknown.join(', ')}.`);
  return new Set(requested);
}

function resolveOnlyCategories(args) {
  const filters = args.filter(arg => arg === '--only' || arg.startsWith('--only='));
  if (filters.length > 1) throw new Error('--only may be supplied only once.');
  if (!filters.length) return null;
  if (filters[0] === '--only') throw new Error('--only requires the format --only=01,02.');
  return parseOnlyCategories(filters[0].slice('--only='.length));
}

function getExitCode(results, hasRuntimeError = false) {
  if (hasRuntimeError) return 1;
  return results.some(result => result?.status === 'FAIL' || result?.status === 'BLOCK') ? 1 : 0;
}

module.exports = { getExitCode, parseOnlyCategories, resolveOnlyCategories };
