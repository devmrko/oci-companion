const {chromium}=require('../.tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT required');
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8091';const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(90000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
 await page.goto(base+'/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 const build=JSON.parse(fs.readFileSync('.tools/alpha6-flow-state.json')),vm=JSON.parse(fs.readFileSync('.tools/alpha6-vm-state.json'));
 const original=vm.runs.find(r=>r.version==='alpha6');
 const query=new URLSearchParams({pipeline:original.pipeline,run:original.run,sourcePipeline:build.pipeline,sourceRun:build.run});
 await page.goto(base+'/deployments/runs?'+query);
 const assertContext=()=>{const q=new URL(page.url()).searchParams;if(q.get('sourcePipeline')!==build.pipeline||q.get('sourceRun')!==build.run)throw Error('Build selection lost');};
 for(const label of ['상태 새로고침','로그 새로고침']){await page.getByRole('link',{name:label,exact:true}).click();await page.waitForLoadState('load');assertContext();}
 await page.getByRole('link',{name:'다시 실행·대상 확인',exact:true}).click();await page.waitForLoadState('load');assertContext();
 await page.locator('main').filter({hasText:build.run}).waitFor();
 await page.getByText('대상 VM이 RUNNING 상태가 아닙니다.',{exact:false}).waitFor();
 if(await page.getByRole('button',{name:'확인한 배포 실행',exact:true}).count())throw Error('Stopped VM can execute');
 for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);}
 await page.screenshot({path:'.tools/deployment-retry-proof.png',fullPage:true});
 const failed=JSON.parse(fs.readFileSync('.tools/private-oke-first-run.json'));
 await page.goto(base+'/deployments/runs?'+new URLSearchParams({pipeline:failed.pipeline,run:failed.run,sourcePipeline:build.pipeline,sourceRun:build.run}));
 await page.getByRole('link',{name:'다시 실행·대상 확인',exact:true}).click();await page.waitForLoadState('load');assertContext();
 if(await page.getByRole('button',{name:'확인한 배포 실행',exact:true}).count())throw Error('Mismatched retry can execute');
 await page.getByText('빌드·전달 성공과 배포 파일 일치를 확인하지 못했습니다.',{exact:false}).waitFor();
 await page.screenshot({path:'.tools/deployment-retry-mismatch.png',fullPage:true});
 if(errors.length)throw Error(errors.join('\n'));console.log('PASS native history refresh/retry retains build; stopped VM blocked; mismatched failed retry blocked; no deployment started');
 }catch(e){await page.screenshot({path:'.tools/container-preparation-error.png',fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
