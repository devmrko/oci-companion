(() => {
 const item=document.querySelector('[data-check-context="result"] [data-check="tests"]');if(!item)return;
 function show(state,label,note){item.dataset.state=state;item.querySelector('.check-state-label').textContent=label;item.querySelector('.check-note').textContent=note;}
 document.addEventListener('companion-report',event=>{
  const {state,data}=event.detail||{};
  if(state!=='READY'){show('UNKNOWN','미확인',state==='ERROR'?'보고서 조회 실패':state==='LOADING'?'보고서 조회 중':'보고서 조회 필요');return;}
  if(!data||![data.tests,data.failures,data.errors,data.skipped].every(n=>Number.isInteger(n)&&n>=0)){show('UNKNOWN','미확인','보고서 집계 불가');return;}
  if(data.failures>0||data.errors>0){show('FAIL','조치 필요',`선택 보고서 · 실패 ${data.failures} · 오류 ${data.errors}`);return;}
  const pmd=Array.isArray(data.pmd)?data.pmd:[];
  if(pmd.some(p=>p.violations>0||p.errors>0)){show('WARN','확인 필요','선택 보고서 · PMD 위반·분석 오류 확인');return;}
  if(data.state!=='AVAILABLE'||data.tests===0){show('UNKNOWN','미확인','선택 보고서 · 테스트 결과 없음');return;}
  if(data.skipped>=data.tests){show('WARN','확인 필요','선택 보고서 · 모든 테스트 제외');return;}
  show('PASS','충족',`선택 보고서 · 테스트 ${data.tests} · 제외 ${data.skipped}`);
 });
})();
