(() => {
 const forms=[...document.querySelectorAll('#build-report-form, #object-report-form')];if(!forms.length)return;
 const status=document.getElementById('build-report-status'),details=document.getElementById('build-report-details');
 let revision=0,active=null,chosen='';
 const selection=form=>new URLSearchParams(new FormData(form)).toString();
 function clear(){details.hidden=true;details.textContent='';document.getElementById('pmd-reports').replaceChildren();document.getElementById('report-files').replaceChildren();}
 function changed(form){if(active===form&&chosen!==selection(form)){revision++;chosen=selection(form);clear();status.textContent='선택이 변경되었습니다. 보고서를 다시 조회하세요.';status.dataset.state='CHANGED';}}
 for(const form of forms){
  form.addEventListener('input',()=>changed(form));form.addEventListener('change',()=>changed(form));
  form.addEventListener('submit',async event=>{
   event.preventDefault();if(form.dataset.reportBusy==='true')return;
   const button=form.querySelector('button:not([type=button])');form.dataset.reportBusy='true';button.disabled=true;
   active=form;chosen=selection(form);const request=++revision,snapshot=chosen;
   clear();status.textContent='보고서 조회 중';status.dataset.state='LOADING';
   const current=()=>request===revision&&active===form&&snapshot===selection(form);
   try{
    const params=new URLSearchParams(snapshot);if(form.id==='object-report-form'){params.set('bucket',params.get('reportBucket'));params.set('prefix',params.get('reportPrefix'));}
    const response=await fetch(form.action+'?'+params,{headers:{Accept:'application/json'},cache:'no-store'});
    if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))throw Error();const data=await response.json();
    if(!current())return;
    status.textContent=data.state==='MISSING'?'JUnit 보고서 없음 · 테스트 통과 여부를 판단할 수 없습니다.':data.state==='EMPTY'?'보고서에 테스트가 없습니다.':`테스트 ${data.tests} · 실패 ${data.failures} · 오류 ${data.errors} · 건너뜀 ${data.skipped}`;
    window.CompanionPmdReports.render(data.pmd);window.CompanionReportFiles.render(data.attachments);
    details.textContent=data.details.map(item=>`${item.suite} / ${item.test}\n${item.message}`).join('\n\n');details.hidden=!data.details.length;status.dataset.state='READY';
   }catch{if(current()){clear();status.textContent='집계 실패 · 연결·보고서 형식·크기·읽기 권한을 확인하고 다시 조회하세요.';status.dataset.state='ERROR';}}
   finally{form.dataset.reportBusy='false';button.disabled=form.id==='object-report-form'&&!form.elements.object.value;}
  });
 }
})();

(() => {
 const form=document.getElementById('object-report-form');if(!form)return;
 const status=document.getElementById('object-report-status'),select=form.elements.object;
 const list=document.getElementById('object-report-list'),view=form.querySelector('button:not([type=button])');
 let revision=0;
 const selection=()=>JSON.stringify([form.elements.reportBucket.value,form.elements.reportPrefix.value]);
 let current=selection();
 function clear(){revision++;select.replaceChildren(new Option('보고서 목록을 조회하세요',''));view.disabled=true;status.textContent='';select.dispatchEvent(new Event('change',{bubbles:true}));}
 function changed(){if(selection()!==current){current=selection();clear();}}
 form.addEventListener('input',changed);form.addEventListener('change',changed);
 select.addEventListener('change',()=>{view.disabled=form.dataset.reportBusy==='true'||!select.value;});
 clear();
 list.addEventListener('click',async()=>{
  clear();const request=revision,chosen=selection();list.disabled=true;status.textContent='보고서 조회 중';
  try{
   const data=new URLSearchParams(new FormData(form));data.set('bucket',data.get('reportBucket'));data.set('prefix',data.get('reportPrefix'));
   const response=await fetch('/builds/report-storage/objects?'+data,{headers:{Accept:'application/json'},cache:'no-store'});
   if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))throw Error();
   const objects=await response.json();if(request!==revision||chosen!==selection())return;
   select.replaceChildren(new Option('단계별 보고서 선택',''),...objects.map(name=>new Option('단계 …'+(name.split('/').at(-2)||'').slice(-12)+' · test-reports.tar.gz',name)));
   if(objects.length===1)select.value=objects[0];select.dispatchEvent(new Event('change',{bubbles:true}));view.disabled=form.dataset.reportBusy==='true'||!select.value;
   status.textContent=objects.length?`보고서 ${objects.length}개`:'이 실행의 보고서 없음 · 명세의 버킷·접두어와 업로드 로그를 확인하세요.';
  }catch{if(request===revision&&chosen===selection())status.textContent='조회 실패 · 연결·버킷·접두어·객체 읽기 권한을 확인하고 다시 조회하세요.';}
  finally{list.disabled=false;}
 });
})();
