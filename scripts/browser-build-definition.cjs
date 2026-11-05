const {chromium}=require('../.tools/browser/node_modules/playwright'),fs=require('fs');
(async()=>{
if(!process.env.COMPANION_TEST_PROFILE || !process.env.COMPANION_TEST_PIPELINE)throw Error('Set COMPANION_TEST_PROFILE and COMPANION_TEST_PIPELINE for the db-companion example pipeline');
const browser=await chromium.launch({headless:true});try{
const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption(process.env.COMPANION_TEST_PROFILE);await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
const pipeline=process.env.COMPANION_TEST_PIPELINE;
await page.goto('http://127.0.0.1:8091/builds?tab=execute&pipeline='+encodeURIComponent(pipeline));await page.waitForLoadState('load');
await page.locator('.pipeline-definition').waitFor();
const text=await page.locator('.pipeline-definition').innerText();
for(const wanted of ['Java21-Maven-Node','OL8_X86_64_STANDARD_10','build_spec.yaml','db-companion-jar','https://github.com/devmrko/db-companion.git'])if(!text.includes(wanted))throw Error('Missing actual definition: '+wanted);
await page.locator('#build-source').selectOption({index:1});
await page.locator('#source-status').filter({hasText:'최근 커밋'}).waitFor();
if(await page.locator('#build-branch').inputValue()!=='main')throw Error('Missing main branch');
if(await page.locator('#build-commit option').count()<2)throw Error('Missing commits');
await page.locator('#build-commit').selectOption({index:1});
await page.getByRole('button',{name:'실행 설정 확인',exact:true}).click();
await page.locator('#build-review[open]').waitFor();
if(!(await page.locator('#build-review').innerText()).includes('main'))throw Error('Review missing branch');
await page.getByRole('button',{name:'수정',exact:true}).click();
await page.screenshot({path:'.tools/build-definition-desktop.png',fullPage:true});
await page.setViewportSize({width:390,height:844});
if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Mobile overflow');
await page.screenshot({path:'.tools/build-definition-mobile.png',fullPage:true});
if(process.env.COMPANION_TEST_RUN==='1'){
await page.locator('[name=runName]').fill('db-companion-selected-source-'+Date.now());
await page.getByRole('button',{name:'실행 설정 확인',exact:true}).click();
await page.locator('#build-review').getByRole('button',{name:'빌드 실행',exact:true}).click();
await page.waitForLoadState('load');await page.getByText('빌드 실행 요청 완료',{exact:true}).waitFor();
fs.writeFileSync('.tools/build-definition-run.json',JSON.stringify({url:page.url(),run:new URL(page.url()).searchParams.get('run')}));
}
if(errors.length)throw Error(errors.join('\n'));
console.log('PASS real OCI stage/spec/output/script sources, branch/commit selectors, review, mobile; build='+(process.env.COMPANION_TEST_RUN||'0')+'');
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exit(1)});
