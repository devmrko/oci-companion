const {chromium}=require('../.tools/browser/node_modules/playwright');
(async()=>{
 const base=process.env.COMPANION_TEST_URL,profile=process.env.COMPANION_TEST_PROFILE;
 if(!base||!profile)throw Error('Explicit URL and profile required');
 const browser=await chromium.launch({headless:true});const page=await browser.newPage();page.setDefaultTimeout(90000);
 try{
  await page.goto(base+'/login');await page.locator('[name=profile]').selectOption(profile);
  await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  const scope=page.locator('.global-scope');
  await scope.locator('[name=region]').selectOption(process.env.COMPANION_TEST_REGION);
  await scope.locator('[name=compartment]').selectOption({label:process.env.COMPANION_TEST_COMPARTMENT});
  await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
  await page.goto(base+'/agents/connections/oke');const tools=page.getByRole('region',{name:'서버 도구 확인'});
  await tools.getByText('kubectl · '+(process.env.COMPANION_TEST_MISSING==='1'?'설정 필요':'실행 파일 확인'),{exact:true}).waitFor();
  await tools.getByText('OCI CLI · 실행 파일 확인',{exact:true}).waitFor();
  if(await page.locator('#oke-connection-form [name=cluster] option').count()<2)throw Error('Native clusters missing');
  for(const width of [390,768,1440]){
   await page.setViewportSize({width,height:1000});
   if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);
   await page.screenshot({path:'.tools/oke-tools-'+width+'.png',fullPage:true});
  }
  console.log('PASS native profile, cluster list, tool readiness, responsive widths');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
