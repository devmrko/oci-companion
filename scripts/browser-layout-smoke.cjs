const { chromium }=require('../.tools/browser/node_modules/playwright');
const assert=require('node:assert/strict');
(async()=>{if(!process.env.COMPANION_TEST_PROFILE)throw Error('Set COMPANION_TEST_PROFILE for actual OCI read-only checks');const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8091';const browser=await chromium.launch({headless:true});try{
 const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(base+'/login');await page.locator('[name=profile]').selectOption(process.env.COMPANION_TEST_PROFILE);await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
 for(const path of ['/','/sources','/builds','/guides']){
  await page.goto(base+path);await page.waitForLoadState('load');
  for(const width of [320,390,768,1024,1440,2048]){
   await page.setViewportSize({width,height:950});await page.waitForTimeout(100);
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false,path+' overflow '+width);
   const boxes=await page.locator('.global-scope select,.global-scope > button,.connection-summary').evaluateAll(es=>es.map(e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height}}));
   assert.ok(boxes.every(r=>r.w>=40&&r.x>=0&&r.x+r.w<=width+1),path+' controls '+width);
   for(let i=0;i<boxes.length;i++)for(let j=i+1;j<boxes.length;j++){const a=boxes[i],b=boxes[j];assert.ok(!(a.x<b.x+b.w-1&&a.x+a.w>b.x+1&&a.y<b.y+b.h-1&&a.y+a.h>b.y+1),path+' overlap '+width);}
   if(width>1100){assert.ok((await page.locator('.global-scope').boundingBox()).width<=620,'bounded global scope');}
   if(width>800){
    const head=await page.locator('.app-topbar').boundingBox(),brand=await page.locator('.side-brand').boundingBox();assert.ok(Math.abs(head.height-brand.height)<1,'brand/header height');
    const controls=await page.locator('.global-scope .ui-control,.global-scope>.ui-button').evaluateAll(es=>es.map(e=>{const r=e.getBoundingClientRect();return {h:r.height,b:r.bottom}}));
    assert.ok(controls.every(r=>Math.abs(r.h-34)<1&&Math.abs(r.b-controls[0].b)<1),'toolbar alignment');
   }
   const heights=await page.locator('.scope-bar .ui-control,.scope-bar .ui-button,.list-filters .ui-control,.list-filters .ui-button').evaluateAll(es=>es.map(e=>e.getBoundingClientRect().height));assert.ok(heights.every(h=>Math.abs(h-42)<1),'form control height');
   if(width===2048)await page.screenshot({path:'.tools/wide-'+path.replaceAll('/','')+'.png',fullPage:true});
   if(width<=800){
    await page.locator('#sidebar-toggle').click();
    const nav=await page.locator('.side-nav').evaluate(e=>({display:getComputedStyle(e).display,overflow:e.scrollWidth>e.clientWidth+1}));assert.equal(nav.display,'block');assert.equal(nav.overflow,false);
    const header=await page.locator('.app-topbar').boundingBox(),side=await page.locator('.app-sidebar').boundingBox();assert.ok(side.y>=header.y+header.height-1);
    await page.screenshot({path:'.tools/css-'+path.replaceAll('/','')+'-'+width+'.png'});await page.locator('#sidebar-toggle').click();
   }
  }
 }
 assert.deepEqual(errors,[]);console.log('PASS: 4 pages x 6 widths, control overlap, vertical mobile menu, no overflow');
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exit(1)});
