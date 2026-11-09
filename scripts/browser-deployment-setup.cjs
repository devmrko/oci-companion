const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
(async()=>{
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8092';
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT profile required');
 const env=JSON.parse(fs.readFileSync('.tools/vm-environment-validation.json'));
 const state=JSON.parse(fs.readFileSync('.tools/ui-onboarding-state.json'));
 const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(120000);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 async function visit(path){const r=await page.goto(base+path);if(!r.ok())throw Error(path+' HTTP '+r.status());}
 async function layout(label){for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+label+' '+width);}await page.screenshot({path:'.tools/deployment-setup-'+label+'.png',fullPage:true});}
 try{
 await visit('/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 await visit('/deployments/configure?environment='+encodeURIComponent(env.environment)+'&repository='+encodeURIComponent(state.registry));
 await page.getByRole('heading',{name:'CPU·메모리·스토리지',exact:true}).waitFor();
 const capacity=await page.locator('section').filter({has:page.getByRole('heading',{name:'CPU·메모리·스토리지',exact:true})}).innerText();
 const artifact=await page.locator('[name=artifact] option').evaluateAll(xs=>xs.filter(x=>x.textContent.includes('hello/app.tar.gz')).map(x=>({value:x.value,label:x.textContent})));if(!artifact.length)throw Error('Native app artifact absent');
 await page.locator('[name=artifact]').selectOption(artifact[0].value);
 await page.locator('[name=name]').fill('companion-vm-configuration-'+new Date().toISOString().replace(/[^0-9]/g,'').slice(0,14));await page.locator('[name=description]').fill('VM 배포 구성 화면 검증 · 명세 생성만 수행');
 await page.locator('[name=install]').fill('python3 -m py_compile "$DEPLOY_DIR/app.py"');await page.locator('[name=health]').fill('test -f "$DEPLOY_DIR/app.py"');
 await layout('form');await page.getByRole('button',{name:'설정 검토',exact:true}).click();await page.getByRole('heading',{name:'생성 전 확인',exact:true}).waitFor();await layout('preview');
 const yaml=await page.locator('.deployment-spec').innerText();if(!yaml.includes('DEPLOY_DIR')||!yaml.includes('python3 -m py_compile'))throw Error('Native spec missing input');
 let created=false,pipeline='';if(process.env.COMPANION_TEST_CREATE==='1'){
 await page.getByRole('button',{name:'확인한 설정으로 생성',exact:true}).click();await page.waitForLoadState('load');const result=page.getByRole('heading',{name:'파이프라인 생성 완료',exact:true});if(!await result.count())throw Error('Creation did not complete: '+await page.locator('.notice').allTextContents());
 pipeline=await page.locator('.resource-details dd').first().innerText();created=true;await layout('created');fs.writeFileSync('.tools/deployment-setup-created.json',JSON.stringify({pipeline,environment:env.environment,repository:state.registry},null,2));
 await page.getByRole('link',{name:'실행 전 확인',exact:true}).click();await page.waitForLoadState('load');if(await page.getByRole('button',{name:'확인한 배포 실행',exact:true}).count())throw Error('Stopped VM unexpectedly executable');const notices=(await page.locator('.notice').allTextContents()).join(' ');if(!notices.includes('VM'))throw Error('Missing stopped VM explanation');await layout('stopped');
 }
 if(errors.length)throw Error(errors.join('\n'));fs.writeFileSync(created?'.tools/deployment-setup-results.json':'.tools/deployment-setup-read-results.json',JSON.stringify({capacity,artifact:artifact[0],yaml,created,pipeline,errors},null,2));console.log(JSON.stringify({capacity,created,pipeline,errors},null,2));
 }catch(e){await page.screenshot({path:'.tools/deployment-setup-error.png',fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
