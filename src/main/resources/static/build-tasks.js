'use strict';
(() => {
  const form = document.getElementById('build-task-form');
  if (!form) return;
  const field = name => form.elements.namedItem(name);
  const steps = [...form.querySelectorAll('.task-step')];
  let step = 0, loading = false;
  const text = (id, value) => { document.getElementById(id).textContent = value; document.getElementById(id).title = value; };
  const selected = name => field(name).selectedOptions[0]?.textContent || '미선택';
  const bucket = () => field('function').selectedOptions[0]?.dataset.bucket || '';
  function summary() {
    const fn = field('runner').value === 'FUNCTIONS';
    const source = fn ? (field('source').value || '미선택') + (field('commit').value ? ' · '+field('commit').value.slice(0,12) : '') : field('pipeline').value ? selected('pipeline')+' · OCI 소스 설정' : '파이프라인 미선택';
    const build = fn ? 'Maven · '+field('goal').value+(field('profiles').value ? ' · '+field('profiles').value : '') : 'OCI 파이프라인 설정';
    const environment = fn ? field('function').value ? 'Functions · '+selected('function') : 'Functions · 빌더 미선택' : field('pipeline').value ? 'OCI DevOps · '+selected('pipeline') : 'OCI DevOps · 미선택';
    const storage = fn ? bucket() ? bucket()+(field('artifact').value ? ' · '+field('artifact').value : ' · 로그·보고서') : '빌더 선택 후 확인' : 'OCI 전달 단계 설정 · 상세에서 확인';
    text('task-summary-name',field('name').value || '이름 미입력');text('task-summary-source',source);text('task-summary-build',build);text('task-summary-environment',environment);text('task-summary-storage',storage);
    text('task-bucket',bucket() ? '결과 버킷: '+bucket() : '결과 버킷은 선택한 빌더에서 가져옵니다.');
    const review = document.getElementById('task-review');review.replaceChildren();
    for(const [name,value] of [['작업',field('name').value],['소스',source],['빌드',build],['실행 환경',environment],['저장 위치',storage]]) {
      const dt=document.createElement('dt'), dd=document.createElement('dd');dt.textContent=name;dd.textContent=value;review.append(dt,dd);
    }
    form.querySelectorAll('.task-pipeline-link').forEach(link => {const url=new URL('/builds',location.origin);url.searchParams.set('tab','pipelines');if(field('pipeline').value)url.searchParams.set('pipeline',field('pipeline').value);link.href=url;});
  }
  function mode() {
    form.querySelectorAll('[data-runner]').forEach(box => {
      const enabled=box.dataset.runner===field('runner').value;box.hidden=!enabled;
      box.querySelectorAll('input,select,button').forEach(control=>control.disabled=!enabled);
    });summary();
  }
  function show(index) {
    step=index;steps.forEach((box,i)=>box.hidden=i!==step);
    form.querySelectorAll('[data-step-label]').forEach((label,i)=>{label.classList.toggle('is-current',i===step);if(i===step)label.setAttribute('aria-current','step');else label.removeAttribute('aria-current');});
    document.getElementById('task-previous').disabled=step===0;
    document.getElementById('task-next').hidden=step===3;
    text('task-step-count',(step+1)+' / 4');summary();
  }
  function valid(box) {for(const control of box.querySelectorAll('input,select'))if(!control.disabled && !control.checkValidity()){control.reportValidity();return false;}return true;}
  document.getElementById('task-next').addEventListener('click',()=>{if(valid(steps[step]))show(step+1);});
  document.getElementById('task-previous').addEventListener('click',()=>show(Math.max(0,step-1)));
  form.querySelectorAll('[data-jump]').forEach(button=>button.addEventListener('click',()=>show(Number(button.dataset.jump))));
  document.addEventListener('keydown',event=>{if(event.key==='Escape')form.querySelectorAll('.field-help[open]').forEach(help=>help.open=false);});
  field('runner').addEventListener('change',mode);form.addEventListener('input',summary);form.addEventListener('change',summary);
  document.getElementById('task-source-choice').addEventListener('change',event=>{if(event.target.value)field('source').value=event.target.value;summary();});
  function options(name, items, label, extras) {
    const select=field(name), old=select.value;select.replaceChildren(new Option('선택',''));
    for(const item of items){const option=new Option(label(item),item.id);if(extras)extras(option,item);select.add(option);}select.value=[...select.options].some(o=>o.value===old)?old:'';
  }
  async function refresh() {
    if(loading)return;loading=true;field('runner').disabled=true;document.getElementById('task-pipeline-refresh').disabled=true;document.getElementById('task-environment-refresh').disabled=true;
    const app=field('application').value;field('application').disabled=true;field('function').disabled=true;text('task-environment-status','조회 중');
    try {
      const url=new URL(form.dataset.environments,location.origin);url.searchParams.set('application',app);
      const response=await fetch(url,{headers:{Accept:'application/json'},cache:'no-store',signal:AbortSignal.timeout(90000)});
      if(!response.ok || !response.headers.get('content-type')?.includes('application/json'))throw Error('조회 실패 · 프로파일·리전·구획·권한을 확인하세요.');
      const data=await response.json();options('application',data.applications,a=>a.name+' · '+a.shape);options('pipeline',data.pipelines,p=>p.projectName+' / '+p.name);
      options('function',data.functions.filter(f=>f.contract==='maven-v1' && f.state==='ACTIVE' && f.bucket && f.namespace && /^[a-zA-Z0-9_-]{1,80}$/.test(f.prefix)),f=>f.name,(option,f)=>option.dataset.bucket=f.bucket);
      text('task-environment-status',data.warning ? data.warning : field('function').options.length>1?'조회 완료':'사용 가능한 빌더 없음 · 애플리케이션 선택 또는 새 환경 구성');
    }catch(error){field('function').replaceChildren(new Option('조회 실패',''));text('task-environment-status',error.message);}
    finally{loading=false;field('runner').disabled=false;document.getElementById('task-pipeline-refresh').disabled=false;document.getElementById('task-environment-refresh').disabled=false;mode();}
  }
  field('application').addEventListener('change',()=>{field('function').replaceChildren(new Option('조회 중',''));summary();refresh();});
  document.getElementById('task-environment-refresh').addEventListener('click',refresh);
  document.getElementById('task-pipeline-refresh').addEventListener('click',refresh);
  form.noValidate=true;
  form.addEventListener('submit',event=>{if(loading){event.preventDefault();return;}for(let i=0;i<steps.length;i++){const invalid=[...steps[i].querySelectorAll('input,select')].find(c=>!c.disabled && !c.checkValidity());if(invalid){event.preventDefault();show(i);invalid.reportValidity();return;}}});
  document.querySelector('.task-wizard-controls').hidden=false;mode();show(0);
})();
