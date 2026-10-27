const {chromium}=require('../.tools/browser/node_modules/playwright');
const fs=require('fs');
async function until(check,timeout=120000){const end=Date.now()+timeout;while(Date.now()<end){if(await check())return;await new Promise(r=>setTimeout(r,300));}throw Error('Condition timeout');}
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT'||process.env.GITEA_WRITE_TEST!=='1')throw Error('Explicit DEFAULT and GITEA_WRITE_TEST=1 required');
 const target=JSON.parse(fs.readFileSync('.tools/gitea-validation.json','utf8'));
 if(target.name!=='oci-companion-build-validation')throw Error('Dedicated validation repository required');
 const browser=await chromium.launch({headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(120000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8091/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');
  const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption('ocid1.compartment.oc1..aaaaaaaam3amapwxz6nyciqzb2v2iwg66t22g47vabo5ilnghbsskooeop3q');await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
  await page.goto('http://127.0.0.1:8091/sources');
  if(!(await page.locator('main').innerText()).includes(target.name)){
   const form=page.locator('form').filter({has:page.locator('select[name=provider]')});await form.locator('[name=provider]').selectOption('GITEA');await form.locator('[name=name]').fill(target.name);await form.locator('[name=url]').fill(target.url);await form.locator('[name=branch]').fill(target.branch);await form.getByRole('button',{name:'등록',exact:true}).click();await page.waitForLoadState('load');
  }
  await page.goto('http://127.0.0.1:8091/builds/configure');
  const recipe=page.locator('#build-specification-form'),git=page.locator('#git-spec-form');
  for(const [tool,runtime] of [['maven','jdk21'],['gradle','jdk17'],['python','python311'],['node','node22']]){
   await recipe.locator('[name=tool]').selectOption(tool);await recipe.locator('[name=runtime]').selectOption(runtime);await recipe.getByRole('button',{name:'명세 미리보기'}).click();await page.locator('#build-specification-preview').waitFor({state:'visible'});const yaml=await page.locator('#build-specification-preview').innerText();if(!(yaml.includes('--version')||yaml.includes('java -version'))||!yaml.includes('test-reports.tar.gz'))throw Error('Missing runtime/report configuration');fs.writeFileSync('.tools/runtime-'+tool+'.yaml',yaml);
  }
  await recipe.locator('[name=tool]').selectOption('maven');await recipe.locator('[name=runtime]').selectOption('jdk21');await recipe.getByRole('button',{name:'명세 미리보기'}).click();await page.locator('#build-specification-preview').waitFor({state:'visible'});
  const option=page.locator('select[name=source] option').filter({hasText:target.name}).first();await page.locator('select[name=source]').selectOption(await option.getAttribute('value'));await git.locator('[name=username]').fill(target.user);await git.locator('[name=secret]').fill('env:GITEA');
  await git.getByRole('button',{name:'Git 내용 비교'}).click();await until(async()=>!(await git.locator('#git-spec-compare').isDisabled()));
  const compared=await page.locator('#git-spec-status').innerText();if(await git.locator('#git-spec-save').isDisabled())throw Error(compared);
  await git.getByRole('button',{name:'확인한 내용 커밋'}).click();await until(async()=>!(await git.locator('#git-spec-compare').isDisabled()));
  const committed=await page.locator('#git-spec-status').innerText();if(!committed.includes('커밋 완료')&&!committed.includes('변경 없음'))throw Error(committed);console.log(committed);
  await git.getByRole('button',{name:'Git 내용 비교'}).click();await until(async()=>!(await git.locator('#git-spec-compare').isDisabled()));
  if(await git.locator('#git-spec-save').isDisabled())throw Error('Comparison failed before conflict check');
  require('child_process').execFileSync('python3',['.tools/gitea-validation.py','concurrent'],{stdio:'pipe'});
  await git.getByRole('button',{name:'확인한 내용 커밋'}).click();await until(async()=>!(await git.locator('#git-spec-compare').isDisabled()));
  if(!(await page.locator('#git-spec-status').innerText()).includes('원격 브랜치 변경'))throw Error('Concurrent change not rejected');
  console.log('PASS real Gitea concurrent change rejected');
  // Editing the draft invalidates an old comparison ticket in the UI.
  await recipe.locator('[name=profiles]').fill('ci');if(!(await git.locator('#git-spec-save').isDisabled())||!(await git.locator('#git-spec-compare').isDisabled()))throw Error('Stale draft could be committed');
  for(const width of [320,390,768,1024,1440,2048]){await page.setViewportSize({width,height:1000});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))throw Error('Configure overflow '+width);}
  await page.setViewportSize({width:1440,height:1000});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:'.tools/build-completion-configure.png',fullPage:true});
  await page.goto('http://127.0.0.1:8091/builds/functions?view=history&application=ocid1.fnapp.oc1.ap-seoul-1.amaaaaaavsea7yiai3wahxcr366lrb2n54jpx5he4ep5m7t35phg36eljbbq&function=ocid1.fnfunc.oc1.ap-seoul-1.amaaaaaavsea7yiatgguo5apbfw4bh2jem2d6ptcnefwyuttidqf7imhdv3q&run=85e5762a-8b6d-462c-ab26-dd2f141aac88');
  await page.getByRole('button',{name:'테스트 보고서 조회'}).click();await until(async()=>!(await page.locator('#function-reports-refresh').isDisabled()));const summary=await page.locator('#function-reports-status').innerText();console.log('FUNCTION_REPORT',summary);if(!/테스트 \d+ · 실패 0 · 오류 0/.test(summary))throw Error(summary);
  await page.locator('#function-reports-status').scrollIntoViewIfNeeded();await page.screenshot({path:'.tools/build-completion-report.png'});
  if(errors.length)throw Error(errors.join('\n'));
  console.log('PASS real Gitea commit, four runtime previews, stale draft invalidation, six viewport widths, real Object Storage report aggregation.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
