(() => {
 for(const widget of document.querySelectorAll('[data-report-storage]')) {
  const status=widget.querySelector('[data-storage-status]'),bucket=widget.querySelector('[name=reportBucket]'),ns=widget.querySelector('[name=reportNamespace]');
  const scope=()=>new URLSearchParams({region:widget.dataset.region,compartment:widget.dataset.compartment});
  async function refresh(selected=bucket.value){
   status.textContent='버킷 조회 중';
   const response=await fetch('/builds/report-storage?'+scope(),{headers:{Accept:'application/json'},cache:'no-store'});
   if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))throw Error('버킷 조회 실패 · 리전·구획·읽기 권한을 확인하세요.');
   const data=await response.json();ns.value=data.namespace;bucket.replaceChildren(new Option('버킷 선택',''),...data.buckets.map(name=>new Option(name,name)));bucket.value=selected;
   status.textContent=`Namespace: ${data.namespace} · 버킷 ${data.buckets.length}개`;widget.dispatchEvent(new Event('input',{bubbles:true}));
  }
  widget.querySelector('[data-storage-refresh]').addEventListener('click',async event=>{event.target.disabled=true;try{await refresh();}catch(e){status.textContent=e.message;}finally{event.target.disabled=false;}});
  widget.querySelector('[data-storage-create]').addEventListener('click',async event=>{
   const name=widget.querySelector('[data-bucket-name]').value.trim();if(!/^[A-Za-z0-9_-]{1,256}$/.test(name)){status.textContent='버킷 이름을 입력하세요 · 영문·숫자·밑줄·하이픈';return;}
   event.target.disabled=true;status.textContent='버킷 생성 중';
   try{const data=scope();data.set('name',name);data.set(widget.dataset.csrfName,widget.dataset.csrf);const response=await fetch('/builds/report-storage/buckets',{method:'POST',body:data,headers:{Accept:'application/json'}});if(!response.ok)throw Error('생성 여부를 확인하지 못했습니다. 다시 생성하기 전에 목록·권한을 확인하세요.');await refresh(name);status.textContent+=' · 생성 완료';}catch(e){status.textContent=e.message;}finally{event.target.disabled=false;}
  });
 }
 const form=document.getElementById('build-specification-form');if(!form)return;
 const panel=form.querySelector('[data-object-storage-fields]');
 function toggle(){const enabled=form.elements.reports.checked,object=enabled&&form.elements.reportDestination.value==='object';panel.hidden=!object;panel.querySelectorAll('input,select,button').forEach(el=>el.disabled=!object);for(const name of ['reportBucket','reportPrefix'])form.elements[name].required=object;

 }
 form.addEventListener('change',toggle);toggle();
})();
