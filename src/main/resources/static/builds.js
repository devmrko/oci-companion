(() => {
  const picker = document.querySelector('#pipeline-picker');
  picker?.querySelector('[name=project]').addEventListener('change', () => {
    picker.querySelector('[name=pipeline]').value = ''; picker.requestSubmit();
  });
  picker?.querySelector('[name=pipeline]').addEventListener('change', () => picker.requestSubmit());
  const form = document.querySelector('.build-execution');
  if (!form) return;
  let dirty = false, loading = false, sequence = 0;
  const source = form.querySelector('#build-source'), branch = form.querySelector('#build-branch'), commit = form.querySelector('#build-commit');
  const status = document.querySelector('#source-status'), run = form.querySelector('#build-run');
  document.querySelectorAll('.build-destination-form').forEach(destination => destination.addEventListener('input', () => {
    dirty = true;form.dataset.destinationDirty='true';document.querySelector('#destination-unsaved').hidden=false;
  }));
  const runnable = !run.disabled, review = document.querySelector('#build-review');
  document.querySelectorAll('.build-execution,.build-rules,.build-defaults').forEach(f => f.addEventListener('input', () => { dirty = true; }));
  function options(select, values, empty) {
    select.replaceChildren();
    if (empty !== null) select.add(new Option(empty, ''));
    values.forEach(v => select.add(new Option(v.label, v.value)));
  }
  async function loadSource(wantedBranch = '', wantedCommit = '') {
    const request = ++sequence;
    if (!source.value) {
      options(branch, [], '파이프라인 기본값 사용');options(commit, [], '선택 브랜치 최신 커밋');
      branch.disabled = commit.disabled = true;branch.required = false;loading = false;run.disabled = !runnable;
      status.textContent = '모든 소스의 기본 브랜치 최신 커밋으로 실행합니다.';return;
    }
    loading = true;run.disabled = true;branch.disabled = commit.disabled = true;
    status.textContent = '브랜치·커밋 조회 중…';
    try {
      const query = new URLSearchParams({ticket:form.elements.ticket.value,source:source.value,branch:wantedBranch});
      const response = await fetch('/builds/source-options?' + query, {headers:{Accept:'application/json'}});
      if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw Error();
      const data = await response.json();
      if (request !== sequence) return;
      options(branch,data.branches,null);branch.value = data.branch;branch.disabled = false;branch.required = true;
      options(commit,data.commits,'선택 브랜치 최신 커밋');commit.disabled = data.external;
      if (wantedCommit) {
        if (!data.commits.some(c => c.value === wantedCommit)) throw Error('프리셋 커밋이 조회 목록에 없습니다. 소스를 다시 선택하세요.');
        commit.value = wantedCommit;
      }
      if (wantedBranch && wantedBranch !== data.branch) throw Error('프리셋 브랜치가 없습니다. 소스를 다시 선택하세요.');
      if (!data.branches.length) throw Error('브랜치가 없습니다.');
      status.textContent = data.external ? '외부 저장소: OCI에 등록된 브랜치만 선택 가능합니다. 외부 Git 인증 연동 전에는 커밋 목록을 조회할 수 없습니다.' : '선택 브랜치의 최근 커밋 최대 50개 · 생략하면 최신 커밋 사용';
      loading = false;run.disabled = !runnable;
    } catch(error) {
      if (request !== sequence) return;
      loading = true;run.disabled = true;
      status.textContent = error.message || '조회 실패 · 권한과 연결을 확인하거나 파이프라인 기본값을 선택하세요.';
    }
  }
  source.addEventListener('change', () => loadSource());
  branch.addEventListener('change', () => loadSource(branch.value));
  if (source.value) loadSource(source.dataset.savedBranch,source.dataset.savedCommit);
  const rows = document.querySelector('#parameter-rows');
  document.querySelector('#add-parameter').addEventListener('click', () => {
    const row = document.createElement('tr');
    ['parameterName','parameterValue','parameterDescription'].forEach((name,i) => {
      const cell = row.insertCell();cell.className = 'table-cell';
      const input = document.createElement('input');input.name = name;input.className = 'ui-control';
      input.setAttribute('aria-label',['파라미터 이름','기본값','설명'][i]);cell.append(input);
    });
    const cell = row.insertCell();cell.className = 'table-cell';
    const button = document.createElement('button');button.type = 'button';button.className = 'ui-button remove-parameter';button.textContent = '삭제';
    cell.append(button);rows.append(row);dirty = true;
  });
  rows.addEventListener('click', e => { if (e.target.closest('.remove-parameter')) { e.target.closest('tr').remove();dirty = true; } });
  function addSummary(label,value) {
    const item = document.createElement('div'), title = document.createElement('dt'), content = document.createElement('dd');
    title.textContent = label;content.textContent = value;item.append(title,content);document.querySelector('#build-review-values').append(item);
  }
  document.querySelector('#close-build-review').addEventListener('click', () => review.close());
  document.querySelectorAll('.build-execution,.build-rules,.build-defaults').forEach(f => f.addEventListener('submit', event => {
    const action = event.submitter?.value;
    if (action === 'run') {
      if (loading || form.dataset.destinationDirty) { event.preventDefault();document.querySelector('#destination-unsaved')?.scrollIntoView({block:'center'});return; }
      if (!event.submitter.dataset.confirmed) {
        event.preventDefault();document.querySelector('#build-review-values').replaceChildren();
        addSummary('파이프라인',form.dataset.pipeline);
        const global = document.querySelector('.global-scope');
        if (global) ['region','compartment'].forEach((key,i) => addSummary(['리전','구획'][i],global.querySelector('[name='+key+']')?.selectedOptions[0]?.textContent || ''));
        addSummary('실행 목적',form.elements.purpose.selectedOptions[0].textContent);
        addSummary('실행 이름',form.elements.runName.value || 'OCI 자동 생성');
        document.querySelectorAll('.destination-current').forEach(item => addSummary('산출물 · '+item.dataset.output,item.textContent.trim()));
        if(!document.querySelector('.destination-current'))addSummary('산출물 저장소','Registry 전달 위치 없음 또는 조회 불가');
        addSummary('소스',source.selectedOptions[0].textContent);
        if(source.value) {addSummary('브랜치',branch.value);addSummary('커밋',commit.value || '최신 커밋');}
        form.querySelectorAll('.build-parameter').forEach(field => addSummary(field.dataset.label,field.querySelector('[name=parameterValue]').value || '(빈 값)'));
        review.showModal();return;
      }
    }
    if(action === 'defaults' && !confirm('OCI 파이프라인 기본 파라미터 전체를 교체합니다. 저장할까요?')) {event.preventDefault();return;}
    if(f.dataset.pending) {event.preventDefault();return;}
    f.dataset.pending = 'true';dirty = false;f.setAttribute('aria-busy','true');
  }));
  document.querySelectorAll('.build-destination-form').forEach(destination => destination.addEventListener('submit',()=>{dirty=false;}));
  window.addEventListener('beforeunload', event => {if(dirty) {event.preventDefault();event.returnValue='';}});
})();
