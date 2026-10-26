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
