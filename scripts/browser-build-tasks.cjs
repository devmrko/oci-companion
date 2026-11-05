const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
async function until(check,timeout=240000){const end=Date.now()+timeout;while(Date.now()<end){if(await check())return;await new Promise(r=>setTimeout(r,500));}throw Error('Condition timeout');}
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT profile required');
 const browser=await chromium.launch({headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(120000);
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption('ocid1.compartment.oc1..aaaaaaaam3amapwxz6nyciqzb2v2iwg66t22g47vabo5ilnghbsskooeop3q');await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
  const app=JSON.parse(fs.readFileSync('.tools/arm-function-app.json','utf8')).data;
  const base='http://127.0.0.1:8091/builds/functions';

  await page.goto('http://127.0.0.1:8091/builds');
  if(!page.url().includes('/builds/tasks'))throw Error('Build entry missing');
  const name='db-companion 검증 · Functions';
  const existing=page.locator('.task-card').filter({hasText:name});
  if(await existing.count())await existing.getByRole('link',{name:'설정 수정',exact:true}).click();
  else await page.getByRole('link',{name:'새 빌드 작업',exact:true}).click();
  const form=page.locator('#build-task-form');
  await form.locator('[name=name]').fill(name);
  const sourceOption=form.locator('[name=sourceId] option').filter({hasText:'GITHUB · db-companion'}).first();
  await form.locator('[name=sourceId]').selectOption(await sourceOption.getAttribute('value'));
  await until(async()=>/^[a-f0-9]{40}$/.test(await form.locator('[name=commit]').inputValue()));
  const chosenUrl=await form.locator('[name=source]').inputValue();
  await form.locator('[name=commit]').fill('7a5cb34b68d8dae90f9c398667a0db976bb2f6d2');
  await page.getByLabel('커밋 SHA 도움말',{exact:true}).click();
  if(!(await page.locator('.field-help[open]').innerText()).includes('40자리'))throw Error('Missing field help');
  await page.keyboard.press('Escape');
  await page.getByRole('button',{name:'다음',exact:true}).click();
  await form.locator('[name=runner]').selectOption('FUNCTIONS');await form.locator('[name=goal]').selectOption('verify');await form.locator('[name=profiles]').fill('quality');await form.locator('[name=artifact]').fill('target/*.jar');
  await page.getByRole('button',{name:'다음',exact:true}).click();
  if(!(await page.locator('#task-summary-source').innerText()).includes('db-companion'))throw Error('Lost source summary');
  if(!(await page.locator('#task-summary-build').innerText()).includes('quality'))throw Error('Lost profile summary');
  const target=JSON.parse(fs.readFileSync('.tools/function-template-target.json','utf8'));
  await form.locator('[name=application]').selectOption(app.id);
  await page.locator('#task-environment-status').filter({hasText:'조회 완료'}).waitFor();
  await form.locator('[name=function]').selectOption(target.id);
  if(!(await page.locator('#task-summary-storage').innerText()).includes('oci-companion-arm-build-997'))throw Error('Missing actual bucket');
  for(const width of [320,390,768,1024,1440,2048]) {
    await page.setViewportSize({width,height:1000});
    if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);
  }
  await page.setViewportSize({width:1440,height:1000});
  await page.screenshot({path:'.tools/task-ux-environment.png',fullPage:true});
  await page.locator('[data-jump="0"]').click();
  if(await form.locator('[name=source]').inputValue()!==chosenUrl)throw Error('Lost source on back');
  await page.locator('[data-jump="2"]').first().click();
  if(await form.locator('[name=function]').inputValue()!==target.id)throw Error('Lost environment on back');
  await page.getByRole('button',{name:'다음',exact:true}).click();
  await page.screenshot({path:'.tools/task-ux-review.png',fullPage:true});
  await page.getByRole('button',{name:'빌드 작업 저장',exact:true}).click();await page.waitForLoadState('load');
  const card=page.locator('.task-card').filter({hasText:name});await card.waitFor();
  await page.screenshot({path:'.tools/task-ux-home.png',fullPage:true});
  await card.getByRole('link',{name:'설정 확인·실행',exact:true}).click();
  if(await page.locator('[name=source]').inputValue()!==chosenUrl)throw Error('Source not applied');
  if(await page.locator('[name=profiles]').inputValue()!=='quality')throw Error('Profile not applied');
  if(!(await page.locator('#function-selection-build').innerText()).includes('quality'))throw Error('Execute summary missing');
  await page.screenshot({path:'.tools/task-ux-execute.png',fullPage:true});
  await page.goto('http://127.0.0.1:8091/builds/tasks?view=new');
  await page.locator('[name=name]').fill('소스 불일치 검증');
  const opt=page.locator('[name=sourceId] option').filter({hasText:'GITHUB · db-companion'}).first();
  await page.locator('[name=sourceId]').selectOption(await opt.getAttribute('value'));
  await until(async()=>/^[a-f0-9]{40}$/.test(await page.locator('[name=commit]').inputValue()));
  await page.getByRole('button',{name:'다음',exact:true}).click();
  await page.getByRole('button',{name:'다음',exact:true}).click();
  const pipeline=page.locator('[name=pipeline] option').filter({hasText:'db-companion-maven'}).first();
  await page.locator('[name=pipeline]').selectOption(await pipeline.getAttribute('value'));
  await until(async()=>(await page.locator('#task-match-status').innerText()).includes('소스 불일치'));
  if(await page.locator('[name=pipeline]').evaluate(e=>e.checkValidity()))throw Error('Mismatch accepted');
  await page.screenshot({path:'.tools/task-source-mismatch.png',fullPage:true});
  // Register the actual OCI build-config source if needed; only local metadata is saved.
  const pipelineId=await page.locator('[name=pipeline]').inputValue();
  await page.locator('[data-jump="0"]').click();
  const popupPromise=page.waitForEvent('popup');await page.getByRole('link',{name:'새 소스 등록 ↗'}).click();const sourcePage=await popupPromise;await sourcePage.waitForLoadState('load');
  const repository=sourcePage.locator('[name=resourceId] option').filter({hasText:'build-config'}).first();
  if(!(await repository.count()))throw Error('Build-config repository unavailable');
  const repositoryId=await repository.getAttribute('value');
  const sourceForm=sourcePage.locator('form').filter({has:sourcePage.locator('[name=resourceId]')});
  // Existing registration is reused by its displayed name.
  const configName=(await repository.innerText()).split(' / ').pop();
  if(!(await page.locator('[name=sourceId] option').filter({hasText:'OCI · '+configName}).count())) {
    await sourceForm.locator('[name=resourceId]').selectOption(repositoryId);await sourceForm.getByRole('button',{name:'등록',exact:true}).click();await sourcePage.waitForLoadState('load');
  }
  await sourcePage.close();await page.locator('#task-sources-refresh').click();
  await until(async()=>(await page.locator('#task-source-status').innerText()).includes('등록 소스 조회 완료'));
  if(await page.locator('[name=name]').inputValue()!=='소스 불일치 검증')throw Error('Lost draft after registration');
  const config=page.locator('[name=sourceId] option').filter({hasText:'OCI · '+configName}).first();await page.locator('[name=sourceId]').selectOption(await config.getAttribute('value'));
  await until(async()=>/^[a-f0-9]{40}$/.test(await page.locator('[name=commit]').inputValue()));
  const configCommit=await page.locator('[name=commit]').inputValue();
  await page.locator('[name=name]').fill('db-companion 빌드 설정 · DevOps');
  await page.getByRole('button',{name:'다음',exact:true}).click();await page.getByRole('button',{name:'다음',exact:true}).click();
  await until(async()=>(await page.locator('#task-match-status').innerText()).startsWith('소스 일치'));
  await page.getByRole('button',{name:'다음',exact:true}).click();await page.getByRole('button',{name:'빌드 작업 저장',exact:true}).click();await page.waitForLoadState('load');
  await page.locator('.task-card').filter({hasText:'db-companion 빌드 설정 · DevOps'}).last().getByRole('link',{name:'설정 확인·실행',exact:true}).click();
  console.log('DevOps execute URL',page.url());
  await page.screenshot({path:'.tools/task-source-devops.png',fullPage:true});
  const commitSelect=page.locator('[name=commit]');
  await until(async()=>await commitSelect.inputValue()===configCommit);
  if(new URL(page.url()).searchParams.get('pipeline')!==pipelineId)throw Error('Wrong pipeline');
  if(errors.length)throw Error(errors.join('\n'));
  console.log('PASS actual OCI environment, task save/reload/execution values, persistent summary/back, help, six widths; no build invoked');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
