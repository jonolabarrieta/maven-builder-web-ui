"""Exercise production group and selection code in headless Chrome."""
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]

def harness():
    template = (ROOT/'src/main/resources/templates/workspace-detail.html').read_text()
    selection = template[template.index('const selectedProjectIds = new Set();'):template.index('        let refreshInProgress = false;')]
    groups = (ROOT/'src/main/resources/static/js/workspace-groups.js').read_text()
    css = (ROOT/'src/main/resources/static/css/responsive.css').read_text()
    return '<!doctype html><html><head><style>' + css + '</style></head>' + '''<body>
    <div id="workspace-git-status" data-url="/workspaces/5/git-status-table"></div>
    <form class="group-refresh-form" action="http://localhost/workspaces/5/groups/10/refresh"><button>Refresh group</button></form><div id="selected-status"></div><div id="bulk-actions"></div><details id="bulk-more-menu"></details><input type="checkbox" id="select-all">
    <table><tbody id="project-table-body">
      <tr class="workspace-group-header" data-group-id="10" data-block-key="g:10"><td>
        <input type="checkbox" class="group-checkbox" data-group-id="10"><button class="group-disclosure" data-group-id="10" data-group-name="Shared">Collapse</button>
        <form class="group-action-form" data-group-id="10" data-group-name="Shared"><select name="action"><option value="discard">discard</option></select><input name="branch"><input type="checkbox" name="createNew"><input type="checkbox" name="includeUntracked"><input type="checkbox" name="selectedOnly" checked><button>Run</button></form></td></tr>
      <tr data-id="1" data-context-group-id="10" data-consumer-id="5" data-block-key="g:10"><td><input class="project-checkbox" value="1"><button data-path="/repo/a">X</button></td></tr>
      <tr data-id="2" data-context-group-id="10" data-consumer-id="5" data-block-key="g:10"><td><input class="project-checkbox" value="2"><button data-path="/repo/b">Y</button></td></tr>
      <tr class="workspace-group-end" data-group-id="10"><td>End of Shared</td></tr>
      <tr class="workspace-group-header" data-group-id="20" data-block-key="g:20"><td><input type="checkbox" class="group-checkbox" data-group-id="20"><button class="group-disclosure" data-group-id="20">Collapse</button></td></tr>
      <tr data-id="3" data-context-group-id="20" data-consumer-id="5" data-block-key="g:20" data-imported="true"><td><input class="project-checkbox" value="3"><button data-move="1">Move</button></td></tr>
    </tbody></table><pre id="results"></pre>
    <script>
      localStorage.clear(); window.moveProject=()=>{};window.htmx={ajax:async()=>{}};
      window.ConfirmationModal={show:o=>{window.confirmation=o},error:o=>{window.lastError=o}};
      window.fetch=async(input, options)=>{window.lastFetch={input,options};return {ok:true,text:async()=>''}};
    </script><script>''' + selection + '</script><script>' + groups + '''</script><script>
    document.addEventListener('DOMContentLoaded',async()=>{
      const checks={};try {
        const group=document.querySelector('.group-checkbox[data-group-id="10"]');group.checked=true;group.dispatchEvent(new Event('change',{bubbles:true}));
        checks.groupSelection=selectedProjectIds.size===2&&document.getElementById('selected-status').textContent==='2 projects selected';
        const disclosure=document.querySelector('.group-disclosure[data-group-id="10"]');disclosure.click();
        checks.collapseKeepsSelection=document.querySelector('tr[data-id="1"]').hidden&&selectedProjectIds.size===2&&disclosure.getAttribute('aria-expanded')==='false';
        checks.collapseHidesGroupEnd=document.querySelector('.workspace-group-end[data-group-id="10"]').hidden;
        WorkspaceGroups.initialize();checks.collapseRestores=document.querySelector('tr[data-id="2"]').hidden;
        checks.collapseRestoresGroupEnd=document.querySelector('.workspace-group-end[data-group-id="10"]').hidden;
        checks.hiddenReallyHidden=getComputedStyle(document.querySelector('tr[data-id="1"]')).display==='none';
        checks.namedDisclosure=disclosure.getAttribute('aria-label')==='Expand group Shared';
        checks.headerSpansColumns=getComputedStyle(document.querySelector('.workspace-group-header')).gridColumnEnd==='-1';
        const first=document.querySelector('.project-checkbox[value="1"]');first.checked=false;first.dispatchEvent(new Event('change',{bubbles:true}));
        checks.mixed=group.indeterminate&&!group.checked&&selectedProjectIds.size===1;
        document.querySelector('.group-action-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
        checks.confirmation=confirmation.requireTyped==='Shared'&&confirmation.message.includes('other workspaces');
        await confirmation.onConfirm(); await new Promise(resolve=>setTimeout(resolve,0));
        checks.selectedScope=lastFetch.input==='/workspaces/5/groups/10/action'&&lastFetch.options.body.getAll('projectIds').join(',')==='2';
        const parameters={};const detail={path:'/projects/1/checkout',elt:first,parameters};document.dispatchEvent(new CustomEvent('htmx:configRequest',{detail}));
        checks.htmxContext=parameters.consumerId==='5'&&parameters.contextGroupId==='10';
        await fetch('/projects/2/git/discard',{method:'POST'});checks.fetchContext=lastFetch.input.includes('consumerId=5')&&lastFetch.input.includes('contextGroupId=10');
        const all=document.getElementById('select-all');all.checked=true;toggleAllCheckboxes(all);
        checks.selectAllHidden=selectedProjectIds.size===3&&document.querySelector('.project-checkbox[value="2"]').checked;
        const refresh=document.querySelector('.group-refresh-form');refresh.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await new Promise(resolve=>setTimeout(resolve,0));
        checks.refreshKeepsSelection=selectedProjectIds.size===3&&document.querySelector('tr[data-id="1"]').hidden&&!refresh.querySelector('button').disabled;
        checks.refreshContext=lastFetch.options.headers['X-Group-Refresh']==='true';
        checks.importedReadOnly=document.querySelector('tr[data-id="3"] [data-move]').disabled;
        document.querySelector('tr[data-id="2"]').dataset.contextGroupId='99';reconcileProjectSelection();
        checks.changedIdentityRemoved=!selectedProjectIds.has('2')&&selectedProjectIds.has('1');
        document.querySelector('tr[data-id="3"]').remove();reconcileProjectSelection();checks.removedMemberReconciled=!selectedProjectIds.has('3');
      }catch(e){checks.error=e.stack}document.getElementById('results').textContent=JSON.stringify(checks);
    });</script></body></html>'''

def main():
    with tempfile.TemporaryDirectory(prefix='workspace-groups-browser-') as directory:
        path=Path(directory)/'test.html';path.write_text(harness())
        result=subprocess.run(['google-chrome','--headless','--no-sandbox','--disable-dev-shm-usage','--disable-gpu','--disable-background-networking','--window-size=900,900',f'--user-data-dir={directory}/profile','--virtual-time-budget=2000','--dump-dom',path.as_uri()],capture_output=True,text=True,check=True,timeout=25)
        match=re.search(r'<pre id="results">(.*?)</pre>',result.stdout,re.S)
        if not match: raise AssertionError(result.stdout[-2000:])
        checks=json.loads(match.group(1));print(json.dumps(checks,indent=2))
        assert checks and all(value is True for value in checks.values()),checks
if __name__=='__main__':main()
