const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Set COMPANION_TEST_PROFILE=DEFAULT');
 const stateFile='.tools/vm-environment-validation.json';const state=fs.existsSync(stateFile)?JSON.parse(fs.readFileSync(stateFile)):{};
 const source=JSON.parse(fs.readFileSync('.tools/ui-onboarding-state.json'));
 const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(120000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
 await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
 const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 await page.goto('http://127.0.0.1:8091/environments?tab=vms');const option=page.locator('select[name=instance] option').filter({hasText:'codex-test-vm ·'});if(await option.count()!==1)throw Error('Dedicated stopped validation VM not found');state.instance=await option.getAttribute('value');
 await page.locator('select[name=instance]').selectOption(state.instance);await page.getByRole('button',{name:'조건 확인',exact:true}).click();await page.getByRole('heading',{name:'배포 조건',exact:true}).waitFor();
 const inspection=page.locator('section').filter({has:page.getByRole('heading',{name:'배포 조건',exact:true})});const before=await inspection.innerText();if(!before.includes('STOPPED')||!before.includes('미확인'))throw Error('Stopped VM readiness not represented');fs.writeFileSync('.tools/vm-environment-inspection.txt',before);await page.screenshot({path:'.tools/vm-environment-inspection.png',fullPage:true});console.log('PASS real VM read: stopped and unknown checks');
 if(process.env.COMPANION_UI_ENVIRONMENT_WRITE==='1'&&!state.environment){
  await page.goto('http://127.0.0.1:8091/environments');const existing=page.locator('tr').filter({hasText:'companion-vm-preparation-20261104'});
  if(await existing.count()){const url=await existing.getByRole('link',{name:'선택',exact:true}).getAttribute('href');state.environment=new URL(url,'http://127.0.0.1:8091').searchParams.get('environment');}
  else {
   await page.goto('http://127.0.0.1:8091/environments?tab=new');const form=page.locator('form[action="/environments"][method=post]');await form.locator('[name=project]').selectOption(source.project);await form.locator('[name=name]').fill('companion-vm-preparation-20261104');await form.locator('[name=description]').fill('VM environment UI validation; reference only, no deployment');await form.locator('input[name=instances][value="'+state.instance+'"]').check();await form.getByRole('button',{name:'OCI 환경 생성',exact:true}).click();await page.waitForURL(/environment=/);state.environment=new URL(page.url()).searchParams.get('environment');
  }
  fs.writeFileSync(stateFile,JSON.stringify(state,null,2));console.log('CREATED/REUSED native environment reference',state.environment);
 }
 if(state.environment){
  await page.goto('http://127.0.0.1:8091/environments?environment='+state.environment);await page.getByRole('link',{name:'이 환경으로 배포 준비',exact:true}).click();await page.getByRole('heading',{name:'선택한 환경',exact:true}).waitFor();if(!(await page.locator('main').innerText()).includes('companion-vm-preparation-20261104'))throw Error('Environment selection lost');await page.screenshot({path:'.tools/vm-environment-selected.png',fullPage:true});console.log('PASS native environment selection into deployment');
 }
 for(const url of ['/environments?tab=new','/environments?tab=vms&instance='+state.instance,'/deployments'+(state.environment?'?environment='+state.environment:'')]){
  await page.goto('http://127.0.0.1:8091'+url);
  for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});await page.waitForTimeout(150);if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width+' '+url);}
 }
 await page.goto('http://127.0.0.1:8091/environments?tab=vms&instance='+state.instance);if(!(await page.locator('section').filter({has:page.getByRole('heading',{name:'배포 조건',exact:true})}).innerText()).includes('STOPPED'))throw Error('VM state changed');
 if(errors.length)throw Error(errors.join('\n'));console.log('PASS 390/768/1440 layout, no JS errors, VM still stopped');
 }catch(e){await page.screenshot({path:'.tools/vm-environment-error.png',fullPage:true}).catch(()=>{});fs.writeFileSync('.tools/vm-environment-error.txt',await page.locator('body').innerText().catch(()=>''));throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
