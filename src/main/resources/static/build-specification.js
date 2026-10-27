(() => {
  const form=document.querySelector('#build-specification-form');if(!form)return;
  const preview=document.querySelector('#build-specification-preview'),status=document.querySelector('#build-specification-status'),download=document.querySelector('#build-specification-download');
  let content='';
  function invalidate(){content='';preview.hidden=download.hidden=true;status.textContent='';}
  form.addEventListener('input',invalidate);
  form.elements.tool.addEventListener('change',()=>{
    const tool=form.elements.tool.value;
    const values={maven:['Goal','verify',['test','package','verify']],gradle:['Task','build',['test','build','assemble']],python:['pytest 옵션','--junitxml=reports/tests.xml',['--junitxml=reports/tests.xml','-q']],node:['npm script','test build',['test','build','test build']]}[tool];
    document.querySelector('#build-tasks-label').textContent=values[0];form.elements.tasks.value=values[1];
    document.querySelector('#build-task-options').replaceChildren(...values[2].map(value=>new Option(value,value)));
    form.elements.launcher.disabled=!['maven','gradle'].includes(tool);form.elements.launcher.closest('label').hidden=form.elements.launcher.disabled;form.elements.profiles.closest('label').hidden=tool!=='maven';form.elements.location.placeholder={maven:'target/application.jar',gradle:'build/libs/application.jar',python:'dist/application.whl',node:'application.tar.gz'}[tool];form.elements.profiles.disabled=tool!=='maven';if(tool!=='maven')form.elements.profiles.value='';invalidate();
  });
  form.addEventListener('submit',async event=>{
    event.preventDefault();status.textContent='생성 중';
    try {
      const response=await fetch(form.action,{method:'POST',body:new URLSearchParams(new FormData(form)),headers:{Accept:'text/plain'}});
      if(!response.ok||!response.headers.get('content-type')?.startsWith('text/plain'))throw Error();
      content=await response.text();preview.textContent=content;preview.hidden=download.hidden=false;status.textContent='미리보기 · OCI 미적용';
    }catch{invalidate();status.textContent='생성 실패 · 입력값과 연결을 확인하세요.';}
  });
  download.addEventListener('click',()=>{
    if(!content)return;const url=URL.createObjectURL(new Blob([content],{type:'text/yaml'}));
    const a=document.createElement('a');a.href=url;a.download='build_spec.yaml';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  });
})();
