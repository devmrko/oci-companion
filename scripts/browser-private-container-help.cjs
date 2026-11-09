const {chromium}=require('../.tools/browser/node_modules/playwright'),fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT required');
 const target=JSON.parse(fs.readFileSync(process.env.COMPANION_CONTAINER_TARGET||'.tools/private-ci-created.json'));if(target.name!=='companion-ci-flow-20261110')throw Error('Dedicated validation instance required');
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8091',browser=await chromium.launch({headless:true}),page=await browser.newPage();page.setDefaultTimeout(90000);
 try{
  await page.goto(base+'/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
  await page.goto(base+'/environments/containers?instance='+target.id);await page.locator('.task-selection-summary').filter({hasText:target.name+' · FAILED'}).waitFor();await page.getByText('A container\'s image could not be pulled because the image does not exist or requires authorization.',{exact:false}).waitFor();
  await page.getByText('사설 OCIR 인증 확인 ?',{exact:true}).click();const help=page.locator('details').filter({has:page.locator('summary').filter({hasText:'사설 OCIR 인증 확인 ?'})});
  if(!(await help.innerText()).includes('한도 초과'))throw Error('Quota help missing');if(!(await help.innerText()).includes('target.repo.name='))throw Error('Repository condition missing');
  for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});await help.scrollIntoViewIfNeeded();if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);await page.screenshot({path:'.tools/private-ci-help-'+width+'.png'});}
  if(process.env.COMPANION_TEST_MUTATE==='private-ci-cleanup'){await page.getByRole('button',{name:'삭제',exact:true}).click();await page.locator('[name=confirmation]').fill(target.name);await page.getByRole('button',{name:'삭제 요청',exact:true}).click();await page.getByRole('alert').filter({hasText:'삭제 요청 접수'}).waitFor();console.log('Deletion requested through UI');}
  console.log('PASS actual failed CI, scoped permission help and responsive layout');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
