const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const { resolveEndpoints } = require('./e2e/config.cjs');

function launchBrowser({ softwareWebGL = true } = {}) {
  const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
  return chromium.launch({
    headless: true,
    ...(executablePath ? { executablePath } : { channel: 'chrome' }),
    ...(softwareWebGL ? { args: ['--no-sandbox', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] } : {})
  });
}

module.exports = { ...resolveEndpoints(), launchBrowser };
