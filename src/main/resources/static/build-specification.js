(() => {
  const form=document.querySelector('#build-specification-form');if(!form)return;
  const preview=document.querySelector('#build-specification-preview'),status=document.querySelector('#build-specification-status'),download=document.querySelector('#build-specification-download');
  let content='';
  function invalidate(){document.dispatchEvent(new Event('specification-invalidated'));content='';preview.hidden=download.hidden=true;status.textContent='';}
  form.addEventListener('input',invalidate);
  form.elements.buildCommand.addEventListener('input',()=>{document.getElementById('build-command-mode').textContent=form.elements.buildCommand.value.trim()?'직접 입력 명령 사용 · Goal·profile 미적용':'도구 기본 명령 사용';});
  function outputFields(){
    const container=form.elements.outputType.value==='DOCKER_IMAGE';
    form.querySelectorAll('[data-container-field]').forEach(field=>{field.hidden=!container;field.querySelectorAll('input').forEach(input=>{input.disabled=!container;input.required=container;});});
    form.elements.outputName.required=container;form.elements.location.required=container;
    document.querySelector('#build-location-label').textContent=container?'로컬 이미지:태그 · 필수':'출력 파일 · 저장 시 필수';
    form.elements.location.placeholder=container?'application:v1':{maven:'target/application.jar',gradle:'build/libs/application.jar',python:'dist/application.whl',node:'application.tar.gz',typescript:'application.tar.gz',go:'dist/application',ruby:'application.tar.gz'}[form.elements.tool.value];
  }
  const builderImages={maven:'docker.io/library/maven:3.9-eclipse-temurin-21',gradle:'docker.io/library/gradle:8-jdk21',python:'docker.io/library/python:3.11-slim',node:'docker.io/library/node:22-bookworm-slim',typescript:'docker.io/library/node:22-bookworm-slim',go:'docker.io/library/golang:1.27.3-bookworm',ruby:'docker.io/library/ruby:3.4-bookworm'};
  function builderFields(){const enabled=form.elements.runtime.value==='builder';const field=document.getElementById('build-builder-image-field');field.hidden=!enabled;form.elements.builderImage.disabled=!enabled;form.elements.builderImage.required=enabled;if(enabled&&!form.elements.builderImage.value)form.elements.builderImage.value=builderImages[form.elements.tool.value];document.getElementById('builder-image-options').replaceChildren(new Option(builderImages[form.elements.tool.value]));}
  form.elements.runtime.addEventListener('change',()=>{builderFields();invalidate();});builderFields();
  form.elements.outputType.addEventListener('change',()=>{outputFields();invalidate();});outputFields();
  form.elements.tool.addEventListener('change',()=>{
    const tool=form.elements.tool.value;
    document.getElementById('build-language-requirements').textContent={maven:'Java: JAR/WAR 형식·실행할 JDK 버전·네이티브 의존성을 확인하세요.',gradle:'Java: JAR/WAR 형식·실행할 JDK 버전·네이티브 의존성을 확인하세요.',python:'Python: 버전·의존성·실행 명령이 필요합니다. 기본 명세는 pytest 테스트이며 wheel·소스 패키징은 프로젝트 명세에 추가하세요.',node:'JavaScript/TypeScript: 정적 파일은 웹 서버, Node 서버는 Node 버전·의존성·시작 명령이 필요합니다. package.json의 script를 사용합니다.',typescript:'TypeScript: npm script로 tsc를 실행하고 생성된 JavaScript를 배포합니다. TypeScript·타입 정의는 프로젝트 의존성으로 설치하세요.',go:'Go: 빌더 이미지의 Go·go mod 사용. go test 후 dist/application 생성. 대상 OS·아키텍처와 CGO 의존성을 확인하세요.',ruby:'Ruby: 빌더 이미지의 Ruby·Bundler 사용. Gemfile 설치 후 Rake task 실행. Rakefile과 실행 명령을 확인하세요.'}[tool];
    form.elements.runtime.replaceChildren(...([['existing','기존 환경 사용'],['builder','빌더 컨테이너 사용'],...({maven:[['jdk17','JDK 17 설치 · Temurin'],['jdk21','JDK 21 설치 · Temurin']],gradle:[['jdk17','JDK 17 설치 · Temurin'],['jdk21','JDK 21 설치 · Temurin']],python:[['python311','Python 3.11 설치']],node:[['node22','Node.js 22 설치']],typescript:[['node22','Node.js 22 설치']],go:[],ruby:[]}[tool])]).map(([value,label])=>new Option(label,value)));
    form.elements.builderImage.value=builderImages[tool];if(['go','ruby'].includes(tool))form.elements.runtime.value='builder';builderFields();
    const values={maven:['Goal','verify',['test','package','verify']],gradle:['Task','build',['test','build','assemble']],python:['pytest 옵션','--junitxml=reports/tests.xml',['--junitxml=reports/tests.xml','-q']],node:['npm script','test build',['test','build','test build']],typescript:['npm script','test build',['test','build','test build']],go:['테스트 패키지','./...',['./...','.']],ruby:['Rake task','test',['test','spec']]}[tool];
    document.querySelector('#build-tasks-label').textContent=values[0];form.elements.tasks.value=values[1];
    document.querySelector('#build-task-options').replaceChildren(...values[2].map(value=>new Option(value,value)));
    form.elements.launcher.disabled=!['maven','gradle'].includes(tool);form.elements.launcher.closest('label').hidden=form.elements.launcher.disabled;form.elements.profiles.closest('label').hidden=tool!=='maven';form.elements.location.placeholder={maven:'target/application.jar',gradle:'build/libs/application.jar',python:'dist/application.whl',node:'application.tar.gz',typescript:'application.tar.gz',go:'dist/application',ruby:'application.tar.gz'}[tool];form.elements.profiles.disabled=tool!=='maven';if(tool!=='maven')form.elements.profiles.value='';outputFields();invalidate();
  });
  form.addEventListener('submit',async event=>{
    event.preventDefault();status.textContent='생성 중';
    try {
      const response=await fetch(form.action,{method:'POST',body:new URLSearchParams(new FormData(form)),headers:{Accept:'text/plain'}});
      if(!response.ok||!response.headers.get('content-type')?.startsWith('text/plain'))throw Error();
      content=await response.text();document.dispatchEvent(new CustomEvent('specification-ready',{detail:content}));preview.textContent=content;preview.hidden=download.hidden=false;status.textContent='미리보기 · OCI 미적용';
    }catch{invalidate();status.textContent='생성 실패 · 입력값과 연결을 확인하세요.';}
  });
  download.addEventListener('click',()=>{
    if(!content)return;const url=URL.createObjectURL(new Blob([content],{type:'text/yaml'}));
    const a=document.createElement('a');a.href=url;a.download='build_spec.yaml';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  });
})();

