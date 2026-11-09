const {chromium}=require('../.tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT required');
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8092';const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(90000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
 await page.goto(base+'/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 await page.goto(base+'/environments/containers');
 const links=await page.locator('table a[href*="instance="]').evaluateAll(xs=>xs.map(x=>({href:x.getAttribute('href'),name:x.textContent,state:x.closest('tr').children[1].textContent.trim()})));
 let checked=0;
 for(const resource of links){
  await page.goto(base+resource.href);if(!await page.getByRole('heading',{name:'컨테이너 상태·로그',exact:true}).count())continue;
  const section=page.locator('section').filter({has:page.getByRole('heading',{name:'컨테이너 상태·로그',exact:true})});
  const expected={ACTIVE:['중지','재시작','삭제'],INACTIVE:['시작','삭제'],FAILED:['삭제']}[resource.state]||[];
  for(const label of ['시작','중지','재시작','삭제']){const count=await section.getByRole('button',{name:label,exact:true}).count();if(Boolean(count)!==expected.includes(label))throw Error('Action mismatch '+resource.state+' '+label);}
  for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);}
  checked++;
 }
 await page.screenshot({path:'.tools/container-lifecycle-ui.png',fullPage:true});
 fs.writeFileSync('.tools/container-lifecycle-ui.json',JSON.stringify({resources:links.map(x=>({name:x.name,state:x.state})),checked,mutations:false,errors},null,2));
 if(errors.length)throw Error(errors.join('\n'));console.log('PASS',JSON.stringify({checked,errors}));
 }catch(e){await page.screenshot({path:'.tools/container-preparation-error.png',fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
