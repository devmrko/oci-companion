'use strict';
(() => {
  const panel=document.getElementById('function-selection');
  if(!panel)return;
  const form=document.querySelector('form[action="/builds/functions/run"]');
  function show(source,commit,goal,profiles,runtime='Maven') {
    document.getElementById('function-selection-source').textContent=(source||'조회 전')+(commit?' · '+commit.slice(0,12):'');
    document.getElementById('function-selection-build').textContent=goal?runtime+' · '+goal+(profiles?' · '+profiles:''):'조회 전';
  }
  if(form){const sync=()=>show(form.elements.namedItem('source')?.value||form.elements.namedItem('sourceObject')?.value,form.elements.namedItem('commit')?.value,form.elements.namedItem('goal')?.value,form.elements.namedItem('profiles')?.value,form.dataset.runtime||'Maven');form.addEventListener('input',sync);form.addEventListener('change',sync);sync();}
  if(!form)document.addEventListener('function-build-progress',event=>{const r=event.detail;show(r.sourceBundle?.object||r.source,r.commit,r.goal,r.profiles,r.environment?.contract==='python-v1'||r.sourceBundle?'Python':'Maven');});
})();
