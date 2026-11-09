const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT required');
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8092';const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(90000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
 await page.goto(base+'/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 await page.goto(base+'/deployments/runs');const options=await page.locator('[name=pipeline] option').evaluateAll(xs=>xs.filter(x=>x.value).map(x=>({id:x.value,name:x.textContent})));const p=options.find(x=>x.name.includes('bootstrap-memory-0.6.0'));if(!p)throw Error('Existing validation pipeline missing');
 await page.goto(base+'/deployments/runs?pipeline='+encodeURIComponent(p.id));await page.getByRole('heading',{name:'실행 이력',exact:true}).waitFor();await page.getByRole('link',{name:'상태·로그',exact:true}).first().click();await page.getByRole('heading',{name:'배포 로그',exact:true}).waitFor();
 const logs=page.locator('section').filter({has:page.getByRole('heading',{name:'배포 로그',exact:true})});const count=await logs.locator('tbody tr').count();const note=await logs.locator('p').first().innerText();if(!count)throw Error('No actual OCI log lines: '+note);
 for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);}
 await page.screenshot({path:'.tools/deployment-history-ui.png',fullPage:true});if(errors.length)throw Error(errors.join('\n'));
 fs.writeFileSync('.tools/deployment-history-ui.json',JSON.stringify({url:page.url(),logLines:count,note,errors,cloudMutations:false},null,2));console.log('PASS',JSON.stringify({logLines:count,note,errors}));
 }catch(e){await page.screenshot({path:'.tools/deployment-history-ui-error.png',fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
