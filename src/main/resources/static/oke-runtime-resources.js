(()=>{
 const form=document.querySelector('#oke-setup-form');if(!form)return;
 const field=n=>form.elements.namedItem(n),button=form.querySelector('[data-runtime-load]'),status=form.querySelector('[data-runtime-status]');
 const selectors=[...form.querySelectorAll('[data-runtime-kind]')],detail=form.querySelector('[data-runtime-ingress]'),services=form.querySelector('[data-runtime-services]');
 let generation=0,controller=null,snapshot=null;
 function render(){const ingress=snapshot?.ingresses.find(x=>x.name===field('ingress').value);detail.hidden=!ingress;if(ingress)detail.textContent=[`Ingress: ${ingress.name} · class: ${ingress.controller||'미확인'}`,`주소: ${ingress.addresses.join(', ')||'미할당'}`,...ingress.routes.map(r=>`${r.host||'*'}${r.path} → ${r.service}:${r.port}`),...ingress.tls.map(t=>`TLS: ${t.hosts.join(', ')} → ${t.secret||'Secret 참조 없음'}`)].join('\n');}
 function clear(){generation++;controller?.abort();snapshot=null;for(const s of selectors)s.replaceChildren(new Option('사용 안 함',''));status.textContent='선택 범위 변경 · 다시 조회하세요.';detail.hidden=services.hidden=true;button.disabled=false;}
 for(const n of ['namespace','endpoint'])field(n).addEventListener(n==='namespace'?'input':'change',clear);
 field('ingress').addEventListener('change',render);
 button.addEventListener('click',async()=>{
  if(!field('namespace').value){status.textContent='Namespace를 입력하세요.';return;}
  const version=++generation;controller?.abort();controller=new AbortController();const active=controller;const timer=setTimeout(()=>active.abort(),40000);
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
