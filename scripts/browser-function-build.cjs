const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
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
  await page.goto(base+'?application='+app.id);await page.getByText('db-companion-builder',{exact:true}).waitFor();
  await page.goto(base+'?view=new&application='+app.id);
  const form=page.locator('form[action="/builds/functions/template"]');
  if(await form.locator('[name=namespace]').inputValue()!=='apackrsct01')throw Error('Actual namespace missing');
  await form.locator('[name=name]').fill('maven-builder-998');await form.locator('[name=bucket]').fill('oci-companion-arm-build-997');
  if(process.env.COMPANION_TEST_FUNCTION_RUN!=='1' && process.env.COMPANION_TEST_FUNCTION_RESULT!=='1') { const downloadPromise=page.waitForEvent('download');await form.getByRole('button',{name:'Fn Project ZIP 다운로드'}).click();const download=await downloadPromise;await download.saveAs('.tools/fn-template-998.zip'); }
  for(const width of [320,390,768,1024,1440,2048]) {
   await page.setViewportSize({width,height:1000});
   if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);
  }
  await page.setViewportSize({width:1440,height:1000});await page.screenshot({path:'.tools/function-environments.png',fullPage:true});
  console.log('PASS actual apps/functions, namespace/bucket selection, six viewport sizes');
  if(process.env.COMPANION_TEST_FUNCTION_RUN==='1') {
   const target=JSON.parse(fs.readFileSync('.tools/function-template-target.json','utf8'));
   await page.goto(base+'?view=execute&application='+app.id+'&function='+target.id);
   const run=page.locator('form[action="/builds/functions/run"]');
   await run.locator('[name=source]').fill('https://github.com/devmrko/db-companion.git');await run.locator('[name=commit]').fill('7a5cb34b68d8dae90f9c398667a0db976bb2f6d2');await run.locator('[name=goal]').selectOption('verify');await run.locator('[name=profiles]').fill('quality');await run.locator('[name=artifact]').fill('target/*.jar');
   await run.getByRole('button',{name:'빌드 실행',exact:true}).click();await page.waitForLoadState('load');
   if(!(await page.locator('main').innerText()).includes('빌드 접수'))throw Error(await page.locator('main').innerText());
   fs.writeFileSync('.tools/function-template-run.json',JSON.stringify({id:new URL(page.url()).searchParams.get('run'),function:target.id,application:app.id,url:page.url()},null,2));
   console.log('PASS GUI detached build accepted');
  }
  if(process.env.COMPANION_TEST_FUNCTION_RESULT==='1') {
   const saved=JSON.parse(fs.readFileSync('.tools/function-template-run.json','utf8'));
   await page.goto(saved.url);
   const main=await page.locator('main').innerText();
   if(!main.includes('SUCCEEDED') || !main.includes('BUILD SUCCESS') || !main.includes('db-manage-companion-0.1.0-alpha.17.jar'))throw Error('Missing actual result, log or artifact');
   for(const view of ['history','execute']) {
    await page.goto(base+'?view='+view+'&application='+saved.application+'&function='+saved.function+'&run='+saved.id);
    for(const width of [320,390,768,1024,1440,2048]) {
     await page.setViewportSize({width,height:1000});
     if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error(view+' overflow '+width);
    }
   }
   console.log('PASS actual successful build history/log/artifact, execute/history six viewport sizes');
  }
  if(errors.length)throw Error(errors.join('\n'));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
