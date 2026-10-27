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
  await form.locator('[name=name]').fill(name);await form.locator('[name=runner]').selectOption('FUNCTIONS');
  await form.locator('[name=source]').fill('https://github.com/devmrko/db-companion.git');
  await form.locator('[name=commit]').fill('7a5cb34b68d8dae90f9c398667a0db976bb2f6d2');
  await page.getByLabel('커밋 SHA 도움말',{exact:true}).click();
  if(!(await page.locator('.field-help[open]').innerText()).includes('40자리'))throw Error('Missing field help');
  await page.keyboard.press('Escape');
  await page.getByRole('button',{name:'다음',exact:true}).click();
  await form.locator('[name=goal]').selectOption('verify');await form.locator('[name=profiles]').fill('quality');await form.locator('[name=artifact]').fill('target/*.jar');
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
  if(await form.locator('[name=source]').inputValue()!=='https://github.com/devmrko/db-companion.git')throw Error('Lost source on back');
  await page.locator('[data-jump="2"]').first().click();
  if(await form.locator('[name=function]').inputValue()!==target.id)throw Error('Lost environment on back');
  await page.getByRole('button',{name:'다음',exact:true}).click();
  await page.screenshot({path:'.tools/task-ux-review.png',fullPage:true});
  await page.getByRole('button',{name:'빌드 작업 저장',exact:true}).click();await page.waitForLoadState('load');
  const card=page.locator('.task-card').filter({hasText:name});await card.waitFor();
  await page.screenshot({path:'.tools/task-ux-home.png',fullPage:true});
  await card.getByRole('link',{name:'설정 확인·실행',exact:true}).click();
  if(await page.locator('[name=source]').inputValue()!=='https://github.com/devmrko/db-companion.git')throw Error('Source not applied');
  if(await page.locator('[name=profiles]').inputValue()!=='quality')throw Error('Profile not applied');
  if(!(await page.locator('#function-selection-build').innerText()).includes('quality'))throw Error('Execute summary missing');
  await page.screenshot({path:'.tools/task-ux-execute.png',fullPage:true});
  await page.goto('http://127.0.0.1:8091/builds/tasks');
  const managedName='db-companion 검증 · DevOps';const managedCard=page.locator('.task-card').filter({hasText:managedName});
  if(await managedCard.count())await managedCard.getByRole('link',{name:'설정 수정',exact:true}).click();else await page.getByRole('link',{name:'새 빌드 작업',exact:true}).click();
  await page.locator('[name=name]').fill(managedName);await page.locator('[name=runner]').selectOption('DEVOPS');
  const pipeline=page.locator('[name=pipeline] option').filter({hasText:'db-companion-maven'}).first();
  const pipelineId=await pipeline.getAttribute('value');await page.locator('[name=pipeline]').selectOption(pipelineId);
  for(let i=0;i<3;i++)await page.getByRole('button',{name:'다음',exact:true}).click();
  await page.getByRole('button',{name:'빌드 작업 저장',exact:true}).click();await page.waitForLoadState('load');
  await page.screenshot({path:'.tools/task-ux-home.png',fullPage:true});
  await page.locator('.task-card').filter({hasText:managedName}).getByRole('link',{name:'설정 확인·실행',exact:true}).click();
  if(new URL(page.url()).searchParams.get('pipeline')!==pipelineId)throw Error('Managed pipeline not applied');
  if(errors.length)throw Error(errors.join('\n'));
  console.log('PASS actual OCI environment, task save/reload/execution values, persistent summary/back, help, six widths; no build invoked');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
