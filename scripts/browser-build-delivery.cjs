const {chromium}=require('../.tools/browser/node_modules/playwright'),fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_SETUP!=='1'||!process.env.COMPANION_TEST_PIPELINE)throw Error('Explicit COMPANION_TEST_SETUP=1 and db-companion pipeline required');
 const browser=await chromium.launch({headless:true});
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(60000);
  await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');
  await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  const pipeline=process.env.COMPANION_TEST_PIPELINE,base='http://127.0.0.1:8091/builds?pipeline='+encodeURIComponent(pipeline);
  await page.goto(base+'&tab=pipelines');await page.waitForLoadState('load');
  if(!(await page.locator('.pipeline-definition').innerText()).includes('db-companion-maven'))throw Error('Only db-companion example supported');
  let repo=page.locator('select[name=repository] option').filter({hasText:'db-companion-builds'});
  if(!await repo.count()){
   const section=page.locator('details').filter({has:page.locator('summary').filter({hasText:'1. Artifact Registry 저장소 생성'})}).first();
   await section.locator('summary').click();await section.locator('[name=name]').fill('db-companion-builds');
   await section.getByRole('button',{name:'저장소 생성',exact:true}).click();await page.waitForLoadState('load');
   console.log('Repository create submitted');
   repo=page.locator('select[name=repository] option').filter({hasText:'db-companion-builds'});await repo.waitFor({state:'attached'});
  }
  const repoId=await repo.getAttribute('value');
  if(process.env.COMPANION_TEST_EDIT==='1'){
  // Verify real update path without altering the existing stage values.
  const edit=page.locator('details').filter({has:page.locator('summary').filter({hasText:'빌드 단계 수정'})}).first();
  await edit.locator('summary').click();await edit.getByRole('button',{name:'빌드 단계 저장'}).click();await page.waitForLoadState('load');
  await page.getByText('OCI 설정 저장 완료 · 실행 중인 빌드에는 적용되지 않습니다.',{exact:true}).waitFor();
  }
  let artifact=page.locator('select[name=artifact] option').filter({hasText:'db-companion-jar-delivery'});
  if(!await artifact.count()){
   const section=page.locator('details').filter({has:page.locator('summary').filter({hasText:'2. 산출물 저장 위치 등록'})}).first();
   await section.locator('summary').click();await section.locator('[name=name]').fill('db-companion-jar-delivery');
   await section.locator('[name=type]').selectOption('BINARY');await section.locator('[name=repository]').selectOption(repoId);
   await section.locator('[name=path]').fill('db-companion/app.jar');await section.locator('[name=version]').fill('${COMPANION_VERSION}');
   await section.getByRole('button',{name:'저장 위치 등록'}).click();await page.waitForLoadState('load');
   console.log('Artifact reference create submitted');
   artifact=page.locator('select[name=artifact] option').filter({hasText:'db-companion-jar-delivery'});await artifact.waitFor({state:'attached'});
  }
  const artifactId=await artifact.getAttribute('value');
  if(!(await page.locator('.pipeline-definition').innerText()).includes('Registry · db-companion-jar')){
   const section=page.locator('details').filter({has:page.locator('summary').filter({hasText:'3. Registry 전달 단계 추가'})}).first();
   await section.locator('[name=stage]').selectOption({index:1});await section.locator('[name=output]').fill('db-companion-jar');
   await section.locator('[name=artifact]').selectOption(artifactId);await section.getByRole('button',{name:'전달 단계 추가'}).click();await page.waitForLoadState('load');
   console.log('Delivery stage create submitted');
  }
  if(!(await page.locator('.pipeline-definition').innerText()).includes('Registry · db-companion-jar'))throw Error('Delivery stage missing after create');
  await page.goto(base+'&tab=execute');await page.waitForLoadState('load');
  const advanced=page.locator('details').filter({has:page.locator('summary').filter({hasText:'OCI 기본 파라미터 관리'})}).first();
  await advanced.locator('summary').click();
  let row=advanced.locator('tr').filter({has:page.locator('[name=parameterName][value=COMPANION_VERSION]')});
  if(!await row.count()){await advanced.getByRole('button',{name:'파라미터 추가'}).click();row=advanced.locator('#parameter-rows tr').last();await row.locator('[name=parameterName]').fill('COMPANION_VERSION');}
  const version='gui-'+Date.now();
  await row.locator('[name=parameterValue]').fill(version);await row.locator('[name=parameterDescription]').fill('Registry version; use a new value for each build');
  await advanced.getByRole('button',{name:'OCI에 저장'}).click();await page.waitForLoadState('load');
  await page.locator('[name=runName]').fill('db-companion-registry-'+Date.now());
  await page.getByRole('button',{name:'실행 설정 확인',exact:true}).click();
  await page.locator('#build-review').getByRole('button',{name:'빌드 실행',exact:true}).click();await page.waitForLoadState('load');
  const run=new URL(page.url()).searchParams.get('run');if(!run)throw Error('Run submission failed: '+await page.locator('main').innerText());
  if(fs.existsSync('.tools/build-flow-run.json'))fs.copyFileSync('.tools/build-flow-run.json','.tools/build-flow-run-previous.json');
  fs.writeFileSync('.tools/build-flow-run.json',JSON.stringify({run,pipeline,repository:repoId,artifact:artifactId,version,url:page.url()},null,2));
  console.log('PASS real repository/reference/delivery stage/update/defaults/run submission; completion pending');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
