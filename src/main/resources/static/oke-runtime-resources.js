(()=>{
 const form=document.querySelector('#oke-setup-form');if(!form)return;
 const field=n=>form.elements.namedItem(n),button=form.querySelector('[data-runtime-load]'),status=form.querySelector('[data-runtime-status]');
 const selectors=[...form.querySelectorAll('[data-runtime-kind]')],detail=form.querySelector('[data-runtime-ingress]'),services=form.querySelector('[data-runtime-services]');
 const routeSelect=form.querySelector('[data-endpoint-route]'),probeButton=form.querySelector('[data-endpoint-check]'),probeStatus=form.querySelector('[data-endpoint-status]'),probeResult=form.querySelector('[data-endpoint-result]');
 let probeGeneration=0,probeController=null;
 function resetProbe(){probeGeneration++;probeController?.abort();probeStatus.textContent='';probeResult.hidden=true;probeResult.replaceChildren();probeButton.disabled=!routeSelect.value;}
 let generation=0,controller=null,snapshot=null;
 function render(){routeSelect.replaceChildren(new Option('접속 경로 선택',''));const ingress=snapshot?.ingresses.find(x=>x.name===field('ingress').value);detail.hidden=!ingress;if(ingress)for(const r of ingress.routes){if(r.host&&!r.host.includes('*'))routeSelect.add(new Option('https://'+r.host+r.path,JSON.stringify({host:r.host,path:r.path})));}resetProbe();if(ingress)detail.textContent=[`Ingress: ${ingress.name} · class: ${ingress.controller||'미확인'}`,`주소: ${ingress.addresses.join(', ')||'미할당'}`,...ingress.routes.map(r=>`${r.host||'*'}${r.path} → ${r.service}:${r.port}`),...ingress.tls.map(t=>`TLS: ${t.hosts.join(', ')} → ${t.secret||'Secret 참조 없음'}`)].join('\n');}
 function clear(){generation++;controller?.abort();snapshot=null;routeSelect.replaceChildren(new Option('Ingress 조회 후 선택',''));resetProbe();for(const s of selectors)s.replaceChildren(new Option('사용 안 함',''));status.textContent='선택 범위 변경 · 다시 조회하세요.';detail.hidden=services.hidden=true;button.disabled=false;}
 for(const n of ['namespace','endpoint'])field(n).addEventListener(n==='namespace'?'input':'change',clear);
 field('ingress').addEventListener('change',render);
 routeSelect.addEventListener('change',resetProbe);
 probeButton.addEventListener('click',async()=>{
  if(!routeSelect.value)return;const route=JSON.parse(routeSelect.value),version=++probeGeneration;probeController?.abort();const active=probeController=new AbortController();const timer=setTimeout(()=>active.abort(),45000);
  probeButton.disabled=true;probeResult.hidden=true;probeStatus.textContent='접속 점검 중…';
  const body=new URLSearchParams({region:form.dataset.region,compartment:form.dataset.compartment,environment:field('environment').value,endpoint:field('endpoint').value,namespace:field('namespace').value,ingress:field('ingress').value,host:route.host,path:route.path,_csrf:field('_csrf').value});
  try{const response=await fetch('/deployments/oke/endpoint-check',{method:'POST',headers:{Accept:'application/json'},body,signal:active.signal});if(!response.ok||response.redirected||!response.headers.get('content-type')?.includes('application/json'))throw Error();const report=await response.json();if(version!==probeGeneration)return;
   probeStatus.textContent=report.target+' · '+report.observedAt;probeResult.replaceChildren();const table=document.createElement('table');table.className='data-table';const head=table.createTHead().insertRow();for(const title of ['항목','상태','근거']){const th=document.createElement('th');th.textContent=title;head.append(th);}const rows=table.createTBody();for(const check of report.checks){const row=rows.insertRow();row.insertCell().textContent=check.item;const badge=document.createElement('span');badge.className='endpoint-check-state';badge.dataset.state=check.state;badge.textContent={PASS:'확인',WARN:'확인 필요',FAIL:'실패',UNKNOWN:'미확인'}[check.state]||'미확인';row.insertCell().append(badge);row.insertCell().textContent=check.detail;}probeResult.append(table);probeResult.hidden=false;
  }catch{if(version===probeGeneration)probeStatus.textContent='점검 실패·시간 초과 · 로그인과 리소스 조회를 확인하세요.';}
  finally{clearTimeout(timer);if(version===probeGeneration)probeButton.disabled=!routeSelect.value;}
 });
 button.addEventListener('click',async()=>{
  if(!field('namespace').value){status.textContent='Namespace를 입력하세요.';return;}
  const version=++generation;controller?.abort();controller=new AbortController();const active=controller;const timer=setTimeout(()=>active.abort(),120000);
  button.disabled=true;status.textContent='조회 중…';const query=new URLSearchParams({region:form.dataset.region,compartment:form.dataset.compartment,environment:field('environment').value,namespace:field('namespace').value,endpoint:field('endpoint').value});
  try{
   const response=await fetch('/deployments/oke/resources?'+query,{headers:{Accept:'application/json'},cache:'no-store',signal:active.signal});
   if(!response.ok||response.redirected||!response.headers.get('content-type')?.includes('application/json'))throw Error();const data=await response.json();if(version!==generation)return;
   snapshot=data;const notes=[];
   for(const select of selectors){const current=select.value,listing=data.resources[select.dataset.runtimeKind];if(!listing?.available){notes.push(listing?.note||'조회 실패');continue;}
    select.replaceChildren(new Option('사용 안 함',''));for(const item of listing.items){if(select.name==='secret'&&item.type!=='Opaque')continue;if(select.name==='pullSecret'&&!['kubernetes.io/dockerconfigjson','kubernetes.io/dockercfg'].includes(item.type))continue;select.add(new Option(item.name,item.name));}
    if(current){if(![...select.options].some(o=>o.value===current))select.add(new Option(current+' · 목록에 없음',current));select.value=current;}
   }
   const loads=data.services.filter(s=>s.type==='LoadBalancer');services.hidden=!loads.length;services.textContent=loads.map(s=>`LoadBalancer: ${s.name} · ${s.addresses.join(', ')||'주소 미할당'}`).join('\n');render();
   status.textContent='조회 완료 · '+data.observedAt+(notes.length?' · '+[...new Set(notes)].join(' / '):'');
  }catch{if(version===generation)status.textContent='조회 실패 · 이전 선택 유지. 로그인·API 접속·list 권한을 확인하세요.';}
  finally{clearTimeout(timer);if(version===generation)button.disabled=false;}
 });
})();
