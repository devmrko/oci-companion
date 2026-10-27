(() => {
 const type=document.querySelector('#artifact-type'),repository=document.querySelector('#artifact-repository'),path=document.querySelector('#artifact-path');
 if(!type||!repository)return;
 const selected=repository.selectedOptions[0];if(selected?.dataset.kind)type.value=selected.dataset.kind;
 function change(){
  [...repository.options].forEach(option=>{const other=option.dataset.kind&&option.dataset.kind!==type.value;option.hidden=!!other;option.disabled=!!other;});
  if(repository.selectedOptions[0]?.disabled)repository.value='';
  path.required=type.value==='BINARY';path.disabled=type.value!=='BINARY';
 }
 type.addEventListener('change',change);change();
})();
