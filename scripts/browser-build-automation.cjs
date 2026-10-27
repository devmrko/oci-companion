const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
async function until(check,timeout=240000){const end=Date.now()+timeout;while(Date.now()<end){if(await check())return;await new Promise(r=>setTimeout(r,500));}throw Error('Condition timeout');}
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT profile required');
 const browser=await chromium.launch({headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(120000);
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption('ocid1.compartment.oc1..aaaaaaaam3amapwxz6nyciqzb2v2iwg66t22g47vabo5ilnghbsskooeop3q');await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
  await page.goto('http://127.0.0.1:8091/builds/configure');
  await page.locator('#build-specification-form').waitFor();
  const recipe=page.locator('#build-specification-form');
  await recipe.locator('[name=launcher]').selectOption('system');
  await recipe.locator('[name=profiles]').fill('quality');
  await recipe.locator('[name=variables]').fill('APP_ENV=ci');
  await recipe.locator('[name=vaultVariables]').fill('TOKEN=ocid1.vaultsecret.oc1.test');
  await recipe.getByRole('button',{name:'명세 미리보기',exact:true}).click();
  await page.locator('#build-specification-preview').waitFor({state:'visible'});
  const preview=await page.locator('#build-specification-preview').innerText();
  if(!preview.includes("mvn -B 'verify' '-Pquality'") || !preview.includes('vaultVariables'))throw Error('Recipe not applied');
  for(const tool of ['gradle','python','node']){await recipe.locator('[name=tool]').selectOption(tool);await recipe.getByRole('button',{name:'명세 미리보기',exact:true}).click();await page.locator('#build-specification-preview').waitFor({state:'visible'});}
  for(const width of [320,390,768,1024,1440,2048]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Configure overflow '+width);}
  await page.setViewportSize({width:1440,height:1000});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:'.tools/build-configure.png',fullPage:true});
  await page.goto('http://127.0.0.1:8091/builds/automation');
  const pipeline=page.locator('[name=pipeline] option').filter({hasText:'db-companion-maven'}).first();await page.locator('[name=pipeline]').selectOption(await pipeline.getAttribute('value'));await page.getByRole('button',{name:'조회',exact:true}).click();await page.waitForLoadState('load');
  await page.getByText('예약 추가',{exact:true}).click();
  await page.locator('#schedule-preview').click();await until(async()=>(await page.locator('#schedule-preview-result').innerText()).includes('서울'));
  const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'실행 요청 Function 템플릿 ZIP'}).click();const download=await downloadPromise;await download.saveAs('.tools/devops-dispatch.zip');
  await page.getByText('트리거 추가',{exact:true}).click();
  if(await page.locator('[name=source] option').count()<2)throw Error('Pipeline native sources missing');
  for(const width of [320,390,768,1024,1440,2048]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Automation overflow '+width);}
  await page.setViewportSize({width:1440,height:1000});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:'.tools/build-automation.png',fullPage:true});
  console.log('Actual automation notices:',await page.locator('.notice').allTextContents());
  await page.goto('http://127.0.0.1:8091/builds/tasks?view=new');
  await page.locator('[name=name]').fill('UX 초안 유지 확인');
  const source=page.locator('[name=sourceId] option').filter({hasText:'GITHUB · db-companion'}).first();await page.locator('[name=sourceId]').selectOption(await source.getAttribute('value'));
  await until(async()=>/^[a-f0-9]{40}$/.test(await page.locator('[name=commit]').inputValue()));
  await page.getByRole('button',{name:'다음',exact:true}).click();
  const popupPromise=page.waitForEvent('popup');await page.getByRole('link',{name:'새 파이프라인 구성 ↗'}).click();const popup=await popupPromise;await popup.waitForLoadState('load');
  if(!(await popup.locator('main').innerText()).includes('선택 소스: db-companion'))throw Error('Source lost on configure handoff');
  await popup.close();if(await page.locator('[name=name]').inputValue()!=='UX 초안 유지 확인')throw Error('Draft lost after configure');
  if(errors.length)throw Error(errors.join('\n'));
  console.log('PASS real DEFAULT pipeline/trigger/schedule/dispatcher reads, four recipe previews, cron preview, ZIP download, six widths. No cloud mutation or build.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
