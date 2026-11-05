const {chromium}=require('../.tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT profile required');
 const browser=await chromium.launch({headless:true});try{
 const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(120000);
 await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
 const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption('ocid1.compartment.oc1..aaaaaaaam3amapwxz6nyciqzb2v2iwg66t22g47vabo5ilnghbsskooeop3q');await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 await page.goto('http://127.0.0.1:8091/builds/configure');const sourceId=await page.locator('select[name=source] option').filter({hasText:'oci-companion-build-validation'}).first().getAttribute('value');
 const params=new URLSearchParams({region:'ap-seoul-1',compartment:'ocid1.compartment.oc1..aaaaaaaam3amapwxz6nyciqzb2v2iwg66t22g47vabo5ilnghbsskooeop3q',sourceId});
 let response=await page.request.get('http://127.0.0.1:8091/builds/tasks/source-versions?'+params);if(!response.ok())throw Error('Gitea refs failed '+response.status());const refs=await response.json();if(!refs.some(x=>x.name==='main'&&/^[a-f0-9]{40}$/.test(x.commit)))throw Error('Gitea main branch missing');
 params.set('ref','main');response=await page.request.get('http://127.0.0.1:8091/builds/tasks/source-versions?'+params);if(!response.ok()||!(await response.json()).length)throw Error('Gitea commits failed');console.log('PASS actual Gitea branch and commit lookup');
 if(process.env.CHECK_DEVOPS_REPORT==='1'){
  const target=JSON.parse(fs.readFileSync('.tools/runtime-cloud-validation.json','utf8'));
  await page.goto('http://127.0.0.1:8091/builds?tab=history&pipeline='+target.pipeline+'&run='+target.run);
  const option=page.locator('#build-report-form [name=artifact] option').filter({hasText:'test-reports'}).first();const deliveredDeadline=Date.now()+120000;while(!(await option.count())&&Date.now()<deliveredDeadline){await new Promise(r=>setTimeout(r,2000));await page.reload();}if(!(await option.count()))throw Error('No delivered report yet');await page.locator('#build-report-form [name=artifact]').selectOption(await option.getAttribute('value'));await page.getByRole('button',{name:'보고서 보기'}).click();
  const end=Date.now()+120000;while(Date.now()<end&&await page.getByRole('button',{name:'보고서 보기'}).isDisabled())await new Promise(r=>setTimeout(r,300));
  const summary=await page.locator('#build-report-status').innerText();console.log('DEVOPS_REPORT',summary);if(summary!=='테스트 3 · 실패 0 · 오류 0 · 건너뜀 0')throw Error(summary);
  const logDeadline=Date.now()+90000;while(!(await page.locator('#log-output').innerText()).trim()&&Date.now()<logDeadline)await new Promise(r=>setTimeout(r,500));if(!(await page.locator('#log-output').innerText()).trim())throw Error('Actual DevOps logs missing');console.log('PASS actual DevOps build logs');
  await page.locator('#build-report-status').scrollIntoViewIfNeeded();await page.screenshot({path:'.tools/build-completion-devops-report.png'});
 }
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
