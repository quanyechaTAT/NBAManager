const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  console.log('=== Community Page Test with Authentication Check ===\n');

  try {
    // Navigate to community page
    console.log('1. Navigating to community page...');
    await page.goto('http://127.0.0.1:5173/community', { waitUntil: 'networkidle' });

    // Wait for page to load
    await page.waitForTimeout(2000);

    // Check current URL
    const currentUrl = page.url();
    console.log(`Current URL: ${currentUrl}`);

    // Check if we were redirected to login
    if (currentUrl.includes('/login')) {
      console.log('Result: Redirected to login page (authentication required)');
      console.log('The community page requires user authentication.');

      // Take screenshot of login page
      await page.screenshot({
        path: 'E:/Visual Studio Code/NBAManager/test-screenshots/06-community.png',
        fullPage: true
      });
      console.log('Screenshot saved to test-screenshots/06-community.png');

      // Check login page elements
      const loginForm = await page.locator('form, [class*="login"], input[type="password"]').count();
      console.log(`Login form elements found: ${loginForm}`);

      // Check for test credentials in the page
      const pageContent = await page.textContent('body');
      console.log('\n=== Page Content Analysis ===');
      console.log(`Page title: ${await page.title()}`);

      // Check if there are any demo accounts or test credentials
      const hasDemoAccount = pageContent.includes('demo') ||
                            pageContent.includes('test') ||
                            pageContent.includes('admin');
      console.log(`Has demo/test account info: ${hasDemoAccount}`);

      console.log('\n=== Summary ===');
      console.log('Status: Community page requires authentication');
      console.log('To test community posts, you need to:');
      console.log('1. Create an account or use existing credentials');
      console.log('2. Login first, then navigate to /community');
      console.log('3. Or add test data to the database');

    } else {
      console.log('Result: Successfully accessed community page');

      // Take screenshot
      await page.screenshot({
        path: 'E:/Visual Studio Code/NBAManager/test-screenshots/06-community.png',
        fullPage: true
      });
      console.log('Screenshot saved to test-screenshots/06-community.png');

      // Check for community content
      const content = await page.textContent('body');
      const hasCommunityHeader = content.includes('社区') || content.includes('Community');
      const hasPostList = content.includes('帖子') || content.includes('Post');
      const hasTabs = content.includes('全部') || content.includes('讨论');

      console.log('\n=== Community Page Content ===');
      console.log(`Has community header: ${hasCommunityHeader}`);
      console.log(`Has post list: ${hasPostList}`);
      console.log(`Has category tabs: ${hasTabs}`);

      // Check for post elements
      const postElements = await page.locator('.post-item, [class*="post"]').count();
      console.log(`Post elements found: ${postElements}`);

      // Check for create post button
      const createButton = await page.locator('button:has-text("发帖"), [class*="create"]').count();
      console.log(`Create post button found: ${createButton}`);

      // Check for hot posts sidebar
      const hotSidebar = await page.locator('.hot-card, [class*="hot"]').count();
      console.log(`Hot posts sidebar found: ${hotSidebar}`);
    }

  } catch (error) {
    console.error('Error testing community page:', error.message);

    // Take error screenshot
    try {
      await page.screenshot({
        path: 'E:/Visual Studio Code/NBAManager/test-screenshots/06-community-error.png',
        fullPage: true
      });
      console.log('Error screenshot saved');
    } catch (screenshotError) {
      console.error('Failed to save error screenshot:', screenshotError.message);
    }
  } finally {
    await browser.close();
  }
})();
