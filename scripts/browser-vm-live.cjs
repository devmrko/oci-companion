const {chromium}=require('../.tools/browser/node_modules/playwright');const fs=require('fs');
(async()=>{
 if(process.env.COMPANION_TEST_PROFILE!=='DEFAULT')throw Error('Explicit DEFAULT required');
 const mode=process.argv[2]||'status';const base=process.env.COMPANION_TEST_URL||'http://127.0.0.1:8092';const file='.tools/vm-live-state.json';const s=JSON.parse(fs.readFileSync(file));const source=JSON.parse(fs.readFileSync('.tools/ui-onboarding-state.json'));const save=()=>fs.writeFileSync(file,JSON.stringify(s,null,2));
 const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(90000);
 const install='release="/var/tmp/companion-hello/releases/$RELEASE_VERSION"\nmkdir -p "$release"\ncp -a "$DEPLOY_DIR/." "$release/"\npython3 -m py_compile "$release/app.py"\ntest "$(cat "$release/version.txt")" = "$RELEASE_VERSION"\nln -sfn "$release" /var/tmp/companion-hello/current';
 const start=`python3 - <<'PY'
from pathlib import Path
import os, signal, subprocess, time
root=Path('/var/tmp/companion-hello')
pidfile=root/'app.pid'
if pidfile.exists():
    pid=int(pidfile.read_text())
    try:
        command=Path('/proc/%s/cmdline'%pid).read_bytes()
        if b'/var/tmp/companion-hello/' not in command:
            raise RuntimeError('PID belongs to another process')
        os.kill(pid,signal.SIGTERM)
        time.sleep(1)
    except FileNotFoundError:
        pass
    except ProcessLookupError:
        pass
with open(root/'app.log','ab') as log:
    p=subprocess.Popen(['python3',str(root/'current/app.py')],stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,start_new_session=True,close_fds=True)
pidfile.write_text(str(p.pid))
PY`;
 const health=`curl --fail --silent --retry 10 --retry-connrefused --retry-delay 1 http://127.0.0.1:8080/hello | python3 -c 'import json,os,sys; d=json.load(sys.stdin); print(json.dumps(d)); assert d["version"]==os.environ["RELEASE_VERSION"]'`;
 async function visit(path){const r=await page.goto(base+path);if(!r.ok())throw Error(path+' HTTP '+r.status());}
 try{
 await visit('/login');await page.locator('[name=profile]').selectOption('DEFAULT');await page.getByRole('button',{name:'연결',exact:true}).click();await page.waitForLoadState('load');const scope=page.locator('.global-scope');await scope.locator('[name=region]').selectOption('ap-seoul-1');await scope.locator('[name=compartment]').selectOption({label:'Joungmin.Ko'});await scope.getByRole('button',{name:'적용',exact:true}).click();await page.waitForLoadState('load');
 if(mode==='prepare'){
  if(!s.environment){await visit('/environments?tab=new');const form=page.locator('form[action="/environments"][method=post]');await form.locator('[name=project]').selectOption(source.project);await form.locator('[name=name]').fill('companion-live-vm-20261109');await form.locator('[name=description]').fill('HTTP v1/v2 배포 및 이전 버전 복구 검증');await form.locator('[name=instances][value="'+s.instance+'"]').check();await form.getByRole('button',{name:'OCI 환경 생성',exact:true}).click();await page.waitForURL(/environment=/);s.environment=new URL(page.url()).searchParams.get('environment');save();}
  s.pipelines=s.pipelines||{};
  for(const version of ['v1','v2']){if(s.pipelines[version])continue;await visit('/deployments/configure?environment='+s.environment+'&repository='+s.repository);await page.locator('[name=artifact]').selectOption(s.artifacts[version]);await page.locator('[name=name]').fill('companion-live-'+version+'-lf-20261109');await page.locator('[name=description]').fill('격리된 HTTP 예제 '+version+' · native OCI 배포');await page.locator('[name=destination]').fill('/var/tmp/companion-hello/staging');await page.locator('[name=install]').fill(install);await page.locator('[name=start]').fill(start);await page.locator('[name=health]').fill(health);await page.getByText('환경변수·실패 시 명령',{exact:true}).click();await page.locator('[name=variables]').fill('RELEASE_VERSION='+version);await page.getByRole('button',{name:'설정 검토',exact:true}).click();await page.getByRole('heading',{name:'생성 전 확인',exact:true}).waitFor();await page.getByRole('button',{name:'확인한 설정으로 생성',exact:true}).click();await page.getByRole('heading',{name:'파이프라인 생성 완료',exact:true}).waitFor();s.pipelines[version]=await page.locator('.resource-details dd').first().innerText();save();console.log('Created via UI',version);}
 } else if(mode.startsWith('execute:')){
  const version=mode.split(':')[1];await visit('/deployments/runs?pipeline='+s.pipelines[version]);await page.getByRole('button',{name:'확인한 배포 실행',exact:true}).click();await page.waitForURL(/run=/);const run=new URL(page.url()).searchParams.get('run');s.runs=s.runs||[];s.runs.push({version,run,pipeline:s.pipelines[version]});save();console.log('Executed via UI',version,run);
 } else {
  const r=s.runs.at(-1);await visit('/deployments/runs?pipeline='+r.pipeline+'&run='+r.run);const panel=page.locator('section').filter({has:page.getByRole('heading',{name:'OCI 배포 상태',exact:true})});r.state=await panel.locator('strong').innerText();const logs=page.locator('section').filter({has:page.getByRole('heading',{name:'배포 로그',exact:true})});r.logLines=await logs.locator('tbody tr').count();const text=await logs.innerText();fs.writeFileSync('.tools/vm-live-last-log.txt',text);r.httpVersionInLogs=text.includes('"version": "'+r.version+'"');save();await page.screenshot({path:'.tools/vm-live-'+s.runs.length+'-'+r.state+'.png',fullPage:true});console.log(JSON.stringify(r));
 }
 }catch(e){await page.screenshot({path:'.tools/vm-live-ui-error.png',fullPage:true}).catch(()=>{});throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
