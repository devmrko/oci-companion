(() => {
 const dialog=document.getElementById('term-dialog');
 document.querySelectorAll('[data-term]').forEach(link=>link.addEventListener('click',event=>{
  if(!dialog||typeof dialog.showModal!=='function')return;
  const sections=[...dialog.querySelectorAll('[data-term-content]')],selected=sections.find(s=>s.dataset.termContent===link.dataset.term);
  if(!selected)return;event.preventDefault();sections.forEach(s=>s.hidden=s!==selected);dialog.showModal();
 }));
 function revealHash(){
  let id;try{id=decodeURIComponent(location.hash.slice(1));}catch{return;}
  const target=id&&document.getElementById(id);if(!target)return;
  let ancestor=target;while(ancestor){if(ancestor.matches?.('details'))ancestor.open=true;ancestor=ancestor.parentElement;}
 }
 addEventListener('hashchange',revealHash);revealHash();
})();
