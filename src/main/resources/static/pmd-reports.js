(() => {
  function element(tag, text, cls) {
    const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(cls)node.className=cls;return node;
  }
  window.CompanionPmdReports={render(reports=[]) {
    const root=document.getElementById('pmd-reports');if(!root)return;root.replaceChildren();
    if(!reports.length){root.append(element('p','PMD 보고서 없음 · 압축 파일에 PMD XML이 없습니다.','small muted'));return;}
    for(const report of reports){
      const panel=element('details',undefined,'card');panel.open=true;
      panel.append(element('summary',`PMD 보고서 · 위반 ${report.violations} · 분석 오류 ${report.errors}`));
      panel.append(element('p',`${report.path} · PMD ${report.version || '버전 없음'} · 제외된 위반 ${report.suppressed}`,'small muted'));
      if(report.findings.length){
        const scroll=element('div',undefined,'table-scroll'),table=element('table',undefined,'data-table');
        const head=element('thead'),header=element('tr');for(const label of ['파일','줄','규칙','우선순위','내용'])header.append(element('th',label,'table-heading'));head.append(header);table.append(head);
        const body=element('tbody');for(const item of report.findings){const row=element('tr');for(const key of ['file','line','rule','priority','message'])row.append(element('td',item[key],'table-cell'));body.append(row);}table.append(body);scroll.append(table);panel.append(scroll);
      }
      if(report.violations>report.findings.length)panel.append(element('p','목록은 500건까지 표시합니다. 전체 내용은 XML 원문을 확인하세요.','small muted'));
      if(report.errors)panel.append(element('pre',report.problems.join('\n\n'),'code-block'));
      const raw=element('details');raw.append(element('summary','XML 원문 보기'),element('pre',report.xml,'code-block'));panel.append(raw);
      const actions=element('div',undefined,'form-toolbar'),download=element('button','XML 다운로드','ui-button');download.type='button';
      download.addEventListener('click',()=>{const url=URL.createObjectURL(new Blob([report.xml],{type:'application/xml;charset=utf-8'}));const a=element('a');a.href=url;a.download='pmd.xml';document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);});
      actions.append(download);panel.append(actions);root.append(panel);
    }
  }};
})();
