const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

  const errors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') {
      const text = msg.text();
      errors.push(text);
      console.log('[BROWSER ERROR]', text);
    }
  });
  page.on('pageerror', err => {
    errors.push(err.message);
    console.log('[PAGE ERROR]', err.message);
  });

  // Step 1: Navigate to login page first
  console.log('=== Step 1: Navigate to Login Page ===');
  await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(3000);
  console.log('URL:', page.url());

  // Step 2: Login via API
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
    const usernameInput = await page.$('input[placeholder="请输入用户名"]');
    if (usernameInput) {
      await usernameInput.click();
      await usernameInput.type('admin', { delay: 80 });
    }
    const passwordInput = await page.$('input[placeholder="请输入密码"]');
    if (passwordInput) {
      await passwordInput.click();
      await passwordInput.type('admin123', { delay: 80 });
    }
    await page.waitForTimeout(1000);
    await page.click('button:has-text("登录")');
    await page.waitForTimeout(5000);
    console.log('URL after form login:', page.url());
  } else {
    const token = loginResult.data.token;
    const profile = { username: loginResult.data.username, role: loginResult.data.role };
    console.log('Token received, setting localStorage...');
    await page.evaluate(({ token, profile }) => {
      localStorage.setItem('nba_token', token);
      localStorage.setItem('nba_profile', JSON.stringify(profile));
    }, { token, profile });
    console.log('Auth data set in localStorage');
  }

  // Step 3: Navigate to news page
  console.log('\n=== Step 3: Navigate to News Page ===');
  await page.goto('http://127.0.0.1:5173/news', { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(5000);

  const url = page.url();
  console.log('News URL:', url);

  if (url.includes('login')) {
    console.log('ERROR: Still redirected to login. Authentication failed.');
    await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/05-news.png' });
    await browser.close();
    return;
  }

  // Step 4: Check page content
  console.log('\n=== Step 4: Analyze Page Content ===');
  const bodyText = await page.textContent('body');
  console.log('Body text length:', bodyText.length);

  // Check for today's games section
  const hasTodaySection = bodyText.includes('今日赛事') || bodyText.includes('今日暂无赛事');
  console.log('Has today section:', hasTodaySection);

  // Check for news list section
  const hasNewsList = bodyText.includes('全部资讯') || bodyText.includes('赛事资讯');
  console.log('Has news list section:', hasNewsList);

  // Check for search input
  const searchInput = await page.$('input[placeholder*="搜索"]');
  console.log('Has search input:', !!searchInput);

  // Step 5: Count news items in table
  console.log('\n=== Step 5: Check News Table ===');
  let tableRows = [];
  try {
    tableRows = await page.$$eval('table tbody tr', rows => {
      return rows.slice(0, 10).map(row => {
        const cells = row.querySelectorAll('td');
        return Array.from(cells).map(cell => cell.textContent.trim().substring(0, 80)).join(' | ');
      });
    });
    console.log('Table rows found:', tableRows.length);
    tableRows.forEach((row, i) => console.log(`  ${i + 1}. ${row}`));
  } catch (e) {
    console.log('Table rows not found or empty:', e.message);
  }

  // Count today's game cards
  let todayCards = [];
  try {
    todayCards = await page.$$eval('.game-card', cards => {
      return cards.map(card => {
        const title = card.querySelector('.game-title')?.textContent?.trim() || '';
        const teams = card.querySelector('.game-teams')?.textContent?.trim() || '';
        return { title: title.substring(0, 60), teams: teams.substring(0, 60) };
      });
    });
    console.log('Today game cards:', todayCards.length);
    todayCards.forEach((card, i) => console.log(`  ${i + 1}. ${card.teams} - ${card.title}`));
  } catch (e) {
    console.log('Today cards not found:', e.message);
  }

  // Check total news count
  const totalMatch = bodyText.match(/共\s*(\d+)\s*条/);
  console.log('Total news count:', totalMatch ? totalMatch[1] : 'unknown');

  // Step 6: Test clicking on a news article
  console.log('\n=== Step 6: Test Clicking on News Article ===');
  let clickedArticle = false;

  // Try clicking on a table row first
  try {
    const firstRow = await page.$('table tbody tr:first-child .cell-title');
    if (firstRow) {
      const titleText = await firstRow.textContent();
      console.log('Clicking on first table article:', titleText?.substring(0, 60));
      await firstRow.click();
      await page.waitForTimeout(2000);
      clickedArticle = true;

      // Check if dialog opened
      const dialogVisible = await page.$('.el-dialog');
      if (dialogVisible) {
        const dialogTitle = await page.$eval('.el-dialog .el-dialog__title', el => el.textContent).catch(() => 'unknown');
        console.log('Dialog opened with title:', dialogTitle?.substring(0, 60));

        // Check dialog content
        const detailContent = await page.$('.detail-content');
        if (detailContent) {
          const contentText = await detailContent.textContent();
          console.log('Detail content length:', contentText?.length || 0);
        }

        // Check detail stats
        const detailStats = await page.$('.detail-stats');
        if (detailStats) {
          const statsText = await detailStats.textContent();
          console.log('Detail stats:', statsText?.trim());
        }

        // Take screenshot with dialog open
        await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/05-news-detail.png' });
        console.log('Detail dialog screenshot saved');

        // Close dialog
        const closeBtn = await page.$('.el-dialog__headerbtn');
        if (closeBtn) await closeBtn.click();
        await page.waitForTimeout(1000);
      }
    }
  } catch (e) {
    console.log('Error clicking table article:', e.message);
  }

  // If no table row found, try clicking a game card
  if (!clickedArticle) {
    try {
      const gameCard = await page.$('.game-card');
      if (gameCard) {
        console.log('Clicking on first game card...');
        await gameCard.click();
        await page.waitForTimeout(2000);

        const dialogVisible = await page.$('.el-dialog');
        if (dialogVisible) {
          const dialogTitle = await page.$eval('.el-dialog .el-dialog__title', el => el.textContent).catch(() => 'unknown');
          console.log('Game card dialog opened with title:', dialogTitle?.substring(0, 60));
          await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/05-news-detail.png' });
          const closeBtn = await page.$('.el-dialog__headerbtn');
          if (closeBtn) await closeBtn.click();
          await page.waitForTimeout(1000);
          clickedArticle = true;
        }
      }
    } catch (e) {
      console.log('Error clicking game card:', e.message);
    }
  }

  console.log('Article click test:', clickedArticle ? 'PASSED' : 'NO ARTICLES TO CLICK');

  // Step 7: Final screenshots
  console.log('\n=== Step 7: Take Screenshots ===');
  await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/05-news.png', fullPage: false });
  console.log('Viewport screenshot saved');

  await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/05-news-full.png', fullPage: true });
  console.log('Full page screenshot saved');

  // Step 8: Test search
  console.log('\n=== Step 8: Test Search ===');
  if (searchInput) {
    await searchInput.click();
    await searchInput.type('湖人', { delay: 80 });
    await page.waitForTimeout(2000);
    const afterSearchText = await page.textContent('body');
    const searchTotal = afterSearchText.match(/共\s*(\d+)\s*条/);
    console.log('After search "湖人" - total:', searchTotal ? searchTotal[1] : 'unknown');
    console.log('Search results contain 湖人:', afterSearchText.includes('湖人'));

    await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/05-news-search.png' });
    console.log('Search screenshot saved');

    // Clear search
    await searchInput.fill('');
    await page.waitForTimeout(1000);
  }

  // Summary
  console.log('\n========================================');
  console.log('=== TEST SUMMARY ===');
  console.log('========================================');
  console.log('Page loads OK:', url.includes('news') ? 'YES' : 'NO (redirected to ' + url + ')');
  console.log('Current URL:', url);
  console.log('Today section:', hasTodaySection ? 'Present' : 'Missing');
  console.log('News list section:', hasNewsList ? 'Present' : 'Missing');
  console.log('Today game cards:', todayCards.length);
  console.log('Table rows:', tableRows.length);
  console.log('Total news count:', totalMatch ? totalMatch[1] : 'unknown');
  console.log('Search input:', !!searchInput ? 'Present' : 'Missing');
  console.log('Article click test:', clickedArticle ? 'PASSED' : 'FAILED/NO DATA');
  console.log('Browser errors:', errors.length);
  if (errors.length > 0) {
    errors.forEach((e, i) => console.log(`  Error ${i + 1}: ${e.substring(0, 120)}`));
  }

  await browser.close();
  console.log('\n=== TEST COMPLETE ===');
})();
