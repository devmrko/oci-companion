(() => {
  const form = document.querySelector('.build-execution');
  if (!form) return;
  let dirty = false;
  form.addEventListener('input', () => { dirty = true; });
  const rows = document.querySelector('#parameter-rows');
  document.querySelector('#add-parameter').addEventListener('click', () => {
    const row = document.createElement('tr');
    ['parameterName', 'parameterValue', 'parameterDescription'].forEach((name, i) => {
      const cell = row.insertCell(); cell.className = 'table-cell';
      const input = document.createElement('input'); input.name = name; input.className = 'ui-control';
      input.setAttribute('aria-label', ['파라미터 이름', '파라미터 값', '파라미터 설명'][i]);
      cell.append(input);
    });
    const cell = row.insertCell(); cell.className = 'table-cell';
    const button = document.createElement('button'); button.type = 'button'; button.className = 'ui-button remove-parameter'; button.textContent = '삭제';
    cell.append(button); rows.append(row); dirty = true;
  });
  rows.addEventListener('click', event => {
    if (event.target.closest('.remove-parameter')) { event.target.closest('tr').remove(); dirty = true; }
  });
  form.addEventListener('submit', event => {
    const action = event.submitter?.value;
    if (action === 'defaults' && !confirm('OCI 파이프라인 기본값 전체를 교체합니다. 저장할까요?')) { event.preventDefault(); return; }
    if (form.dataset.pending) { event.preventDefault(); return; }
    form.dataset.pending = 'true'; dirty = false;
    // Keep the clicked submitter enabled so its action is submitted.
    form.setAttribute('aria-busy', 'true');
  });
  window.addEventListener('beforeunload', event => { if (dirty) { event.preventDefault(); event.returnValue = ''; } });
})();
