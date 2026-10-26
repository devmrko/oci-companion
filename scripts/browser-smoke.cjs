const { chromium } = require('../.tools/browser/node_modules/playwright');
const assert = require('node:assert/strict');
(async () => {
 const browser = await chromium.launch({headless:true});
 try {
 const page = await browser.newPage({viewport:{width:1440,height:1050}});
 const errors=[];
 page.on('pageerror', e=>errors.push(e.message));
 const base=process.env.COMPANION_TEST_URL || 'http://127.0.0.1:8091';
 await page.goto(base+'/login');
 const profile=process.env.COMPANION_TEST_PROFILE;
 if (!profile) {
   assert.equal(await page.locator('h2').innerText(),'OCI 연결');
   console.log(JSON.stringify({result:'passed',checks:['real service login page'],cloudQueries:false}));
   return;
 }
 await page.locator('[name=profile]').selectOption(profile);
 await page.getByRole('button',{name:'연결',exact:true}).click();
 await page.waitForURL(base+'/',{timeout:60000});
 assert.equal(await page.locator('.banner.demo').count(),0);
 await page.locator('.scope-bar').waitFor({timeout:60000});
 await page.getByRole('button',{name:'새로 고침',exact:true}).click();
 await page.getByText('갱신 완료',{exact:true}).waitFor({timeout:90000});
 assert.equal(await page.locator('.side-item[aria-current=page] .nav-text').innerText(),'리소스');
 assert.equal(await page.locator('.unavailable').count(),8);
 await page.screenshot({path:'.tools/service-catalog.png',fullPage:true});
 await page.getByRole('button',{name:'메뉴 접기',exact:true}).click();
 assert.equal(await page.locator('#sidebar-toggle').getAttribute('aria-expanded'),'false');
 await page.getByRole('button',{name:'메뉴 펼치기',exact:true}).click();
 await page.getByRole('link',{name:'소스',exact:true}).click();
 assert.equal(await page.locator('h1').innerText(),'소스');
 await page.getByRole('link',{name:'OCI 사용 안내',exact:true}).click();
 await page.setViewportSize({width:390,height:844});
 await page.getByRole('button',{name:'메뉴 펼치기',exact:true}).click();
 await page.getByRole('link',{name:'리소스',exact:true}).click();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false,'mobile overflow');
 await page.screenshot({path:'.tools/service-mobile.png',fullPage:true});
 await page.getByRole('button',{name:'연결 해제',exact:true}).click();
 assert.match(page.url(),/\/login$/);
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({result:'passed',checks:['real profile authentication','OCI DevOps refresh','grouped sidebar','collapse','source menu','help','mobile navigation','logout'],cloudMutations:false}));
 } finally { await browser.close(); }
})().catch(e=>{console.error(e.message);process.exit(1)});
