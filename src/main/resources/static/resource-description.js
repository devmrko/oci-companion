document.querySelectorAll('select[data-resource-description]').forEach((select, index) => {
  const description = document.createElement('span');
  description.className = 'resource-description';
  description.id = `resource-description-${index}`;
  description.setAttribute('aria-live', 'polite');
  select.setAttribute('aria-describedby', [select.getAttribute('aria-describedby'), description.id].filter(Boolean).join(' '));
  const toolbar = select.closest('.scope-bar, .form-toolbar');
  if (toolbar) {
    description.classList.add('resource-description--row');
    toolbar.append(description);
  } else {
    select.after(description);
  }
  const update = () => {
    const option = select.selectedOptions[0];
    description.textContent = option?.value ? (option.dataset.description?.trim() || '설명 없음') : '';
  };
  select.addEventListener('change', update);
  update();
});
