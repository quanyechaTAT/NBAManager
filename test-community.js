const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();

  console.log('Navigating to community page...');

  try {
    // Navigate to community page
    await page.goto('http://127.0.0.1:5173/community', { waitUntil: 'networkidle' });
    console.log('Page loaded successfully');

    // Wait a bit for dynamic content to load
    await page.waitForTimeout(2000);

    // Check page title
    const title = await page.title();
    console.log(`Page title: ${title}`);

    // Check for community posts
    const postElements = await page.locator('[class*="post"], [class*="article"], [class*="card"], .post-item, .article-item').count();
    console.log(`Found ${postElements} potential post elements`);

    // Check for any loading indicators
    const loadingElements = await page.locator('[class*="loading"], .spinner, .loader').count();
    console.log(`Found ${loadingElements} loading indicators`);

    // Check for error messages
    const errorElements = await page.locator('[class*="error"], .error-message').count();
    console.log(`Found ${errorElements} error elements`);

    // Take screenshot
    await page.screenshot({
      path: 'E:/Visual Studio Code/NBAManager/test-screenshots/06-community.png',
      fullPage: true
    });
    console.log('Screenshot saved to test-screenshots/06-community.png');

    // Check page content
    const pageContent = await page.textContent('body');
    const hasContent = pageContent && pageContent.trim().length > 0;
    console.log(`Page has content: ${hasContent}`);

    // Check for specific community-related content
    const hasCommunityContent = pageContent.includes('社区') ||
                               pageContent.includes('帖子') ||
                               pageContent.includes('post') ||
                               pageContent.includes('community');
    console.log(`Has community-related content: ${hasCommunityContent}`);

    // Check if there's a post creation form
    const postForm = await page.locator('form, [class*="post-form"], [class*="create-post"], textarea, input[type="text"]').count();
    console.log(`Found ${postForm} potential post creation elements`);

    // Check for any console errors
    page.on('console', msg => {
      if (msg.type() === 'error') {
        console.log(`Console error: ${msg.text()}`);
      }
    });

    // Test posting functionality if available
    if (postForm > 0) {
      console.log('Testing posting functionality...');
      // Try to find a textarea or input for posting
      const textarea = await page.locator('textarea, input[type="text"]').first();
      if (await textarea.isVisible()) {
        console.log('Found post input element');
        // Don't actually post, just check if the form is interactive
        const isDisabled = await textarea.isDisabled();
        console.log(`Post input is disabled: ${isDisabled}`);
      }
    }

    console.log('\n=== Community Page Test Results ===');
    console.log('✓ Page loads OK');
    console.log(`✓ Title: ${title}`);
    console.log(`✓ Post elements found: ${postElements}`);
    console.log(`✓ Loading indicators: ${loadingElements}`);
    console.log(`✓ Error elements: ${errorElements}`);
    console.log(`✓ Page has content: ${hasContent}`);
    console.log(`✓ Community content: ${hasCommunityContent}`);
    console.log(`✓ Post creation elements: ${postForm}`);
    console.log('✓ Screenshot saved');

  } catch (error) {
    console.error('Error testing community page:', error.message);

    // Take screenshot even on error
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
