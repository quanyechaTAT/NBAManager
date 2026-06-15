const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

  // Step 1: Navigate to login page
  console.log('=== Step 1: Navigate to App ===');
  try {
    await page.goto('http://127.0.0.1:5173/', { waitUntil: 'networkidle', timeout: 30000 });
    console.log('App loaded successfully');
  } catch (e) {
    console.log('Error loading app:', e.message);
    await browser.close();
    return;
  }

  await page.waitForTimeout(2000);

  // Step 2: Login with admin credentials
  console.log('\n=== Step 2: Login ===');

  // Fill username
  const usernameInput = await page.$('input[placeholder*="用户名"], input[type="text"], input:first-of-type');
  if (usernameInput) {
    await usernameInput.click();
    await usernameInput.fill('admin');
    console.log('Filled username: admin');
  } else {
    console.log('ERROR: Username input not found');
  }

  // Fill password
  const passwordInput = await page.$('input[placeholder*="密码"], input[type="password"], input:nth-of-type(2)');
  if (passwordInput) {
    await passwordInput.click();
    await passwordInput.fill('admin123');
    console.log('Filled password: admin123');
  } else {
    console.log('ERROR: Password input not found');
  }

  // Click login button
  const loginBtn = await page.$('button:has-text("登录"), button[type="submit"], button');
  if (loginBtn) {
    await loginBtn.click();
    console.log('Clicked login button');
    await page.waitForTimeout(3000);
  } else {
    console.log('ERROR: Login button not found');
  }

  // Check if login succeeded
  const currentUrl = page.url();
  console.log('Current URL after login:', currentUrl);
  const bodyText = await page.textContent('body');
  console.log('After login - page content length:', bodyText.length, 'chars');

  // Step 3: Navigate to Players page
  console.log('\n=== Step 3: Navigate to Players Page ===');
  try {
    await page.goto('http://127.0.0.1:5173/players', { waitUntil: 'networkidle', timeout: 30000 });
    console.log('Players page loaded');
  } catch (e) {
    console.log('Error loading players page:', e.message);
  }

  await page.waitForTimeout(5000);

  // Step 4: Analyze the page
  console.log('\n=== Step 4: Analyze Page Content ===');
  const pageTitle = await page.title();
  console.log('Page title:', pageTitle);

  // Check heading
  const heading = await page.$eval('h1, h2, h3', el => el.textContent.trim()).catch(() => 'No heading');
  console.log('Heading:', heading);

  // Full body text
  const pageBody = await page.textContent('body');
  console.log('Body text length:', pageBody.length);

  // Check for player names - common NBA names
  const nbaNames = ['LeBron', 'Curry', 'Durant', 'Harden', 'Giannis', 'Jokic', 'Doncic', 'Tatum', 'Embiid', 'Irving',
    '詹姆斯', '库里', '杜兰特', '字母哥', '约基奇', '东契奇', '塔图姆', '恩比德'];
  const foundNames = nbaNames.filter(name => pageBody.includes(name));
  console.log('Found NBA player names:', foundNames.length > 0 ? foundNames : 'None');

  // Check for stats
  const hasStats = /PPG|APG|RPG|得分|篮板|助攻|\d+\.\d+/.test(pageBody);
  console.log('Has stats data:', hasStats);

  // Check for search/filter
  const searchInput = await page.$('input[placeholder*="搜索"], input[placeholder*="search"], input[type="search"], input[type="text"]');
  console.log('Search input found:', !!searchInput);

  // Check for tables
  const tables = await page.$$('table');
  console.log('Tables found:', tables.length);

  // Check for player cards
  const cards = await page.$$('[class*="player"], [class*="card"], [class*="item"]');
  console.log('Player/card elements:', cards.length);

  // List some visible text
  console.log('\n=== Page Text Snippet (first 2000 chars) ===');
  console.log(pageBody.substring(0, 2000));

  // Step 5: Test search functionality if available
  if (searchInput) {
    console.log('\n=== Step 5: Test Search ===');
    await searchInput.click();
    await searchInput.fill('LeBron');
    await page.waitForTimeout(2000);

    const searchResults = await page.textContent('body');
    const searchHasLeBron = searchResults.includes('LeBron') || searchResults.includes('詹姆斯');
    console.log('Search results contain LeBron:', searchHasLeBron);

    // Take screenshot after search
    await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/04-players-search.png' });
    console.log('Search screenshot saved');

    // Clear search
    await searchInput.fill('');
    await page.waitForTimeout(1000);
  }

  // Step 6: Take final screenshot
  console.log('\n=== Step 6: Take Screenshot ===');
  await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/04-players.png', fullPage: false });
  console.log('Final screenshot saved to test-screenshots/04-players.png');

  // Check for console errors
  const consoleErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });

  // Final summary
  console.log('\n=== SUMMARY ===');
  console.log('Page loads OK: YES (URL:', page.url(), ')');
  console.log('Console errors:', consoleErrors.length === 0 ? 'None' : consoleErrors.join('; '));

  await browser.close();
})();
