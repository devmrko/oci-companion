(() => {
 const form=document.getElementById('build-report-form');if(!form)return;
 form.addEventListener('submit',async event=>{
  event.preventDefault();const status=document.getElementById('build-report-status'),details=document.getElementById('build-report-details'),button=form.querySelector('button');button.disabled=true;details.hidden=true;status.textContent='보고서 조회 중';
  try {
   const response=await fetch(form.action+'?'+new URLSearchParams(new FormData(form)),{headers:{Accept:'application/json'},cache:'no-store'});
   if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))throw Error();const data=await response.json();
   status.textContent=data.state==='MISSING'?'JUnit 보고서 없음 · 테스트 통과 여부를 판단할 수 없습니다.':data.state==='EMPTY'?'보고서에 테스트가 없습니다.':`테스트 ${data.tests} · 실패 ${data.failures} · 오류 ${data.errors} · 건너뜀 ${data.skipped}`;
   details.textContent=data.details.map(item=>`${item.suite} / ${item.test}\n${item.message}`).join('\n\n');details.hidden=!data.details.length;
  }catch{status.textContent='집계 실패 · 보고서 형식·크기·읽기 권한을 확인하세요.';}finally{button.disabled=false;}
 });
})();
