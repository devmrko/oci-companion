(() => {
 const root=document.querySelector('#build-logs'); if(!root)return;
 const status=document.querySelector('#log-status'),output=document.querySelector('#log-output');
 const interval=document.querySelector('#log-interval'),stage=document.querySelector('#log-stage'),search=document.querySelector('#log-search');
 const older=document.querySelector('#log-older'),refresh=document.querySelector('#log-refresh');
 let lines=[],next='',until='',timer,busy=false;
 function render(){output.textContent=lines.filter(l=>(!stage.value||l.stage===stage.value)&&l.message.toLowerCase().includes(search.value.toLowerCase())).map(l=>l.time+' ['+l.stage+'] '+l.message).join(String.fromCharCode(10));}
 function schedule(){clearTimeout(timer);if(Number(interval.value))timer=setTimeout(()=>load(false),Number(interval.value));}
 async function load(previous){
  if(busy)return;busy=true;older.disabled=refresh.disabled=true;status.textContent='로그 조회 중…';
  const q=new URLSearchParams({...root.dataset,page:previous?next:'',until:previous?until:''});
  try{
   const response=await fetch('/builds/logs?'+q,{headers:{Accept:'application/json'}});
   if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))throw Error('연결 또는 조회 범위가 변경되었습니다. 화면을 다시 조회하세요.');
   const data=await response.json();
   if(!['READY','EMPTY'].includes(data.state)){status.textContent=data.note+' · 아래 권한 설정 확인';next='';return;}
   lines=previous?[...data.lines,...lines]:data.lines;next=data.next;until=data.until;
   const chosen=stage.value;stage.replaceChildren(new Option('전체',''));
   [...new Set(lines.map(l=>l.stage))].filter(Boolean).forEach(s=>stage.add(new Option(s,s)));stage.value=chosen;
   status.textContent=data.note+' · '+lines.length+'건 · 갱신 '+new Date().toLocaleTimeString();render();
  }catch(e){status.textContent=e.message+' · 기존 조회 로그 유지';}
  finally{busy=false;refresh.disabled=false;older.disabled=!next;schedule();}
 }
 refresh.addEventListener('click',()=>load(false));older.addEventListener('click',()=>{interval.value='0';clearTimeout(timer);load(true);});
 interval.addEventListener('change',schedule);stage.addEventListener('change',render);search.addEventListener('input',render);
 window.addEventListener('pagehide',()=>clearTimeout(timer));load(false);
})();
