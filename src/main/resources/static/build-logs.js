(() => {
 const root=document.querySelector('#build-logs'); if(!root)return;
 const status=document.querySelector('#log-status'),output=document.querySelector('#log-output'),count=document.querySelector('#log-visible-count');
 const interval=document.querySelector('#log-interval'),stage=document.querySelector('#log-stage'),search=document.querySelector('#log-search');
 const older=document.querySelector('#log-older'),refresh=document.querySelector('#log-refresh');
 let lines=[],next='',until='',timer,busy=false,lastSuccess='',failed=false;
 function render(){const visible=lines.filter(l=>(!stage.value||l.stage===stage.value)&&l.message.toLowerCase().includes(search.value.toLowerCase()));output.textContent=visible.map(l=>l.time+' ['+l.stage+'] '+l.message).join('\n');if(count)count.textContent='표시 '+visible.length+'건 / 조회 '+lines.length+'건';}
 function schedule(){clearTimeout(timer);if(Number(interval.value))timer=setTimeout(()=>load(false),Number(interval.value));}
 function failure(note,state){failed=true;root.dataset.logState=state;interval.value='0';clearTimeout(timer);status.textContent=note+' · 자동 갱신 중지'+(lastSuccess?' · 이전 조회 로그 '+lines.length+'건 유지 · 마지막 성공 '+lastSuccess:' · 성공한 로그 조회 없음');}
 async function load(previous){
  if(busy)return;busy=true;older.disabled=refresh.disabled=true;status.textContent='로그 조회 중…';
  const q=new URLSearchParams({region:root.dataset.region,compartment:root.dataset.compartment,pipeline:root.dataset.pipeline,run:root.dataset.run,page:previous?next:'',until:previous?until:''});
  try{
   const response=await fetch('/builds/logs?'+q,{headers:{Accept:'application/json'},cache:'no-store'});
   if(!response.ok||!response.headers.get('content-type')?.includes('application/json'))throw Error('로그 조회 실패 · 연결·로그인·조회 범위를 확인하고 다시 조회하세요.');
   const data=await response.json();
   if(!['READY','EMPTY'].includes(data.state)){failure(data.note||'로그 조회 실패 · 연결과 Logging 설정을 확인하세요.',data.state||'ERROR');return;}
   lines=previous?[...data.lines,...lines]:data.lines;next=data.next;until=data.until;failed=false;root.dataset.logState=data.state;
   const chosen=stage.value;stage.replaceChildren(new Option('전체',''));
   [...new Set(lines.map(l=>l.stage))].filter(Boolean).forEach(s=>stage.add(new Option(s,s)));stage.value=[...stage.options].some(o=>o.value===chosen)?chosen:'';
   lastSuccess=new Date().toLocaleTimeString();status.textContent=data.note+' · '+lines.length+'건 · 마지막 성공 '+lastSuccess;render();
  }catch(e){failure('로그 조회 실패 · 연결·로그인·조회 범위를 확인하고 다시 조회하세요.','ERROR');}
  finally{busy=false;refresh.disabled=false;older.disabled=failed||!next;schedule();}
 }
 refresh.addEventListener('click',()=>load(false));older.addEventListener('click',()=>{interval.value='0';clearTimeout(timer);load(true);});
 interval.addEventListener('change',schedule);stage.addEventListener('change',render);search.addEventListener('input',render);
 window.addEventListener('pagehide',()=>clearTimeout(timer));load(false);
})();
