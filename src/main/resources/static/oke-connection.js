(()=>{
 const f=document.querySelector('#oke-connection-form');if(!f)return;
 const field=n=>f.elements.namedItem(n),status=f.querySelector('[data-oke-status]'),deployment=field('deployment');let generation=0;
 function clear(){generation++;deployment.replaceChildren(new Option('조회 후 선택',''));status.textContent='';}
 for(const n of ['cluster','endpoint'])field(n).addEventListener('change',()=>{clear();field('namespace').value='';document.querySelector('#oke-namespaces').replaceChildren();});
 field('namespace').addEventListener('input',clear);
 async function load(kind){const version=++generation;const params=new URLSearchParams();for(const n of ['region','compartment','cluster','endpoint'])params.set(n,field(n).value);
  if(!field('cluster').value){status.textContent='클러스터를 선택하세요.';return;}
  if(kind==='deployments'){if(!field('namespace').value){status.textContent='namespace를 입력하세요.';return;}params.set('namespace',field('namespace').value);deployment.replaceChildren(new Option('조회 중',''));}
  status.textContent='조회 중…';
  try{const response=await fetch('/agents/connections/oke/options?'+params,{headers:{Accept:'application/json'}});if(!response.ok||response.redirected)throw Error();const data=await response.json();if(version!==generation)return;
   if(data.error){status.textContent=data.error;return;}
   const list=kind==='namespaces'?document.querySelector('#oke-namespaces'):deployment;list.replaceChildren();if(kind==='deployments')list.add(new Option('선택',''));for(const value of data.items)list.append(new Option(value,value));status.textContent=data.items.length+'개 조회됨';
  }catch(e){if(version===generation)status.textContent='조회 실패 · 로그인 상태와 연결을 확인하세요.';}
 }
 f.querySelector('[data-namespaces]').addEventListener('click',()=>load('namespaces'));f.querySelector('[data-deployments]').addEventListener('click',()=>load('deployments'));
})();
