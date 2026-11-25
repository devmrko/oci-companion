(() => {
  if (document.querySelector('[data-arm-running]')) setTimeout(() => location.reload(), 3000);
  const copy = document.getElementById('iam-copy-command');
  if (copy) copy.addEventListener('click', async () => {
    const command = document.getElementById('iam-command');
    const status = document.getElementById('iam-copy-status');
    try { await navigator.clipboard.writeText(command.value); status.textContent = '명령을 복사했습니다.'; }
    catch { command.select(); status.textContent = '자동 복사를 사용할 수 없습니다. 선택한 명령을 복사하세요.'; }
  });
})();
