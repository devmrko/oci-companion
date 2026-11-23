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
const route=location.pathname, query=new URLSearchParams(location.search), tab=query.get('tab'), view=query.get('view');
function navigationKey(){
 if(route==='/readiness')return 'readiness';
 if(route==='/')return 'resources';
 if(route.startsWith('/sources'))return 'sources';
 if(route==='/builds'&&tab==='registry')return query.get('kind')==='CONTAINER'?'images':'files';
 if(route==='/builds'&&tab==='projects')return 'projects';
 if(route==='/builds'&&tab==='topics')return 'topics';
 if(route.startsWith('/builds/automation'))return 'automation';
 if(route==='/builds/insights'||route==='/builds'&&(tab==='history'||!tab&&query.has('run'))||route==='/builds/tasks'&&view==='history')return 'history';
 if(route==='/builds'&&tab==='pipelines'||route.startsWith('/builds/settings')||route.startsWith('/builds/project-resources'))return 'pipelines';
 if(route.startsWith('/builds/functions')||route==='/builds/tasks'&&view==='environments')return 'builders';
 if(route.startsWith('/builds'))return 'tasks';
 if(route.startsWith('/deployments/runs')||route.startsWith('/deployments/rollback')||route.startsWith('/deployments/observe'))return 'deploy-history';
 if(route.startsWith('/deployments'))return 'deployments';
 if(route.startsWith('/environments'))return 'environments';
 if(route.startsWith('/agents'))return 'monitoring';
 if(route.startsWith('/backups'))return 'backups';
 if(route.startsWith('/guides'))return 'guides';
 if(route.startsWith('/settings/profiles'))return 'profiles';
 return '';
}
const activeKey=navigationKey();
sidebar?.querySelectorAll('a[data-nav-key]').forEach(link=>{
 if(link.dataset.navKey===activeKey){link.classList.add('is-active');link.setAttribute('aria-current','page');}
});
sidebar?.querySelectorAll('.nav-section').forEach(group=>{
 group.open=!!group.querySelector('[aria-current="page"]');
 group.querySelector('summary').addEventListener('click',()=>{if(collapsed&&!mobile.matches){collapsed=false;renderNavigation();}});
});
if (toggle) renderNavigation();

const topbar = document.querySelector('.app-topbar');
if (topbar) {
  new ResizeObserver(() => {
    document.documentElement.style.setProperty('--topbar-height', `${topbar.getBoundingClientRect().height}px`);
  }).observe(topbar);
}

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
