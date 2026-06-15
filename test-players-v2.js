const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

  console.log('=== Navigating to App ===');
  await page.goto('http://127.0.0.1:5173/', { waitUntil: 'networkidle', timeout: 30000 });
  await page.waitForTimeout(2000);

  // Login
  console.log('=== Logging in ===');
  const usernameInput = await page.$('input[placeholder*="用户名"], input:first-of-type');
  await usernameInput.fill('admin');
  const passwordInput = await page.$('input[placeholder*="密码"], input[type="password"]');
  await passwordInput.fill('admin123');
  await page.click('button:has-text("登录")');
  await page.waitForTimeout(3000);

  // Navigate to Players
  console.log('\n=== Navigating to Players Page ===');
  await page.goto('http://127.0.0.1:5173/players', { waitUntil: 'load', timeout: 60000 });
  await page.waitForTimeout(5000);

  // Get initial player list
  console.log('\n=== Initial Player Data ===');
  const initialRows = await page.$$('table tbody tr, [class*="row"], [class*="item"]');
  console.log('Initial visible rows:', initialRows.length);

  // Get player names from table
  const playerNames = await page.$$eval('table tbody tr, [class*="row"]', rows => {
    return rows.slice(0, 20).map(row => row.textContent.trim().substring(0, 100));
  }).catch(() => []);
  console.log('First 10 player rows:');
  playerNames.slice(0, 10).forEach((name, i) => console.log(`  ${i + 1}. ${name}`));

  // Count total players
  const totalText = await page.textContent('body');
  const totalMatch = totalText.match(/共\s*(\d+)\s*条/);
  console.log('Total players:', totalMatch ? totalMatch[1] : 'unknown');

  // Test Search with Chinese name
  console.log('\n=== Testing Search with Chinese Name ===');
  const searchInput = await page.$('input[placeholder*="搜索"], input[placeholder*="查询"], input[type="text"]');
  if (searchInput) {
    await searchInput.click();
    await searchInput.fill('詹姆斯');
    await page.waitForTimeout(2000);
    const afterSearch = await page.textContent('body');
    const hasLeBron = afterSearch.includes('詹姆斯');
    console.log('Search "詹姆斯" - found:', hasLeBron);
    const searchTotal = afterSearch.match(/共\s*(\d+)\s*条/);
    console.log('Results after search:', searchTotal ? searchTotal[1] : 'unknown');

    // Take screenshot of search result
    await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/04-players-search-zh.png' });
    console.log('Search screenshot saved');

    // Clear search
    await searchInput.fill('');
    await page.waitForTimeout(1000);
  }

  // Test filter by team
  console.log('\n=== Testing Team Filter ===');
  const teamFilter = await page.$('select:near(:text("筛选球队")), select:near(:text("球队"))');
  if (teamFilter) {
    const options = await teamFilter.$$eval('option', opts => opts.map(o => o.textContent.trim()));
    console.log('Team filter options:', options.slice(0, 10));
  }

  // Test filter by position
  console.log('\n=== Testing Position Filter ===');
  const positionFilter = await page.$('select:near(:text("筛选位置")), select:near(:text("位置"))');
  if (positionFilter) {
    const options = await positionFilter.$$eval('option', opts => opts.map(o => o.textContent.trim()));
    console.log('Position filter options:', options.slice(0, 10));
  }

  // Test pagination
  console.log('\n=== Testing Pagination ===');
  const paginationInfo = await page.textContent('body');
  const paginationMatch = paginationInfo.match(/共\s*(\d+)\s*条\s*(\d+)\s*\d+\s*\d+/);
  console.log('Pagination info:', paginationMatch ? `${paginationMatch[1]} total, page ${paginationMatch[2]}` : 'unknown');

  // Check table columns
  console.log('\n=== Table Structure ===');
  const headers = await page.$$eval('table th, table thead td', ths => ths.map(th => th.textContent.trim()));
  console.log('Table headers:', headers);

  // Check for action buttons
  const editBtns = await page.$$('button:has-text("编辑"), [class*="edit"]');
  const deleteBtns = await page.$$('button:has-text("删除"), [class*="delete"]');
  console.log('Edit buttons:', editBtns.length);
  console.log('Delete buttons:', deleteBtns.length);

  // Final screenshot
  await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/04-players.png', fullPage: false });
  console.log('\nFinal screenshot saved');

  await browser.close();
  console.log('\n=== TEST COMPLETE ===');
})();
