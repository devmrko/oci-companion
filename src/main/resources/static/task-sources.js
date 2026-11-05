'use strict';
(() => {
  const form=document.getElementById('build-task-form');if(!form)return;
  const f=name=>form.elements.namedItem(name), el=id=>document.getElementById(id);
  const status=el('task-source-status'), refs=el('task-ref-choice'), commits=el('task-commit-choice');
  let versionSequence=0, matchSequence=0;
  function query(path){return new URL(path+'?'+new URLSearchParams({region:f('region').value,compartment:f('compartment').value,sourceId:f('sourceId').value,source:f('source').value}),location.origin);}
  async function read(url){const response=await fetch(url,{headers:{Accept:'application/json'},cache:'no-store',signal:AbortSignal.timeout(90000)});if(!response.ok || !response.headers.get('content-type')?.includes('application/json'))throw Error('조회 실패 · 소스 접근 권한·공개 여부·API 한도를 확인하세요.');return response.json();}
  function changed(){form.dispatchEvent(new Event('input'));form.dispatchEvent(new Event('change'));}
  function support(){const o=f('sourceId').selectedOptions[0];const blocked=f('runner').value==='FUNCTIONS' && o?.dataset.provider==='OCI';f('runner').setCustomValidity(blocked?'OCI 저장소는 OCI DevOps를 선택하세요.':'');el('task-source-support').textContent=blocked?'OCI 저장소는 OCI DevOps로 빌드합니다. Functions는 공개 GitHub·GitLab을 지원합니다.':'';}
  function clearVersion(){versionSequence++;refs.replaceChildren(new Option('조회 후 선택',''));commits.replaceChildren(new Option('직접 입력',''));f('commit').value='';}
  async function match(){
    const sequence=++matchSequence;support();
    if(f('runner').value!=='DEVOPS' || !f('pipeline').value){f('pipeline').setCustomValidity('');el('task-match-status').textContent='';return;}
    if(!f('source').value){f('pipeline').setCustomValidity('소스를 먼저 선택하세요.');return;}
    f('pipeline').setCustomValidity('소스 연결 조회 중');el('task-match-status').textContent='소스 연결 확인 중';
    try {
      const url=query('/builds/tasks/source-match');url.searchParams.set('pipeline',f('pipeline').value);const data=await read(url);
      if(sequence!==matchSequence)return;
      f('pipeline').setCustomValidity('');el('task-match-status').textContent='소스 일치 · '+data.source.name+' · '+data.source.branch;
    }catch{if(sequence!==matchSequence)return;f('pipeline').setCustomValidity('선택한 소스가 파이프라인에 하나의 소스로 연결돼 있는지 확인하세요.');el('task-match-status').textContent='소스 불일치 또는 조회 실패 · 파이프라인 생성·소스 설정에서 연결 후 다시 조회하세요. 스크립트 안의 Git clone은 자동 연결하지 않습니다.';}
  }
  async function loadCommits(ref,initialSha=''){
    const sequence=++versionSequence;status.textContent='커밋 조회 중';
    try {const url=query('/builds/tasks/source-versions');url.searchParams.set('ref',ref);const values=await read(url);if(sequence!==versionSequence)return;commits.replaceChildren(new Option('직접 입력',''));for(const v of values)commits.add(new Option(v.commit.slice(0,12)+' · '+v.name,v.commit));if(initialSha && !values.some(v=>v.commit===initialSha))commits.add(new Option(initialSha.slice(0,12)+' · 선택한 태그',initialSha));const selected=initialSha || values[0]?.commit || '';commits.value=selected;f('commit').value=selected;status.textContent='조회 완료 · 커밋 SHA로 고정';changed();}
    catch(error){if(sequence!==versionSequence)return;status.textContent=error.message+' 커밋 SHA를 직접 입력하세요.';}
  }
  async function loadRefs(){
    const sequence=++versionSequence;status.textContent='브랜치·태그 조회 중';
    try{const values=await read(query('/builds/tasks/source-versions'));if(sequence!==versionSequence)return;refs.replaceChildren(new Option('선택',''));for(const v of values){const o=new Option((v.kind==='TAG'?'태그':'브랜치')+' · '+v.name,v.kind+':'+v.name);o.dataset.sha=v.commit;o.dataset.name=v.name;o.dataset.kind=v.kind;refs.add(o);}status.textContent='조회 완료 · 브랜치·태그를 선택하세요.';const wanted='BRANCH:'+f('branch').value;if(values.some(v=>v.kind+':'+v.name===wanted)){refs.value=wanted;await selectRef();}}
    catch(error){if(sequence!==versionSequence)return;status.textContent=error.message+' 브랜치·커밋 SHA를 직접 입력하세요.';}
  }
  async function selectRef(){const option=refs.selectedOptions[0];if(!option?.dataset.name)return;if(option.dataset.kind==='BRANCH')f('branch').value=option.dataset.name;else f('branch').value=f('sourceId').selectedOptions[0]?.dataset.branch || f('branch').value || 'main';f('commit').value=option.dataset.sha || '';changed();await loadCommits(option.dataset.name,option.dataset.sha);}
  f('sourceId').addEventListener('change',()=>{clearVersion();const selected=f('sourceId').selectedOptions[0];f('source').value=selected.dataset.url || '';f('source').readOnly=!!f('sourceId').value;f('branch').value=selected.dataset.branch || 'main';changed();match();if(f('source').value)loadRefs();});
  f('source').addEventListener('input',()=>{clearVersion();match();});f('branch').addEventListener('input',()=>{versionSequence++;f('commit').value='';refs.value='';commits.value='';changed();});f('commit').addEventListener('input',()=>{versionSequence++;commits.value='';});
  refs.addEventListener('change',selectRef);commits.addEventListener('change',()=>{versionSequence++;if(commits.value)f('commit').value=commits.value;changed();});
  f('runner').addEventListener('change',match);f('pipeline').addEventListener('change',match);form.addEventListener('task-environments-updated',match);
  el('task-refs-refresh').addEventListener('click',loadRefs);
  el('task-sources-refresh').addEventListener('click',async()=>{
    try{const values=await read(query('/builds/tasks/sources'));const old=f('sourceId').value;f('sourceId').replaceChildren(new Option('Git URL 직접 입력',''));for(const v of values){const o=new Option(v.provider+' · '+v.name,v.id);o.dataset.url=v.url;o.dataset.branch=v.branch;o.dataset.provider=v.provider;f('sourceId').add(o);}f('sourceId').value=values.some(v=>v.id===old)?old:'';if(old && !f('sourceId').value){f('source').value='';clearVersion();}else if(old && f('source').value!==f('sourceId').selectedOptions[0].dataset.url){f('source').value=f('sourceId').selectedOptions[0].dataset.url;f('branch').value=f('sourceId').selectedOptions[0].dataset.branch;clearVersion();}f('source').readOnly=!!f('sourceId').value;status.textContent='등록 소스 조회 완료';changed();match();}catch(error){status.textContent=error.message;}
  });
  f('source').readOnly=!!f('sourceId').value;support();match();
})();