(() => {
 const form=document.getElementById('git-spec-form');if(!form)return;
 const compare=document.getElementById('git-spec-compare'),save=document.getElementById('git-spec-save'),status=document.getElementById('git-spec-status');let content='',ticket='';
 function invalidate(){ticket='';save.disabled=true;status.textContent='';document.getElementById('git-spec-before-panel').hidden=true;}
 form.addEventListener('input',invalidate);
 function source(){const option=form.elements.source.selectedOptions[0];form.elements.branch.value=option.dataset.branch||'';form.elements.username.value=(option.dataset.url||'').includes('github.com')?'x-access-token':(option.dataset.url||'').includes('gitlab.com')?'oauth2':'';invalidate();}
 form.elements.source.addEventListener('change',source);source();
 function summary(){const panel=document.getElementById('configure-selection');if(!panel)return;const recipe=document.getElementById('build-specification-form');panel.textContent=[form.elements.source.selectedOptions[0].textContent,form.elements.branch.value,recipe.elements.tool.selectedOptions[0].textContent,recipe.elements.runtime.selectedOptions[0].textContent,recipe.elements.buildCommand.value.trim()?'직접 입력 명령':recipe.elements.tasks.value,form.elements.path.value].filter(Boolean).join(' · ');for(const link of document.querySelectorAll('.configure-pipeline-link')){const url=new URL(link.href);url.searchParams.set('registeredSource',form.elements.source.value);link.href=url;}}
 document.addEventListener('input',summary);document.addEventListener('change',summary);summary();
 document.addEventListener('specification-invalidated',()=>{content='';compare.disabled=true;invalidate();});
 document.addEventListener('specification-ready',event=>{content=event.detail;compare.disabled=false;invalidate();});
 async function post(url,data){const response=await fetch(url,{method:'POST',body:new URLSearchParams(data),headers:{Accept:'application/json'}});if(!response.headers.get('content-type')?.includes('application/json'))throw Error('연결과 입력값을 확인하세요.');const result=await response.json();if(!response.ok)throw Error(result.message||'요청 실패');return result;}
 form.addEventListener('submit',async event=>{event.preventDefault();if(!content)return;invalidate();compare.disabled=true;status.textContent='Git 내용 조회 중';try{const data=new FormData(form);data.set('content',content);const result=await post(form.action,data);ticket=result.ticket;save.disabled=false;status.textContent=result.target+' · 기준 '+result.revision.slice(0,12)+' · 위 명세로 저장';document.getElementById('git-spec-before').textContent=result.before||'(새 파일)';document.getElementById('git-spec-before-panel').hidden=false;}catch(error){status.textContent=error.message;}finally{compare.disabled=!content;}});
 save.addEventListener('click',async()=>{if(!ticket)return;save.disabled=compare.disabled=true;const data=new FormData(form);data.set('ticket',ticket);ticket='';status.textContent='커밋 중';try{const result=await post('/builds/specification/git/save',data);status.textContent=(result.state==='UNCHANGED'?'저장 확인 · 변경 없음 · ':'커밋 완료 · ')+result.revision+' · 파이프라인의 명세 경로를 연결하세요.';}catch(error){status.textContent=error.message+' 원격 저장 여부를 확인한 뒤 다시 비교하세요.';}finally{compare.disabled=!content;}});
})();
(() => {
 const steps=[...document.querySelectorAll('.build-flow a[href^="#"]')];if(!steps.length)return;
 let pending=false;
 function mark(){pending=false;let selected=steps[0];for(const link of steps){const section=document.querySelector(link.getAttribute('href'));if(section&&section.getBoundingClientRect().top<innerHeight/3)selected=link;}for(const link of steps){link.classList.toggle('is-current',link===selected);if(link===selected)link.setAttribute('aria-current','step');else link.removeAttribute('aria-current');}}
 addEventListener('scroll',()=>{if(!pending){pending=true;requestAnimationFrame(mark);}},{passive:true});mark();
})();
