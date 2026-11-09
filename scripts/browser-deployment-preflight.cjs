const {chromium}=require('../.tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT required');
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8091';const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(90000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
 await page.goto(base+'/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 const vm=JSON.parse(fs.readFileSync('.tools/alpha6-vm-state.json')),oke=JSON.parse(fs.readFileSync('.tools/private-oke-created.json'));
 await page.goto(base+'/deployments/runs?'+new URLSearchParams({pipeline:oke.pipeline}));
 const panel=page.locator('#deployment-preflight');await panel.getByRole('heading',{name:'배포 전 점검',exact:true}).waitFor();
 for(const title of ['파이프라인·대상','배포 명세']){const row=panel.getByRole('row').filter({has:page.getByRole('cell',{name:title,exact:true})});if(await row.getByRole('cell',{name:'확인',exact:true}).count()!==1)throw Error(title+' not checked');}
 for(const title of ['실행 주체 권한','이미지 인증·아키텍처','네트워크·앱 응답']){const row=panel.getByRole('row').filter({has:page.getByRole('cell',{name:title,exact:true})});if(await row.getByRole('cell',{name:'미확인',exact:true}).count()!==1)throw Error(title+' falsely checked');}
 await page.getByRole('button',{name:'확인한 배포 실행',exact:true}).waitFor();
 for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);await panel.scrollIntoViewIfNeeded();await page.screenshot({path:'.tools/deployment-preflight-'+width+'.png'});}
 await page.goto(base+'/deployments/runs?'+new URLSearchParams({pipeline:vm.pipelines.alpha6}));
 await panel.getByText('실행 불가',{exact:true}).waitFor();await panel.getByText('대상 VM이 RUNNING 상태가 아닙니다.',{exact:false}).waitFor();
 if(await panel.getByRole('cell',{name:'확인',exact:true}).count())throw Error('Failed preflight marked checked');
 if(await page.getByRole('button',{name:'확인한 배포 실행',exact:true}).count())throw Error('Stopped VM can execute');
 await page.screenshot({path:'.tools/deployment-preflight-blocked.png',fullPage:true});
 const run=vm.runs[0];await page.goto(base+'/deployments/runs?'+new URLSearchParams({pipeline:run.pipeline,run:run.run}));if(await panel.count())throw Error('History incorrectly shows preflight');
 if(errors.length)throw Error(errors.join('\n'));console.log('PASS actual OKE check summary, unverified checks, stopped VM block, history exclusion, responsive layout; no cloud mutation');
 }catch(e){await page.screenshot({path:'.tools/container-preparation-error.png',fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
