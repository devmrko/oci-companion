'use strict';
const toggle = document.getElementById('sidebar-toggle');
const sidebar = document.getElementById('app-sidebar');
const mobile = window.matchMedia('(max-width: 800px)');
let collapsed = false;
let mobileOpen = false;
function renderNavigation() {
  const expanded = mobile.matches ? mobileOpen : !collapsed;
  document.body.classList.toggle('sidebar-collapsed', !mobile.matches && collapsed);
  document.body.classList.toggle('sidebar-open', mobile.matches && mobileOpen);
  toggle.setAttribute('aria-expanded', String(expanded));
  toggle.setAttribute('aria-label', expanded ? '메뉴 접기' : '메뉴 펼치기');
  toggle.title = expanded ? '메뉴 접기' : '메뉴 펼치기';
}
toggle?.addEventListener('click', () => {
  if (mobile.matches) mobileOpen = !mobileOpen; else collapsed = !collapsed;
  renderNavigation();
});
mobile.addEventListener('change', renderNavigation);
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && mobile.matches && mobileOpen) {
    mobileOpen = false; renderNavigation(); toggle.focus();
  }
});
sidebar?.querySelectorAll('a[data-nav-path]').forEach(link => {
  if (link.dataset.navPath === window.location.pathname) {
    link.classList.add('is-active');
    link.setAttribute('aria-current', 'page');
  }
});
if (toggle) renderNavigation();

const globalScope = document.querySelector('.global-scope');
const sourceForms = [...document.querySelectorAll('form[action="/sources"]')];
const modifiedForms = new Set();
for (const form of sourceForms) {
  form.addEventListener('input', () => modifiedForms.add(form));
  form.addEventListener('change', () => modifiedForms.add(form));
}
globalScope?.addEventListener('submit', event => {
  if (modifiedForms.size && !window.confirm('작성 중인 소스 입력이 있습니다. 범위를 변경할까요?')) event.preventDefault();
});
