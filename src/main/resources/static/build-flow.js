(() => {
  const flow = document.querySelector('.build-flow');
  function activate(key) {
    if (!flow) return;
    flow.querySelectorAll('[data-step]').forEach(link => {
      const current = link.dataset.step === key;
      link.classList.toggle('is-current', current);
      if (current) link.setAttribute('aria-current', 'step');
      else link.removeAttribute('aria-current');
    });
  }
  flow?.querySelectorAll('[data-step]').forEach(link => link.addEventListener('click', () => activate(link.dataset.step)));
  document.querySelectorAll('[data-flow-step]').forEach(section => section.addEventListener('focusin', () => activate(section.dataset.flowStep)));
  const create = document.querySelector('#create-build-stage');
  create?.querySelector('[name=buildSource]').addEventListener('change', event => {
    const option = event.target.selectedOptions[0], external = option.dataset.provider && option.dataset.provider !== 'OCI';
    create.elements.sourceUrl.readOnly = !external;
    create.elements.sourceUrl.required = !!external;
    create.elements.sourceUrl.value = option.dataset.url || '';
    create.elements.branch.value = option.dataset.branch || 'main';
  });
})();
