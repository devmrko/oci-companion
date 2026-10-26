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
 await page.waitForLoadState('load');
 const scopes=await page.locator('.scope-bar select[name=compartment] option').count();
 await page.locator('.scope-bar select[name=mode]').selectOption('all');
 await page.locator('.scope-bar').getByRole('button',{name:'적용',exact:true}).click();
 assert.match(page.url(),/\/sources$/);
 await page.locator('.scope-results tbody tr').nth(scopes-1).waitFor({state:'attached',timeout:120000});
 if (process.env.COMPANION_TEST_ALL_SCOPES === '1') {
 console.log(JSON.stringify({stage:'refresh all compartments',compartments:scopes}));
 await page.getByRole('button',{name:'새로 고침',exact:true}).click({timeout:600000});
 await page.locator('#refresh-form button:not([disabled])').waitFor({state:'attached',timeout:600000});
 await page.locator('.scope-results tbody tr').nth(scopes-1).waitFor({state:'attached',timeout:120000});
 await page.waitForLoadState('load',{timeout:600000});
 assert.equal(await page.locator('.scope-results tbody tr').count(),scopes);
 const unqueried=await page.locator('.scope-results tbody tr').filter({hasText:'미조회'}).count();
 assert.equal(unqueried,0);
 }
 const filter=page.locator('.list-filters');
 const projects=await filter.locator('[name=project] option').count();
 if (projects>1) {
   await filter.locator('[name=project]').selectOption({index:1});
   if (await filter.locator('[name=state] option').count()>1) await filter.locator('[name=state]').selectOption({index:1});
   await filter.getByRole('button',{name:'필터 적용',exact:true}).click();
   await page.waitForLoadState('load');
   const table=page.locator('section.card').filter({has:page.locator('.list-filters')}).locator('tbody tr');
   assert.ok(await table.count()>0,'project/state filter preserves matching repositories');
 }

 await filter.locator('[name=q]').fill('companion-smoke-no-match-982');
 await filter.getByRole('button',{name:'필터 적용',exact:true}).click();
 await page.getByText('표시할 항목 없음 · 조회 범위와 필터 확인',{exact:true}).waitFor();
 await page.getByRole('link',{name:'초기화',exact:true}).click();
 await page.screenshot({path:'.tools/service-scope.png',fullPage:true});
 const failedScopes=await page.locator('.scope-results tbody tr').filter({hasText:'갱신 실패'}).count();
 console.log(JSON.stringify({stage:'scope filters passed',compartments:scopes,projectChoices:projects-1,failedScopes}));

 await page.getByRole('link',{name:'OCI 사용 안내',exact:true}).click();
 await page.setViewportSize({width:390,height:844});
 await page.getByRole('button',{name:'메뉴 펼치기',exact:true}).click();
 await page.getByRole('link',{name:'리소스',exact:true}).click();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false,'mobile overflow');
 await page.screenshot({path:'.tools/service-mobile.png',fullPage:true});
 await page.getByRole('button',{name:'연결 해제',exact:true}).click();
 assert.match(page.url(),/\/login$/);
 assert.deepEqual(errors,[]);
 console.log(JSON.stringify({result:'passed',checks:['real profile authentication','OCI DevOps refresh','grouped sidebar','collapse','source menu','all compartments','project/name filters','help','mobile navigation','logout'],cloudMutations:false}));
 } finally { await browser.close(); }
})().catch(e=>{console.error(e.message);process.exit(1)});
