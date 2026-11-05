const {chromium}=require('../.tools/browser/node_modules/playwright'),fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('DEFAULT explicitly required');
 const evidence=JSON.parse(fs.readFileSync('.tools/build-flow-run.json','utf8'));
 const browser=await chromium.launch({headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(60000);
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  page.on('dialog',dialog=>dialog.type()==='beforeunload'?dialog.accept():dialog.dismiss());
  await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  const base='http://127.0.0.1:8091/builds?pipeline='+encodeURIComponent(evidence.pipeline);
  await page.goto(base+'&tab=execute');
  await page.locator('.build-execution').waitFor();
  if(await page.locator('.build-flow [aria-current=step]').getAttribute('data-step')!=='execute')throw Error('Current execute step missing');
  if(await page.locator('.build-execution [name=purpose]').inputValue()!=='store')throw Error('Existing delivery not reflected');
  const destination=page.locator('.build-destination-form').filter({has:page.locator('[name=output][value="db-companion-jar"]')});
  if(await destination.locator('[name=repository]').inputValue()!==evidence.repository)throw Error('Current Registry repository not selected');
  if(await destination.locator('[name=path]').inputValue()!=='db-companion/app.jar')throw Error('Current output path missing');
  await page.locator('.build-flow [data-step=storage]').click();
  if(await page.locator('.build-flow [aria-current=step]').getAttribute('data-step')!=='storage')throw Error('Step navigation does not update');
  for(const width of [320,390,768,1024,1440,2048]){
   await page.setViewportSize({width,height:1000});
   if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Execute page overflow '+width);
  }
  await page.setViewportSize({width:1440,height:1000});await page.goto(base+'&tab=pipelines');
  await page.locator('#create-build-stage').waitFor();
  if(await page.locator('#create-build-stage [name=buildSource] option').count()<2)throw Error('Actual source repository list missing');
  await page.getByText('빌드 명세 작성',{exact:true}).click();
  for(const tool of ['maven','gradle','python','node']){
   await page.locator('#build-specification-form [name=tool]').selectOption(tool);
   await page.getByRole('button',{name:'명세 미리보기',exact:true}).click();await page.getByText('미리보기 · OCI 미적용',{exact:true}).waitFor();
   if(!(await page.locator('#build-specification-preview').innerText()).includes('component: build'))throw Error('Invalid '+tool+' specification');
   console.log('PASS '+tool+' specification preview');
  }
  for(const width of [320,390,768,1024,1440,2048]){
   await page.setViewportSize({width,height:1000});
   if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Pipeline page overflow '+width);
  }
  await page.setViewportSize({width:1440,height:1000});await page.screenshot({path:'.tools/build-gap-pipelines.png',fullPage:true});
  // Server must reject a purpose that would incorrectly imply skipping the existing delivery.
  await page.goto(base+'&tab=execute');await page.locator('.build-execution [name=purpose]').selectOption('test');
  await page.getByRole('button',{name:'실행 설정 확인',exact:true}).click();await page.locator('#build-review button[data-confirmed]').click();await page.waitForLoadState('load');
  await page.getByText('실행 목적과 단계 구성이 다릅니다. 빌드·저장·배포 단계와 산출물 연결을 확인하세요.',{exact:true}).waitFor();
  console.log('PASS actual source/destination read, purpose rejection before run, shared flow and six viewport sizes');
  if(process.env.COMPANION_TEST_CREATE==='1'){
   await page.goto(base+'&tab=pipelines');
   const project=await page.locator('form[action="/builds/create"] [name=project] option').filter({hasText:'db-companion'}).getAttribute('value');
   const originalForm=page.locator('#create-build-stage');
   const repo=await originalForm.locator('[name=buildSource] option[data-provider=OCI]').filter({hasText:'build-config'}).getAttribute('value');
   const create=page.locator('form[action="/builds/create"]');
   await create.locator('[name=project]').selectOption(project);await create.locator('[name=name]').fill('companion-stage-check-'+Date.now());
   await create.getByRole('button',{name:'파이프라인 생성',exact:true}).click();await page.waitForLoadState('load');
   const created=new URL(page.url()).searchParams.get('pipeline');if(!created||created===evidence.pipeline)throw Error('New pipeline creation failed: '+page.url()+' '+await page.locator('main').innerText());
   fs.writeFileSync('.tools/build-stage-check.json',JSON.stringify({pipeline:created,project,source:repo},null,2));
   const form=page.locator('#create-build-stage');await form.locator('[name=name]').fill('Managed Build check');await form.locator('[name=buildSource]').selectOption(repo);await form.locator('[name=branch]').fill('main');
   await form.getByRole('button',{name:'빌드 단계 생성',exact:true}).click();await page.waitForLoadState('load');
   await page.getByText('OCI 설정 요청 완료 · 단계 상태와 저장값을 다시 조회하세요. 실행 중인 빌드에는 적용되지 않습니다.',{exact:true}).waitFor();
   await page.getByRole('heading',{name:'Managed Build check',exact:true}).waitFor();
   console.log('PASS real OCI pipeline and Managed Build creation: '+created);
  }
  if(errors.length)throw Error(errors.join('\n'));
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
