const {chromium}=require('../.tools/browser/node_modules/playwright');
(async()=>{
 if(!process.env.COMPANION_TEST_PROFILE)throw Error('Set COMPANION_TEST_PROFILE');
 const browser=await chromium.launch({headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}}), errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8091/login');
  await page.locator('[name=profile]').selectOption(process.env.COMPANION_TEST_PROFILE);
  await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  await page.goto('http://127.0.0.1:8091/builds?tab=topics');await page.waitForLoadState('load');
  await page.getByRole('heading',{name:'알림 주제와 로그',exact:true}).waitFor();
  const topicRow=page.locator('tbody tr').filter({hasText:'db-companion-build'});
  await topicRow.waitFor();
  for(const width of [1440,390,320]){
    await page.setViewportSize({width,height:1000});
    if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Topic overflow '+width);
  }
  await topicRow.getByRole('link',{name:'이 주제로 프로젝트 생성'}).click();await page.waitForLoadState('load');
  if(!(await page.locator('[name=topic] option:checked').innerText()).includes('db-companion-build'))throw Error('Topic selection not retained');
  await page.goto('http://127.0.0.1:8091/builds?tab=projects');await page.waitForLoadState('load');
  if(await page.locator('.build-tab').count()!==5)throw Error('Missing tabs');
  const row=page.locator('tbody tr').filter({hasText:'db-companion'});
  await row.waitFor();
  if(await page.locator('[name=topic] option').count()<2)throw Error('No real notification topics');
  for(const width of [1440,390,320]){
    await page.setViewportSize({width,height:1000});
    if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Project overflow '+width);
  }
  await row.getByRole('link',{name:'조회·추가'}).click();await page.waitForLoadState('load');
  await page.locator('.pipeline-definition').waitFor();
  if(!(await page.locator('.pipeline-definition').innerText()).includes('Java21-Maven-Node'))throw Error('Missing real stages');
  if(await page.locator('.build-execution').count())throw Error('Execution form leaked into pipeline tab');
  for(const width of [1440,390,320]){
    await page.setViewportSize({width,height:1000});
    if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Pipeline overflow '+width);
  }
  await page.getByRole('link',{name:'이 파이프라인으로 실행'}).click();await page.waitForLoadState('load');
  await page.locator('.build-execution').waitFor();
  if(errors.length)throw Error(errors.join('\n'));
  console.log('PASS real OCI projects/topics/pipeline/stages, execution link, 320/390/1440 layout; no cloud mutations');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
