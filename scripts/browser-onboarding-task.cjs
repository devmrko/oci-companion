const {chromium}=require('../.tools/browser/node_modules/playwright');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT profile required');
 const browser=await chromium.launch({headless:true});try{
 const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(120000);
 await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
 const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption('ocid1.compartment.oc1..aaaaaaaam3amapwxz6nyciqzb2v2iwg66t22g47vabo5ilnghbsskooeop3q');await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');

 const fs=require('fs');
 const state={languages:{automation:JSON.parse(fs.readFileSync('.tools/ui-onboarding-state.json'))}};
 const base={repository:{http_url:state.languages.automation.repositoryUrl}};
 const commit=JSON.parse(fs.readFileSync('.tools/onboarding-gitea.json')).commit;
 for(const [lang,item] of Object.entries(state.languages)){
  const name='Gitea 첫 빌드 · Python HTTP API';
  await page.goto('http://127.0.0.1:8091/builds/tasks');
  const existing=page.locator('.task-card').filter({hasText:name});
  if(await existing.count())await existing.getByRole('link',{name:'설정 수정',exact:true}).click();
  else await page.goto('http://127.0.0.1:8091/builds/tasks?view=new');
  const form=page.locator('#build-task-form');
  await form.locator('[name=name]').fill(name);
  await form.locator('[name=source]').fill(base.repository.http_url);
  await form.locator('[name=branch]').fill('validation/gitea-main');
  await form.locator('[name=commit]').fill(commit);
  await page.locator('#task-next').click();
  await form.locator('[name=target]').selectOption('FILE');
  await form.locator('[name=architecture]').selectOption('UNKNOWN');
  await page.locator('#task-next').click();
  await form.locator('[name=pipeline]').selectOption(item.pipeline);
  await page.locator('#task-match-status').filter({hasText:/^소스 일치/}).waitFor({state:'visible'});
  await page.locator('#task-next').click();
  await form.locator('button[type=submit]').click();
  await page.waitForURL('**/builds/tasks');
  await page.locator('.task-card').filter({hasText:name}).screenshot({path:'.tools/ui-onboarding-task.png'});console.log('REGISTERED',name);
 }
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
