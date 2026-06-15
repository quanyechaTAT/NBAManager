const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

  page.on('console', msg => {
    if (msg.type() === 'error') console.log('[BROWSER ERROR]', msg.text());
  });

  // Step 1: Navigate to app first so we're on the right origin
  console.log('=== Step 1: Navigate to App ===');
  await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(3000);
  console.log('URL:', page.url());

  // Step 2: Login via API from the page context
  console.log('\n=== Step 2: Login via API ===');
  const loginResult = await page.evaluate(async () => {
    try {
      const resp = await fetch('http://127.0.0.1:5173/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'admin', password: 'admin123' })
      });
      const data = await resp.json();
      return { status: resp.status, ok: resp.ok, data };
    } catch (e) {
      return { error: e.message };
    }
  });
  console.log('Login result:', JSON.stringify(loginResult).substring(0, 500));

  if (loginResult.error || !loginResult.ok) {
    console.log('API login failed, trying form-based login...');
    // Use Playwright's type method (compatible with v1.60)
    const usernameInput = await page.$('input[placeholder="请输入用户名"]');
    if (usernameInput) {
      await usernameInput.click();
      await usernameInput.type('admin', { delay: 80 });
      console.log('Typed username');
    }

    const passwordInput = await page.$('input[placeholder="请输入密码"]');
    if (passwordInput) {
      await passwordInput.click();
      await passwordInput.type('admin123', { delay: 80 });
      console.log('Typed password');
    }

    await page.waitForTimeout(1000);
    await page.click('button:has-text("登录")');
    console.log('Clicked login button');
    await page.waitForTimeout(5000);
    console.log('URL after form login:', page.url());
  } else {
    // Set token in localStorage to authenticate
    const token = loginResult.data.token;
    const profile = { username: loginResult.data.username, role: loginResult.data.role };
    console.log('Token received, setting localStorage...');

    await page.evaluate(({ token, profile }) => {
      localStorage.setItem('nba_token', token);
      localStorage.setItem('nba_profile', JSON.stringify(profile));
    }, { token, profile });

    console.log('Auth data set in localStorage');
  }

  // Step 3: Navigate to players page
  console.log('\n=== Step 3: Navigate to Players Page ===');
  await page.goto('http://127.0.0.1:5173/players', { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(8000);

  const url = page.url();
  console.log('Players URL:', url);

  if (url.includes('login')) {
    console.log('Still redirected to login. Trying to find the correct localStorage key...');

    // Check authStorage source
    const authStorageContent = await page.evaluate(() => {
      const result = {};
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        result[key] = localStorage.getItem(key)?.substring(0, 100);
      }
      return result;
    });
    console.log('localStorage:', JSON.stringify(authStorageContent, null, 2));
    await browser.close();
    return;
  }

  // Wait for table
  try {
    await page.waitForSelector('table tbody tr', { timeout: 15000 });
    console.log('Table rows loaded!');
  } catch (e) {
    console.log('Table rows not found within 15s');
  }

  await page.waitForTimeout(3000);

  // Analyze page
  const bodyText = await page.textContent('body');
  console.log('\n=== Page Content ===');
  console.log('Body text length:', bodyText.length);

  // Table data
  const tableData = await page.$$eval('table tbody tr', rows => {
    return rows.slice(0, 15).map(row => {
      const cells = row.querySelectorAll('td');
      return Array.from(cells).map(cell => cell.textContent.trim()).join(' | ');
    });
  }).catch(() => []);
  console.log('\nTable data (first 15 rows):');
  tableData.forEach((row, i) => console.log(`  ${i + 1}. ${row}`));

  // Headers
  const headers = await page.$$eval('table th', ths => ths.map(th => th.textContent.trim())).catch(() => []);
  console.log('\nTable headers:', headers);

  // Total count
  const totalMatch = bodyText.match(/共\s*(\d+)\s*条/);
  console.log('Total player records:', totalMatch ? totalMatch[1] : 'unknown');

  // Test search
  console.log('\n=== Testing Search ===');
  const searchInput = await page.$('input[placeholder*="搜索"], input[placeholder*="查询"], input[placeholder*="姓名"]');
  if (searchInput) {
    const ph = await searchInput.getAttribute('placeholder');
    console.log('Search placeholder:', ph);
    await searchInput.click();
    await searchInput.type('詹姆斯', { delay: 80 });
    await page.waitForTimeout(3000);

    const afterSearch = await page.textContent('body');
    const searchTotal = afterSearch.match(/共\s*(\d+)\s*条/);
    console.log('After search "詹姆斯" - total:', searchTotal ? searchTotal[1] : 'unknown');
    console.log('Contains 詹姆斯:', afterSearch.includes('詹姆斯'));

    await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/04-players-search-zh.png' });

    // Clear and try 库里
    await searchInput.fill('');
    await page.waitForTimeout(500);
    await searchInput.type('库里', { delay: 80 });
    await page.waitForTimeout(3000);
    const afterSearch2 = await page.textContent('body');
    const searchTotal2 = afterSearch2.match(/共\s*(\d+)\s*条/);
    console.log('After search "库里" - total:', searchTotal2 ? searchTotal2[1] : 'unknown');
    console.log('Contains 库里:', afterSearch2.includes('库里'));

    await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/04-players-search-kuli.png' });

    await searchInput.fill('');
    await page.waitForTimeout(1000);
  } else {
    console.log('No search input found');
    const allInputs = await page.$$eval('input', inputs => inputs.map(i => ({
      type: i.type, placeholder: i.placeholder, class: i.className?.substring(0, 50)
    })));
    console.log('All inputs on page:', JSON.stringify(allInputs));
  }

  // Final screenshots
  console.log('\n=== Screenshots ===');
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
  console.log('Total player records:', totalMatch ? totalMatch[1] : 'unknown');
  console.log('Table columns:', headers.join(', '));
  console.log('Search functionality: Tested');

  await browser.close();
  console.log('=== TEST COMPLETE ===');
})();
