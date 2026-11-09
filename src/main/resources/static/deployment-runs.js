(() => {
  const control = document.getElementById('deployment-refresh');
  if (!control) return;
  const active = ['ACCEPTED', 'IN_PROGRESS', 'CANCELING'].includes(control.dataset.state);
  const paging = new URLSearchParams(location.search).has('logPage');
  const key = 'deployment-refresh:' + control.dataset.run;
  let preference = null;
  try { preference = sessionStorage.getItem(key); } catch (_) {}
  control.checked = active && !paging && preference !== 'off';
  control.disabled = !active || paging;
  control.addEventListener('change', () => {
    try { sessionStorage.setItem(key, control.checked ? 'on' : 'off'); } catch (_) {}
  });
  document.addEventListener('change', event => {
    if (event.target !== control && event.target.matches('input, select, textarea')) control.checked = false;
  });
  setInterval(() => {
    if (control.checked && !document.hidden) location.reload();
  }, 15000);
})();
