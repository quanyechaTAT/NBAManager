const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

  // Collect console messages
  const consoleMsgs = [];
  page.on('console', msg => consoleMsgs.push(`[${msg.type()}] ${msg.text()}`));
  page.on('requestfailed', req => console.log('FAILED REQUEST:', req.url(), req.failure()?.errorText));

  console.log('=== Step 1: Navigate to App ===');
  await page.goto('http://127.0.0.1:5173/', { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(5000);

  // Check current state
  let url = page.url();
  console.log('Initial URL:', url);
  let bodyText = await page.textContent('body');
  console.log('Initial body (first 500):', bodyText.substring(0, 500));

  // Check if already logged in (URL should NOT contain 'login')
  if (!url.includes('login')) {
    console.log('Already logged in, skipping login step');
  } else {
    console.log('\n=== Step 2: Login ===');
    // Try to find inputs
    const inputs = await page.$$('input');
    console.log('Input elements found:', inputs.length);
    for (let i = 0; i < inputs.length; i++) {
      const type = await inputs[i].getAttribute('type');
      const placeholder = await inputs[i].getAttribute('placeholder');
      console.log(`  Input ${i}: type=${type}, placeholder=${placeholder}`);
    }

    // Try different selectors for username
    const userInput = await page.$('input[type="text"], input:not([type="password"]):not([type="hidden"])');
    if (userInput) {
      await userInput.click();
      await userInput.fill('admin');
      console.log('Filled username');
    }

    const passInput = await page.$('input[type="password"]');
    if (passInput) {
      await passInput.click();
      await passInput.fill('admin123');
      console.log('Filled password');
    }

    // Find and click login button
    const buttons = await page.$$('button');
    console.log('Buttons found:', buttons.length);
    for (let i = 0; i < buttons.length; i++) {
      const text = await buttons[i].textContent();
      console.log(`  Button ${i}: "${text.trim()}"`);
    }

    const loginBtn = await page.$('button:has-text("登录")');
    if (loginBtn) {
      await loginBtn.click();
      console.log('Clicked login button');
    } else {
      // Try clicking the first button
      if (buttons.length > 0) {
        await buttons[0].click();
        console.log('Clicked first button');
      }
    }

    await page.waitForTimeout(5000);
    url = page.url();
    console.log('URL after login:', url);
  }

  // Navigate to players page
  console.log('\n=== Step 3: Navigate to Players ===');
  await page.goto('http://127.0.0.1:5173/players', { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(8000); // Wait longer for data to load

  url = page.url();
  console.log('Players URL:', url);

  // Check for login redirect
  if (url.includes('login') || url.includes('auth') || !url.includes('players')) {
    console.log('REDIRECTED AWAY FROM PLAYERS - authentication may have failed');
    bodyText = await page.textContent('body');
    console.log('Current page content:', bodyText.substring(0, 500));
    await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/04-players-error.png' });
    await browser.close();
    return;
  }

  // Wait for table to appear
  try {
    await page.waitForSelector('table', { timeout: 15000 });
    console.log('Table element appeared');
  } catch (e) {
    console.log('No table found within 15s');
  }

  await page.waitForTimeout(3000);

  bodyText = await page.textContent('body');
  console.log('\n=== Page Content Analysis ===');
  console.log('Body length:', bodyText.length);
  console.log('Full body text:', bodyText.substring(0, 3000));

  // Check for common NBA player names (Chinese)
  const chineseNames = ['詹姆斯', '库里', '杜兰特', '字母哥', '约基奇', '东契奇', '塔图姆', '恩比德', '布克', '莫兰特', '保罗', '哈登', '威少', '汤普森'];
  const foundChineseNames = chineseNames.filter(n => bodyText.includes(n));
  console.log('\nFound Chinese NBA names:', foundChineseNames);

  // Check for team names
  const teams = ['湖人', '勇士', '凯尔特人', '掘金', '雷霆', '76人', '雄鹿', '篮网', '太阳', '热火'];
  const foundTeams = teams.filter(t => bodyText.includes(t));
  console.log('Found team names:', foundTeams);

  // Check for stats
  const hasStats = /\d+\.\d+/.test(bodyText);
  console.log('Has decimal stats:', hasStats);

  // Check for pagination
  const hasPagination = /共\s*\d+\s*条/.test(bodyText);
  console.log('Has pagination:', hasPagination);
  const totalMatch = bodyText.match(/共\s*(\d+)\s*条/);
  if (totalMatch) console.log('Total records:', totalMatch[1]);

  // Search functionality
  console.log('\n=== Search Test ===');
  const searchInput = await page.$('input');
  if (searchInput) {
    const ph = await searchInput.getAttribute('placeholder');
    console.log('Search placeholder:', ph);

    await searchInput.click();
    await searchInput.fill('詹姆斯');
    await page.waitForTimeout(3000);

    const afterSearchText = await page.textContent('body');
    const searchTotal = afterSearchText.match(/共\s*(\d+)\s*条/);
    console.log('After search "詹姆斯" - total:', searchTotal ? searchTotal[1] : 'unknown');
    console.log('Search results contain 詹姆斯:', afterSearchText.includes('詹姆斯'));

    await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/04-players-search-zh.png' });

    // Clear and search another name
    await searchInput.fill('库里');
    await page.waitForTimeout(3000);
    const afterSearch2 = await page.textContent('body');
    const searchTotal2 = afterSearch2.match(/共\s*(\d+)\s*条/);
    console.log('After search "库里" - total:', searchTotal2 ? searchTotal2[1] : 'unknown');
    console.log('Search results contain 库里:', afterSearch2.includes('库里'));

    await searchInput.fill('');
    await page.waitForTimeout(1000);
  }

  // Table analysis
  console.log('\n=== Table Analysis ===');
  const tables = await page.$$('table');
  console.log('Tables found:', tables.length);
  if (tables.length > 0) {
    const headers = await page.$$eval('table th', ths => ths.map(th => th.textContent.trim()));
    console.log('Headers:', headers);

    const rowCount = await page.$$eval('table tbody tr', rows => rows.length);
    console.log('Rows in first table:', rowCount);

    // Get first row data
    const firstRow = await page.$eval('table tbody tr:first-child', row => row.textContent.trim()).catch(() => 'N/A');
    console.log('First row:', firstRow);
  }

  // Screenshots
  console.log('\n=== Taking Screenshots ===');
  await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/04-players.png', fullPage: false });
  console.log('Main screenshot saved');
  await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/04-players-full.png', fullPage: true });
  console.log('Full page screenshot saved');

  // Summary
  console.log('\n========================================');
  console.log('=== TEST SUMMARY ===');
  console.log('========================================');
  console.log('Page loads OK: YES');
  console.log('Current URL:', url);
  console.log('Player data visible:', foundChineseNames.length > 0 || foundTeams.length > 0 ? 'YES' : 'NO');
  console.log('Total player records:', totalMatch ? totalMatch[1] : 'unknown');
  console.log('Teams found:', foundTeams.length > 0 ? foundTeams.join(', ') : 'None');
  console.log('NBA player names found:', foundChineseNames.length > 0 ? foundChineseNames.join(', ') : 'None (players are alphabetically sorted)');
  console.log('Search functionality: Tested with 詹姆斯 and 库里');
  console.log('Console errors:', consoleMsgs.filter(m => m.startsWith('[error]')).length);

  await browser.close();
})();
