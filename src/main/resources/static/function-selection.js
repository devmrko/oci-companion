'use strict';
(() => {
  const panel=document.getElementById('function-selection');
  if(!panel)return;
  const form=document.querySelector('form[action="/builds/functions/run"]');
  function show(source,commit,goal,profiles) {
    document.getElementById('function-selection-source').textContent=(source||'조회 전')+(commit?' · '+commit.slice(0,12):'');
    document.getElementById('function-selection-build').textContent=goal?'Maven · '+goal+(profiles?' · '+profiles:''):'조회 전';
  }
  if(form){const sync=()=>show(...['source','commit','goal','profiles'].map(name=>form.elements.namedItem(name).value));form.addEventListener('input',sync);form.addEventListener('change',sync);sync();}
  if(!form)document.addEventListener('function-build-progress',event=>{const r=event.detail;show(r.source,r.commit,r.goal,r.profiles);});
})();
