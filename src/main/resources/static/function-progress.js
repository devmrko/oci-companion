'use strict';
(() => {
  const panel = document.getElementById('function-progress');
  if (!panel) return;
  const status = document.getElementById('function-run-status');
  const log = document.getElementById('function-live-log');
  const result = document.getElementById('function-live-result');
  const notice = document.getElementById('function-log-notice');
  const refresh = document.getElementById('function-log-refresh');
  const follow = document.getElementById('function-log-follow');
  let timer, busy = false, polls = 0;
  async function update() {
    if (busy) return;
    clearTimeout(timer); busy = true; refresh.disabled = true;
    let again = false;
    try {
      const response = await fetch(panel.dataset.url, {headers: {'Accept': 'application/json'}, cache: 'no-store', signal: AbortSignal.timeout(90000)});
      if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw Error('조회 실패 · 연결·권한·선택한 리전/구획을 확인한 뒤 다시 조회하세요.');
      const data = await response.json();
      status.textContent = data.status;
      log.textContent = data.log;
      result.textContent = data.result;
      if(data.result){try{document.dispatchEvent(new CustomEvent('function-build-progress',{detail:{...JSON.parse(data.result),profiles:data.profiles}}));}catch{ /* Keep displaying the raw result when parsing fails. */ }}
      if (follow.checked) log.scrollTop = log.scrollHeight;
      const done = ['SUCCEEDED', 'FAILED', 'CANCELED', 'CANCELLED', 'TIMED_OUT'].includes(data.status);
      polls++;
      again = !done && polls < 720;
      notice.textContent = done ? '빌드 종료 · 자동 갱신 중지' : again ? '5초마다 갱신 · 마지막 조회 ' + new Date().toLocaleTimeString() : '자동 조회 중지 · 결과가 없으면 OCI 호출 로그와 버킷 권한을 확인하세요.';
    } catch (error) {
      notice.textContent = error.name === 'TimeoutError' ? '조회 시간 초과 · 다시 조회하세요.' : error.message;
    } finally {
      busy = false; refresh.disabled = false;
      if (again) timer = setTimeout(update, 5000);
    }
  }
  refresh.addEventListener('click', () => { polls = 0; update(); });
  window.addEventListener('pagehide', () => clearTimeout(timer));
  const reports=document.getElementById('function-reports-refresh');
  reports.addEventListener('click',async()=>{
    reports.disabled=true;const label=document.getElementById('function-reports-status'),details=document.getElementById('function-reports-details');
    label.textContent='보고서 조회 중';details.hidden=true;
    try {
      const url=new URL(panel.dataset.url,location.origin);url.pathname='/builds/functions/reports';
      const response=await fetch(url,{headers:{Accept:'application/json'},cache:'no-store'});
      if(!response.ok)throw Error();const data=await response.json();
      label.textContent=data.state==='MISSING'?'보고서 없음 · 테스트 통과 여부를 판단할 수 없습니다.':data.state==='EMPTY'?'보고서에 테스트가 없습니다.':`테스트 ${data.tests} · 실패 ${data.failures} · 오류 ${data.errors} · 건너뜀 ${data.skipped}`;
      details.textContent=data.details.map(item=>`${item.suite} / ${item.test}\n${item.message}`).join('\n\n');details.hidden=!data.details.length;
    }catch{label.textContent='보고서 조회 실패 · 권한 또는 보고서 형식을 확인하세요.';}finally{reports.disabled=false;}
  });
  update();
})();
