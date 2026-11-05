const {chromium}=require('../.tools/browser/node_modules/playwright');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT profile required');
 const browser=await chromium.launch({headless:true});try{
 const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(120000);
 await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
 const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption('ocid1.compartment.oc1..aaaaaaaam3amapwxz6nyciqzb2v2iwg66t22g47vabo5ilnghbsskooeop3q');await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');


 for(const tab of ['pipelines','execute','projects','topics','registry']) {
  await page.goto('http://127.0.0.1:8091/builds?tab='+tab);
  if(await page.locator('h1').innerText()!=='빌드')throw Error('Page failed '+tab);
  const buttons=await page.locator('.ui-button:visible').evaluateAll(es=>es.map(e=>({text:e.textContent.trim(),height:e.getBoundingClientRect().height})).filter(e=>Math.abs(e.height-34)>1));
  if(buttons.length)throw Error(JSON.stringify(buttons));
  if(tab==='pipelines') {
   const select=page.locator('#pipeline-picker select[name=pipeline]');
   const expected=await select.locator('option:checked').getAttribute('data-description');
   const actual=await page.locator('#'+await select.getAttribute('aria-describedby')).textContent();
   if(actual!==(expected?.trim()||'설명 없음'))throw Error('Description mismatch');
   console.log('PASS actual selected pipeline description');
   const aligned=await page.locator('#pipeline-picker').evaluate(form=>{
     const controls=[...form.querySelectorAll('select,button')].map(e=>e.getBoundingClientRect().bottom);
     return Math.max(...controls)-Math.min(...controls)<2;
   });
   if(!aligned)throw Error('Selector alignment');
   for(const width of [390,768,1440]) {
    await page.setViewportSize({width,height:1000});
    if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Overflow '+width);
   }
   await page.screenshot({path:'.tools/compact-buttons-descriptions.png'});
  }
  console.log('PASS',tab,'34px buttons');
 }
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
