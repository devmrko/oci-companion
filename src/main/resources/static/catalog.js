'use strict';
const form = document.getElementById('refresh-form');
const interval = Number(form?.dataset.interval || 0);
if ([60, 300, 900].includes(interval)) {
  let timer;
  const schedule = () => {
    clearTimeout(timer);
    if (!document.hidden) timer = setTimeout(() => form.requestSubmit(), interval * 1000);
  };
  document.addEventListener('visibilitychange', schedule);
  // Avoid losing source form edits to an automatic refresh.
  document.addEventListener('input', () => clearTimeout(timer));
  schedule();
}
// Expanded queries are explicit and bounded; update each compartment as it completes.
if (form && form.elements.mode?.value !== 'selected') {
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('button');
    if (button.disabled) return;
    const rows = [...document.querySelectorAll('.scope-results tr[data-compartment]')];
    const progress = document.getElementById('refresh-progress');
    const queue = [...rows]; let completed = 0; let failed = 0; let transportFailed = 0;
    button.disabled = true;
    document.querySelectorAll('.scope-bar button,.scope-bar select').forEach(e => e.disabled = true);
    const update = () => { progress.textContent = `조회 ${completed}/${rows.length} · 실패 ${failed}`; };
    update();
    async function worker() {
      while (queue.length) {
        const row = queue.shift();const cells = row.querySelectorAll('td');
        cells[3].textContent = '조회 중';
        const data = new URLSearchParams(new FormData(form));data.set('target', row.dataset.compartment);
        try {
          const response = await fetch('/catalog/refresh-one', {method:'POST', body:data, headers:{Accept:'application/json'}});
          if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw new Error();
          const result = await response.json();
          cells[1].textContent = result.updatedAt || '조회 이력 없음';
          cells[2].textContent = `${result.elapsedMs} ms`;
          cells[3].textContent = result.error || '조회 완료';
          if (result.error) failed++;
        } catch (_) { failed++;transportFailed++;cells[3].textContent = '조회 실패 · 연결·세션 확인'; }
        completed++; update();
      }
    }
    await Promise.all(Array.from({length:Math.min(4,rows.length)}, worker));
    if (transportFailed) {
      button.disabled = false;
      document.querySelectorAll('.scope-bar button,.scope-bar select').forEach(e => e.disabled = false);
      const link = document.createElement('a');link.href = window.location.href;link.textContent = ' 목록 다시 표시';progress.append(link);
    } else window.location.reload();
  });
}
