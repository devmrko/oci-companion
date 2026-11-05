const {chromium}=require('../.tools/browser/node_modules/playwright'),fs=require('fs');
(async()=>{
 const evidence=JSON.parse(fs.readFileSync('.tools/build-flow-run.json','utf8'));
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('DEFAULT explicitly required');
 const browser=await chromium.launch({headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(30000);
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  const base='http://127.0.0.1:8091/builds?tab=registry&pipeline='+encodeURIComponent(evidence.pipeline);
  await page.goto(base+'&repository='+encodeURIComponent(evidence.repository));
  await page.getByRole('heading',{name:'db-companion-builds',exact:true}).waitFor();
  const artifact=page.locator('tr').filter({hasText:evidence.version});
  if(await artifact.count()!==1||!(await artifact.innerText()).includes('61111725'))throw Error('Actual artifact/version/size missing');
  await artifact.locator('summary').click();
  if(!(await artifact.innerText()).includes(evidence.storedArtifact.sha256))throw Error('Actual SHA256 missing');
  await page.locator('#permissions > summary').click();
  for(const width of [1440,390,320]) {
   await page.setViewportSize({width,height:1000});
   if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Registry overflow '+width);
  }
  await page.screenshot({path:'.tools/registry-file.png',fullPage:true});
  await page.getByRole('link',{name:'빌드 저장소로 선택'}).click();await page.waitForLoadState('load');
  if(await page.locator('#artifact-repository').inputValue()!==evidence.repository)throw Error('Selected repository lost');
  if(await page.locator('#artifact-type').inputValue()!=='BINARY')throw Error('Wrong artifact type');
  if(fs.existsSync('.tools/registry-image-evidence.json')) {
   const image=JSON.parse(fs.readFileSync('.tools/registry-image-evidence.json','utf8'));
   await page.goto(base+'&kind=CONTAINER&repository='+encodeURIComponent(image.repository));
   const row=page.locator('tr').filter({hasText:image.digest});
   if(await row.count()!==1)throw Error('Actual container digest missing');
   await row.locator('summary').click();
   if(!(await row.innerText()).includes(image.image))throw Error('Actual image ID missing');
   console.log('PASS existing OCI container image ID/digest');
  }
  for(const kind of ['GENERIC','CONTAINER']) {
   await page.goto(base+'&kind='+kind);
   if(await page.getByText('목록 조회 실패 · 연결·범위·권한을 확인하세요.',{exact:true}).count())throw Error(kind+' list unavailable');
   if(process.env.COMPANION_TEST_CREATE!=='1')continue;
   const name=kind==='GENERIC'?'companion-registry-check':'companion/registry-check';
   const existing=page.getByRole('link',{name,exact:true});
   if(await existing.count())await existing.click();
   else {
    const section=page.locator('details').filter({has:page.locator('summary').filter({hasText:'저장소 생성'})}).first();
    await section.locator('summary').click();await section.locator('[name=name]').fill(name);await section.locator('[name=description]').fill('OCI Companion Registry validation');
    await section.getByRole('button',{name:'저장소 생성',exact:true}).click();await page.waitForLoadState('load');
    await page.getByText('저장소 생성 완료',{exact:true}).waitFor();
   }
   await page.getByRole('heading',{name,exact:true}).waitFor();
   const id=new URL(page.url()).searchParams.get('repository');
   const edit=page.locator('details').filter({has:page.locator('summary').filter({hasText:'저장소 설정'})}).first();
   await edit.locator('summary').click();await edit.locator('[name=description]').fill('OCI Companion Registry validation — updated');
   await edit.getByRole('button',{name:'설정 저장',exact:true}).click();await page.waitForLoadState('load');
   await page.getByText('저장소 설정 저장 완료',{exact:true}).waitFor();
   if(await page.locator('textarea[name=description]').first().inputValue()!=='OCI Companion Registry validation — updated')throw Error('Description was not saved');
   await page.goto(base+'&kind='+kind+'&repository='+encodeURIComponent(id));
   await page.getByRole('link',{name:'빌드 저장소로 선택'}).click();await page.waitForLoadState('load');
   if(await page.locator('#artifact-repository').inputValue()!==id)throw Error(kind+' selection lost');
   if(await page.locator('#artifact-type').inputValue()!==(kind==='GENERIC'?'BINARY':'DOCKER_IMAGE'))throw Error(kind+' type mismatch');
   if(kind==='CONTAINER' && !await page.locator('#artifact-path').isDisabled())throw Error('Image path should be derived');
   console.log('PASS actual '+kind+' create/reuse, metadata update and build selection');
  }
  if(errors.length)throw Error(errors.join('\n'));
  console.log('PASS actual Registry lists, stored JAR/version/hash, selection, IAM and 1440/390/320 layout');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
