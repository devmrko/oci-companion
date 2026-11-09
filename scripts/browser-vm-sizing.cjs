const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit profile required');
 const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8092';
 const instance=JSON.parse(fs.readFileSync('.tools/vm-live-state.json')).instance;
 const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(90000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
  const url=base+'/environments/sizing?instance='+encodeURIComponent(instance);await page.goto(url);await page.getByRole('heading',{name:'VM 사양 변경',exact:true}).waitFor();
  const original={cpu:await page.locator('[name=cpu]').inputValue(),memory:await page.locator('[name=memory]').inputValue()};
  fs.writeFileSync('.tools/vm-sizing-original.json',JSON.stringify({instance,...original}));
  if(process.env.COMPANION_TEST_MUTATE==='vm-sizing')await page.locator('[name=cpu]').fill(String(Number(original.cpu)+1));await page.locator('[name=memory]').fill(String(Number(original.memory)+1));await page.getByRole('button',{name:'변경값 확인',exact:true}).click();await page.getByRole('heading',{name:'변경 확인',exact:true}).waitFor();if(await page.locator('.global-scope [name=region]').inputValue()!=='ap-seoul-1')throw Error('Scope missing on preview');
  for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);}
  await page.screenshot({path:'.tools/vm-sizing-preview.png',fullPage:true});
  if(['vm-memory','vm-sizing'].includes(process.env.COMPANION_TEST_MUTATE)) {
   await page.getByRole('button',{name:'OCI에 적용',exact:true}).click();await page.waitForLoadState('load');
   async function waitValue(value){for(let i=0;i<30;i++){await page.goto(url);if(await page.locator('[name=memory]').count() && Number(await page.locator('[name=memory]').inputValue())===value)return;await page.waitForTimeout(2000);}throw Error('Memory not ready: '+value);}
   await waitValue(Number(original.memory)+1);
   await page.screenshot({path:'.tools/vm-sizing-applied.png',fullPage:true});
   await page.locator('[name=cpu]').fill(original.cpu);await page.locator('[name=memory]').fill(original.memory);await page.getByRole('button',{name:'변경값 확인',exact:true}).click();await page.getByRole('button',{name:'OCI에 적용',exact:true}).click();await page.waitForLoadState('load');await waitValue(Number(original.memory));
  }
  await page.goto(url);const diskForm=page.locator('form').filter({has:page.locator('[name=size]')}).first();const diskSize=diskForm.locator('[name=size]');const next=await diskSize.getAttribute('min');await diskSize.fill(next);await diskForm.getByRole('button',{name:'확장값 확인',exact:true}).click();await page.getByRole('heading',{name:'변경 확인',exact:true}).waitFor();
  if(!await page.getByText('볼륨은 축소할 수 없습니다.',{exact:false}).count())throw Error('Missing disk warning');await page.screenshot({path:'.tools/vm-sizing-volume-preview.png',fullPage:true});
  if(errors.length)throw Error(errors.join('\n'));const result={original,memoryMutation:['vm-memory','vm-sizing'].includes(process.env.COMPANION_TEST_MUTATE),restored:true,volumePreviewOnly:true,errors};fs.writeFileSync(['vm-memory','vm-sizing'].includes(process.env.COMPANION_TEST_MUTATE)?'.tools/vm-sizing-mutation-ui.json':'.tools/vm-sizing-ui.json',JSON.stringify(result,null,2));console.log('PASS',JSON.stringify(result));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
