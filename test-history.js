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

  // Step 3: Navigate to history page
  console.log('\n=== Step 3: Navigate to History Page ===');
  await page.goto('http://127.0.0.1:5173/history', { waitUntil: 'load', timeout: 30000 });
  await page.waitForTimeout(5000);

  const url = page.url();
  console.log('History URL:', url);

  if (url.includes('login')) {
    console.log('ERROR: Still redirected to login. Authentication failed.');
    await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/09-history.png' });
    await browser.close();
    return;
  }

  // Step 4: Check page content
  console.log('\n=== Step 4: Analyze Page Content ===');
  const bodyText = await page.textContent('body');
  console.log('Body text length:', bodyText.length);

  // Check for key sections
  const hasTitle = bodyText.includes('历史数据');
  console.log('Has title "历史数据":', hasTitle);

  const hasSeasonSelector = await page.$('.el-select') !== null;
  console.log('Has season selector:', hasSeasonSelector);

  const hasLeaderCards = bodyText.includes('得分') || bodyText.includes('篮板') || bodyText.includes('助攻');
  console.log('Has leader categories (得分/篮板/助攻):', hasLeaderCards);

  const hasChartSection = bodyText.includes('球队战绩概览') || bodyText.includes('球队战绩');
  console.log('Has team standings chart section:', hasChartSection);

  // Step 5: Check leader cards
  console.log('\n=== Step 5: Check Leader Cards ===');
  let leaderCards = [];
  try {
    leaderCards = await page.$$eval('.leader-card', cards => {
      return cards.map(card => {
        const title = card.querySelector('.leader-title')?.textContent?.trim() || '';
        const rows = card.querySelectorAll('.leader-row');
        const playerCount = rows.length;
        const firstPlayer = rows[0]?.querySelector('.leader-name')?.textContent?.trim() || '';
        return { title, playerCount, firstPlayer };
      });
    });
    console.log('Leader cards found:', leaderCards.length);
    leaderCards.forEach((card, i) => {
      console.log(`  ${i + 1}. ${card.title} - ${card.playerCount} players - top: ${card.firstPlayer}`);
    });
  } catch (e) {
    console.log('Leader cards parse error:', e.message);
  }

  // Step 6: Check team chart
  console.log('\n=== Step 6: Check Team Chart ===');
  const hasChart = await page.$('canvas') !== null || await page.$('.chart') !== null;
  console.log('Chart rendered (canvas or chart element):', hasChart);

  // Check for team table
  let teamTableRows = [];
  try {
    teamTableRows = await page.$$eval('.standings-table tbody tr, table tbody tr', rows => {
      return rows.slice(0, 5).map(row => {
        const cells = row.querySelectorAll('td');
        return Array.from(cells).map(cell => cell.textContent.trim().substring(0, 30)).join(' | ');
      });
    });
    console.log('Team table rows (top 5):', teamTableRows.length);
    teamTableRows.forEach((row, i) => console.log(`  ${i + 1}. ${row}`));
  } catch (e) {
    console.log('Team table not found:', e.message);
  }

  // Step 7: Check available seasons
  console.log('\n=== Step 7: Check Season Selector ===');
  try {
    const seasonText = await page.$eval('.el-select .el-input__inner', el => el.value || el.textContent);
    console.log('Current season selected:', seasonText?.substring(0, 30));
  } catch (e) {
    console.log('Could not read season selector:', e.message);
  }

  // Step 8: Test season change
  console.log('\n=== Step 8: Test Season Change ===');
  try {
    const selectBtn = await page.$('.el-select');
    if (selectBtn) {
      await selectBtn.click();
      await page.waitForTimeout(1000);
      const dropdown = await page.$('.el-select-dropdown');
      if (dropdown) {
        const options = await page.$$eval('.el-select-dropdown__item', items => {
          return items.slice(0, 5).map(item => item.textContent.trim().substring(0, 30));
        });
        console.log('Season options (first 5):', options);
      }
      // Click elsewhere to close dropdown
      await page.click('body');
      await page.waitForTimeout(500);
    }
  } catch (e) {
    console.log('Season selector test error:', e.message);
  }

  // Step 9: Final screenshot
  console.log('\n=== Step 9: Take Screenshot ===');
  await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/09-history.png', fullPage: false });
  console.log('Viewport screenshot saved');

  await page.screenshot({ path: 'E:/Visual Studio Code/NBAManager/test-screenshots/09-history-full.png', fullPage: true });
  console.log('Full page screenshot saved');

  // Summary
  console.log('\n========================================');
  console.log('=== TEST SUMMARY ===');
  console.log('========================================');
  console.log('Page loads OK:', url.includes('history') ? 'YES' : 'NO (redirected to ' + url + ')');
  console.log('Current URL:', url);
  console.log('Title present:', hasTitle ? 'YES' : 'NO');
  console.log('Season selector:', hasSeasonSelector ? 'YES' : 'NO');
  console.log('Leader cards:', leaderCards.length);
  console.log('Leader categories found:', leaderCards.map(c => c.title).join(', '));
  console.log('Chart rendered:', hasChart ? 'YES' : 'NO');
  console.log('Team table rows:', teamTableRows.length);
  console.log('Browser errors:', errors.length);
  if (errors.length > 0) {
    errors.forEach((e, i) => console.log(`  Error ${i + 1}: ${e.substring(0, 150)}`));
  }

  await browser.close();
  console.log('\n=== TEST COMPLETE ===');
})();
