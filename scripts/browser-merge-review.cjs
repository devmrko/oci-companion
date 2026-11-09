const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
(async()=>{
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8092';
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT profile required');
 const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(120000);const errors=[];page.on('pageerror',e=>errors.push(e.message));const results=[];
 async function visit(path){const r=await page.goto(base+path);if(!r.ok())throw Error(path+' HTTP '+r.status());if((await page.locator('body').innerText()).includes('Whitelabel Error'))throw Error(path+' rendering error');}
 async function layout(label){for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});await page.waitForTimeout(100);if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+label+' '+width);}results.push(label);}
 try{
 await visit('/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 for(const path of ['/environments','/deployments/runs','/agents','/agents/connections','/builds/functions']){await visit(path);await layout(path);await page.screenshot({path:'.tools/merge-ui-'+path.replaceAll('/','-')+'.png',fullPage:true});}
 await visit('/environments');const oke=page.locator('tr').filter({hasText:'OKE 클러스터'}).first();if(await oke.count()){await oke.getByRole('link',{name:'상세·조건 확인',exact:true}).click();await page.getByRole('heading',{name:'OKE 클러스터',exact:true}).waitFor();await layout('OKE environment detail');}
 await visit('/deployments/runs');const choices=await page.locator('[name=pipeline] option').evaluateAll(nodes=>nodes.filter(x=>x.value).map(x=>({id:x.value,label:x.textContent})));
 for(const kind of ['vm','oke']){const chosen=choices.find(x=>(kind==='oke'?x.label.toLowerCase().includes('oke'):!x.label.toLowerCase().includes('oke'))&&x.label.includes('ACTIVE'));if(!chosen){results.push(kind+' pipeline absent');continue;}await visit('/deployments/runs?pipeline='+encodeURIComponent(chosen.id));const plan=page.getByRole('heading',{name:'실행 전 확인',exact:true});if(await plan.count()){await layout(kind+' deployment confirmation');await page.getByRole('button',{name:'확인한 배포 실행',exact:true}).waitFor();}else{const notice=await page.locator('.notice').allTextContents();if(!notice.join('').trim())throw Error(kind+' plan has no result');results.push(kind+' deployment blocked: '+notice.join(' '));}}
 if(errors.length)throw Error(errors.join('\n'));fs.writeFileSync('.tools/merge-ui-results.json',JSON.stringify({results,errors,cloudMutations:false},null,2));console.log('PASS',JSON.stringify(results));
 }catch(e){await page.screenshot({path:'.tools/merge-ui-error.png',fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
