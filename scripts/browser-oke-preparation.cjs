const {chromium}=require('../.tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT required');
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8092';const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(90000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
 await page.goto(base+'/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 await page.goto(base+'/environments/oke');const options=await page.locator('[name=cluster] option').evaluateAll(xs=>xs.filter(x=>x.value).map(x=>({id:x.value,name:x.textContent})));const privateCluster=options.find(x=>x.name.includes('launchpad-oke'));const publicCluster=options.find(x=>x.name.includes('document_search'));if(!privateCluster||!publicCluster)throw Error('Test clusters unavailable');
 await page.locator('[name=cluster]').selectOption(privateCluster.id);await page.getByRole('button',{name:'사양·연결 확인',exact:true}).click();await page.getByRole('heading',{name:'노드 사양',exact:true}).waitFor();
 if(!await page.locator('[name=subnet] option').count())throw Error('No network choices');if(!await page.locator('.data-table tbody tr').count())throw Error('No node pools');
 for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);}
 await page.screenshot({path:'.tools/oke-preparation-private.png',fullPage:true});
 await page.goto(base+'/environments/oke?cluster='+encodeURIComponent(publicCluster.id));await page.getByRole('heading',{name:'DevOps 배포 환경 연결',exact:true}).waitFor();if(await page.locator('[name=subnet]').count())throw Error('Public cluster should not require private subnet');
 let created='';if(process.env.COMPANION_TEST_MUTATE==='oke-reference'){
 const projects=await page.locator('[name=project] option').evaluateAll(xs=>xs.filter(x=>x.value).map(x=>({id:x.value,name:x.textContent})));const project=projects.find(x=>x.name==='db-companion');if(!project)throw Error('Validation project unavailable');
 await page.locator('[name=project]').selectOption(project.id);await page.locator('[name=name]').fill('companion-oke-preparation-20261109');await page.locator('[name=description]').fill('Companion 화면 검증 · 기존 클러스터 참조 · 노드 변경 없음');await page.getByRole('button',{name:'OCI 환경 생성',exact:true}).click();await page.waitForLoadState('load');created=new URL(page.url()).searchParams.get('environment')||'';if(!created)throw Error('Native environment creation failed: '+await page.locator('main').innerText());
 fs.writeFileSync('.tools/oke-preparation-created.json',JSON.stringify({id:created,cluster:publicCluster.id,project:project.id}));
 }
 await page.screenshot({path:'.tools/oke-preparation-final.png',fullPage:true});if(errors.length)throw Error(errors.join('\n'));console.log('PASS',JSON.stringify({clusters:options.length,privateCluster:privateCluster.name,publicCluster:publicCluster.name,created,errors}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
