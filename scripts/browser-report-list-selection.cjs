// Reads an existing OCI report. Holds a real response to test selection changes in flight.
const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT required');
 const target=JSON.parse(fs.readFileSync(process.env.COMPANION_REPORT_TARGET||'.tools/ui-native-report-failed-run.json'));
 const browser=await chromium.launch({headless:true});const context=await browser.newContext();const page=await context.newPage();page.setDefaultTimeout(90000);
 try{
  await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
  await page.goto(target.url);await page.getByText('Object Storage 보고서',{exact:true}).click();
  const form=page.locator('#object-report-form'),list=form.locator('#object-report-list'),view=form.getByRole('button',{name:'Object Storage 보고서 보기',exact:true}),status=form.locator('#object-report-status');
  if(!await view.isDisabled())throw Error('Report view enabled before selection');
  await form.locator('[data-storage-refresh]').click();await form.locator('[data-storage-status]').filter({hasText:'Namespace:'}).waitFor();
  await form.locator('[name=reportBucket]').selectOption('companion-build-reports-validation');
  let release,received;const held=new Promise(r=>release=r),ready=new Promise(r=>received=r);
  await page.route('**/builds/report-storage/objects?*',async route=>{const response=await route.fetch();received();await held;await route.fulfill({response});},{times:1});
  await list.click();await ready;await form.locator('[name=reportPrefix]').fill('selection-validation-missing');release();await page.locator('#object-report-list:enabled').waitFor();
  if(await form.locator('[name=object] option').count()!==1||!await view.isDisabled())throw Error('Stale response applied');
  await list.click();await status.filter({hasText:'이 실행의 보고서 없음'}).waitFor();if(!await view.isDisabled())throw Error('Empty report view enabled');
  await form.locator('[name=reportPrefix]').fill('build-reports');await context.setOffline(true);await list.click();await status.filter({hasText:'조회 실패'}).waitFor();if(!await view.isDisabled())throw Error('Failed report view enabled');await context.setOffline(false);
  await list.click();await status.filter({hasText:/보고서 \d+개/}).waitFor();await view.click();await page.locator('#build-report-status').filter({hasText:'테스트 1 · 실패 0 · 오류 0'}).waitFor();
  // Hold the actual report body while the user changes the target.
  let releaseReport,receivedReport;const heldReport=new Promise(r=>releaseReport=r),readyReport=new Promise(r=>receivedReport=r);
  await page.route('**/builds/report-storage/report?*',async route=>{const response=await route.fetch();receivedReport();await heldReport;await route.fulfill({response});},{times:1});
  await view.click();await readyReport;await form.locator('[name=reportPrefix]').fill('changed-during-report');releaseReport();
  await page.locator('#object-report-form[data-report-busy=false]').waitFor();
  await page.locator('#build-report-status[data-state=CHANGED]').waitFor();
  if(await page.locator('#pmd-reports').textContent()||await page.locator('#report-files').textContent())throw Error('Stale report body displayed');
  if(!await view.isDisabled())throw Error('View enabled after target changed');
  await form.locator('[name=reportPrefix]').fill('build-reports');await list.click();await status.filter({hasText:/보고서 \d+개/}).waitFor();
  await context.setOffline(true);await view.click();await page.locator('#build-report-status[data-state=ERROR]').waitFor();await context.setOffline(false);
  await view.click();await page.locator('#build-report-status[data-state=READY]').waitFor();
  await page.locator('#build-report-status').filter({hasText:'테스트 1 · 실패 0 · 오류 0'}).waitFor();
  for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);await form.scrollIntoViewIfNeeded();await page.screenshot({path:'.tools/report-selection-'+width+'.png'});}
  console.log('PASS real OCI report: stale response discarded, empty/failure disabled, retry, stale report body discarded, report failure/recovery, responsive layout');
 }finally{await context.setOffline(false);await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
