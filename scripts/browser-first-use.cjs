const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
(async()=>{
 const base=process.env.COMPANION_TEST_URL;if(!base||!process.env.COMPANION_TEST_PROFILE||!process.env.COMPANION_TEST_REGION||!process.env.COMPANION_TEST_COMPARTMENT||process.env.COMPANION_TEST_LOCAL_WRITE!=='1')throw Error('Set test URL, profile, region, compartment label and COMPANION_TEST_LOCAL_WRITE=1; use a fresh test data directory');
 const browser=await chromium.launch({headless:true});const page=await browser.newPage();page.setDefaultTimeout(90000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
 await page.goto(base+'/login');await page.locator('[name=profile]').selectOption(process.env.COMPANION_TEST_PROFILE);await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
 const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption(process.env.COMPANION_TEST_REGION);await scope.locator('[name=compartment]').selectOption({label:process.env.COMPANION_TEST_COMPARTMENT});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 await page.getByRole('link',{name:'처음 사용 · 시작 순서'}).click();await page.locator('#start').waitFor();
 const links=await page.locator('#start a').evaluateAll(as=>as.map(a=>a.getAttribute('href')).filter(x=>x.startsWith('/')));
 for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Guide overflow '+width);}
 await page.screenshot({path:'.tools/first-use-guide.png',fullPage:true});
 for(const route of [...new Set(links)]){const r=await page.goto(base+route);if(r.status()!==200)throw Error(route+' HTTP '+r.status());}
 await page.goto(base+'/sources');let external=page.locator('section').filter({has:page.getByRole('heading',{name:'외부 Git',exact:true})});
 await external.locator('[name=name]').fill('first-use-example');await external.locator('[name=url]').fill('http://github.com/devmrko/db-companion');
 const response=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/sources');await external.getByRole('button',{name:'등록',exact:true}).click();if((await response).status()!==302)throw Error('Expected source form redirect');await page.getByRole('alert').filter({hasText:'입력값을 확인'}).waitFor();
 const rejected=await page.goto(base+'/deployments/oke/configure/manifest.yaml?ticket=expired-first-use-ticket');if(rejected.status()!==409)throw Error('Expected expired preview rejection');
 await page.getByRole('heading',{name:'요청 결과 확인'}).waitFor();if(!await page.locator('.global-scope').count())throw Error('Navigation lost');await page.getByRole('link',{name:'권한 설정 안내'}).waitFor();
 for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Error overflow '+width);}
 await page.screenshot({path:'.tools/first-use-error.png',fullPage:true});await page.getByRole('link',{name:'배포 이력',exact:true}).click();await page.goto(base+'/sources');
 external=page.locator('section').filter({has:page.getByRole('heading',{name:'외부 Git',exact:true})});await external.locator('[name=name]').fill('first-use-example');await external.locator('[name=url]').fill('https://github.com/devmrko/db-companion');await external.getByRole('button',{name:'등록',exact:true}).click();await page.getByText('first-use-example',{exact:true}).waitFor();
 await page.goto(base+'/builds/tasks');await page.getByRole('link',{name:'처음 사용 · 준비 순서'}).waitFor();await page.getByRole('link',{name:'첫 빌드 작업 만들기'}).click();await page.locator('#build-task-form').waitFor();
 if(!await page.locator('option').filter({hasText:'first-use-example'}).count())throw Error('Registered source absent from wizard');
 if(errors.length)throw Error(errors.join('\n'));fs.writeFileSync('.tools/first-use-result.json',JSON.stringify({passed:true,linksChecked:new Set(links).size,sourceRegistered:true,errorRecovery:true,errors,cloudMutations:false},null,2));console.log('PASS first-use links, layout, invalid source recovery, source registration and wizard');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
