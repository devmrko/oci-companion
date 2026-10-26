const {chromium}=require('../.tools/browser/node_modules/playwright'),fs=require('fs');
(async()=>{
 if(!process.env.COMPANION_TEST_PROFILE||!process.env.COMPANION_TEST_PIPELINE)throw Error('Set profile and pipeline');
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption(process.env.COMPANION_TEST_PROFILE);
  await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  const pipeline=process.env.COMPANION_TEST_PIPELINE;
  await page.goto('http://127.0.0.1:8091/builds?tab=pipelines&pipeline='+encodeURIComponent(pipeline));await page.waitForLoadState('load');
  await page.getByRole('heading',{name:'파이프라인 수정',exact:true}).waitFor();
  await page.getByRole('heading',{name:'산출물 저장',exact:true}).waitFor();
  const edit=page.locator('details').filter({has:page.locator('summary').filter({hasText:'빌드 단계 수정'})}).first();
  await edit.locator('summary').click();
  if(await edit.locator('[name=branch]').inputValue()!=='main')throw Error('Wrong saved branch');
  if(await edit.locator('[name=spec]').inputValue()!=='build_spec.yaml')throw Error('Missing build spec');
  await page.locator('#permissions > summary').click();
  if(!(await page.locator('#permissions').innerText()).includes('artifact-repositories'))throw Error('Missing registry IAM');
  for(const width of [1440,390,320]){
   await page.setViewportSize({width,height:1000});
   if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1)){console.log(await page.evaluate(()=>[...document.querySelectorAll('main *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1).slice(0,15).map(e=>({tag:e.tagName,cls:e.className,width:e.getBoundingClientRect().width,text:e.textContent.slice(0,60)}))));throw Error('Pipeline overflow '+width);}
  }
  await page.goto('http://127.0.0.1:8091/builds?tab=history&pipeline='+encodeURIComponent(pipeline)+(process.env.COMPANION_TEST_RUN_ID?'&run='+encodeURIComponent(process.env.COMPANION_TEST_RUN_ID):''));await page.waitForLoadState('load');
  await page.locator('#build-logs').waitFor();
  const wanted=process.env.COMPANION_TEST_STORAGE_FAILURE==='1'?'Unable to access deploy-artifact':'BUILD SUCCESS';
  await page.locator('#log-status').filter({hasText:/건 · 갱신/}).waitFor({timeout:15000}).catch(async e=>{console.log('LOG STATUS',await page.locator('#log-status').innerText(),'JS ERRORS',errors);throw e;});
  for(let i=0;i<5 && !(await page.locator('#log-output').innerText()).includes(wanted);i++){
   console.log('LOG PAGE',await page.locator('#log-status').innerText());
   if(await page.locator('#log-older').isDisabled())break;
   const before=await page.locator('#log-status').innerText();
   await page.locator('#log-older').click();
   await page.locator('#log-status').filter({hasNotText:before}).filter({hasText:/건 · 갱신/}).waitFor({timeout:30000});
  }
  if(!(await page.locator('#log-output').innerText()).includes(wanted))throw Error('Actual Maven log missing: '+await page.locator('#log-status').innerText());
  await page.locator('#log-search').fill(wanted);
  if(!(await page.locator('#log-output').innerText()).includes(wanted))throw Error('Log filter failed');
  await page.locator('#log-search').fill('');
  const statusBefore=await page.locator('#log-status').innerText();
  await page.locator('#log-interval').selectOption('10000');
  await page.locator('#log-status').filter({hasNotText:statusBefore}).filter({hasText:/건 · 갱신/}).waitFor({timeout:30000});
  await page.locator('#log-interval').selectOption('0');
  if(await page.locator('#log-stage option').count()<2)throw Error('No stage filter');
  if(!(await page.locator('main').innerText()).includes('Registry 전달'))throw Error('Missing separate storage state');
  if(process.env.COMPANION_TEST_STORAGE_FAILURE==='1'){
   const facts=page.locator('dl.build-facts');
   if(await facts.locator('div').filter({has:page.locator('dt').filter({hasText:'Registry 전달'})}).locator('dd').innerText()!=='FAILED')throw Error('Storage failure hidden');
   if(await facts.locator('div').filter({has:page.locator('dt').filter({hasText:'빌드·테스트'})}).locator('dd').innerText()!=='SUCCEEDED')throw Error('Build result incorrect');
  }
  await page.screenshot({path:'.tools/build-flow-history.png',fullPage:true});
  for(const width of [1440,390,320]){
   await page.setViewportSize({width,height:1000});
   if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Log overflow '+width);
  }
  if(errors.length)throw Error(errors.join('\n'));
  console.log('PASS actual OCI settings, Registry choices, IAM help, build logs/search/stages, separate build/storage state, 320/390/1440 layout');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
