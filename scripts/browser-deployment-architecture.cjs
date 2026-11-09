const {chromium}=require('../.tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT required');
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8091';const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(90000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
 await page.goto(base+'/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 const oke=JSON.parse(fs.readFileSync('.tools/private-oke-created.json'));
 await page.goto(base+'/deployments/runs?'+new URLSearchParams({pipeline:oke.pipeline}));
 const panel=page.locator('#deployment-preflight');await panel.getByRole('heading',{name:'배포 전 점검',exact:true}).waitFor();
 const row=panel.getByRole('row').filter({has:page.getByRole('cell',{name:'아키텍처·명세',exact:true})});const missing=process.env.COMPANION_EXPECT_ARCHITECTURE==='mismatch';
 await row.getByRole('cell',{name:missing?'실행 불가':'확인',exact:true}).waitFor();
 if(missing){if(await page.getByRole('button',{name:'확인한 배포 실행',exact:true}).count())throw Error('Incompatible architecture can execute');await row.getByText('아키텍처 불일치.',{exact:false}).waitFor();}
 else await page.getByRole('button',{name:'확인한 배포 실행',exact:true}).waitFor();
 for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);}
 await row.scrollIntoViewIfNeeded();await page.screenshot({path:'.tools/deployment-architecture-'+(missing?'mismatch':'match')+'.png'});
 if(errors.length)throw Error(errors.join('\n'));console.log('PASS actual OCIR '+(missing?'architecture mismatch blocks execution':'image/manifest architecture matches')+'; no deployment started');
 }catch(e){await page.screenshot({path:'.tools/container-preparation-error.png',fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
