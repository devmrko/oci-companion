const {chromium}=require('../.tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 const profile=process.env.COMPANION_TEST_PROFILE, app=process.env.COMPANION_TEST_APP, region=process.env.COMPANION_TEST_REGION, compartment=process.env.COMPANION_TEST_COMPARTMENT;
 if(!profile||!app||!region||!compartment)throw Error('Explicit profile, app, region and compartment required');
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8092';const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(90000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
 await page.goto(base+'/login');await page.locator('[name=profile]').selectOption(profile);await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption(region);await scope.locator('[name=compartment]').selectOption({label:compartment});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 await page.goto(base+'/agents/connections?selected='+encodeURIComponent(app));
 const section=page.locator('section').filter({has:page.getByRole('heading',{name:'앱이 사용하는 OKE 노드',exact:true})});await section.getByText('조회 완료',{exact:true}).waitFor();
 const rows=section.locator('tbody tr');if(await rows.count()<1)throw Error('No assigned nodes');
 const evidence=await rows.allTextContents();for(const row of evidence){if(!row.includes(((process.env.COMPANION_TEST_ARCH||'amd64')+' / '+(process.env.COMPANION_TEST_ARCH||'amd64')))||!row.includes('linux / linux')||!row.includes('일치'))throw Error('Unexpected native node platform');}
 for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);await section.scrollIntoViewIfNeeded();await page.screenshot({path:'.tools/node-platform-'+width+'.png'});}
 fs.writeFileSync('.tools/node-platform-ui.json',JSON.stringify({rows:evidence,errors,mutations:false}));if(errors.length)throw Error(errors.join('\n'));console.log('PASS actual assigned node architecture/OS labels and responsive UI');
 }catch(e){await page.screenshot({path:'.tools/node-platform-error.png',fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
