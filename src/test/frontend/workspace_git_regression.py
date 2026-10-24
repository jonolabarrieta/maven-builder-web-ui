"""Exercise the production Git page and script in Chrome with delayed transport responses."""
import html
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


def build_harness():
    template = (ROOT / 'src/main/resources/templates/workspace-git.html').read_text()
    template = re.sub(r'<script[^>]*src=[^>]*></script>', '', template)
    template = re.sub(r'<link[^>]+>', '', template)
    template = re.sub(r'<body[^>]*>', '<body class="git-page" data-workspace-id="1" data-workspace-name="Fixture">', template)
    template = template.replace('</body>', '')
    stubs = r'''
      const statuses = new Map([
        ['r1', {branch:'main',upstream:'origin/main',ahead:0,behind:0,version:'v1',stagedVersion:'s1',files:[{path:'file.txt',index:'.',working:'M',kind:'text'}]}],
        ['r2', {branch:'feature',upstream:null,version:'v2',stagedVersion:'s2',files:[]}]
      ]);
      const diff = {path:'file.txt',area:'changes',version:'v1',kind:'text',partial:true,lines:[
        {id:'l0',type:'-',oldNumber:1,newNumber:null,text:'old',newline:true,block:0},
        {id:'l1',type:'+',oldNumber:null,newNumber:1,text:'new',newline:true,block:0},
        {id:'l2',type:'+',oldNumber:null,newNumber:2,text:'extra',newline:true,block:0}
      ]};
      const calls = []; const held = []; let holdStatuses = false; let commitHeld; let batchFails = false;
      const ok = body => ({ok:true,status:200,json:async()=>structuredClone(body)});
      const discovery = {id:'scan',state:'done',outcomes:[{repositoryId:'discovery',state:'success',message:'Completed'}],discovery:{repositories:[{id:'r1',name:'api',path:'api'},{id:'r2',name:'web',path:'web'}],warnings:[],complete:true}};
      window.fetch = async (url, options={}) => {
        const data = options.body ? JSON.parse(options.body) : null; calls.push({url,data});
        if (url.endsWith('/repositories')) return ok(discovery);
        const id = url.includes('/r2/') ? 'r2' : 'r1';
        if (url.endsWith('/status')) {
          if (holdStatuses) return await new Promise(resolve=>held.push({id,resolve}));
          return ok(statuses.get(id));
        }
        if (url.includes('/diff?')) return ok({...diff,area:new URL(url,'http://test').searchParams.get('area')});
        if (url.endsWith('/stage') || url.endsWith('/unstage')) return ok(statuses.get(id));
        if (url.endsWith('/commits')) return ok({id:'batch-'+calls.length,state:'done',outcomes:data.targets.map(t=>({repositoryId:t.id,state:batchFails && t.id==='r2'?'failed':'success',message:batchFails && t.id==='r2'?'Hook refused':'Completed'}))});
        if (url.endsWith('/commit')) return await new Promise(resolve=>commitHeld=()=>resolve(ok({id:'commit',state:'done',outcomes:[{repositoryId:'r1',state:'success',message:'Completed'}]})));
        if (url.endsWith('/actions')) return ok({id:'action-'+calls.length,state:'done',outcomes:data.targets.map(t=>({repositoryId:t.id,state:'success',message:'Completed'}))});
        throw new Error('Unexpected route '+url);
      };
      window.onerror = message => { document.getElementById('test-results').textContent=JSON.stringify({error:String(message)}); };
    '''
    tests = r'''
      const checks = {}; const flush = () => new Promise(resolve=>setTimeout(resolve,25));
      async function run() {
        const W=WorkspaceGit; const $=id=>document.getElementById(id);
        await flush(); await flush();
        checks.nonMavenDiscovery = W.state.repositories.length===2 && $('git-repositories').textContent.includes('api');
        checks.initialDiff = $('git-diff').querySelectorAll('[data-line]').length===3 && $('git-comparison-label').textContent.includes('Working tree');
        const line=$('git-diff').querySelector('[data-line="l1"]'); line.checked=true; line.dispatchEvent(new Event('change',{bubbles:true}));
        checks.singleLineSelection = W.state.lines.size===1 && !$('git-stage-lines').disabled;
        $('git-diff-mode').value='unified'; $('git-diff-mode').dispatchEvent(new Event('change')); await flush();
        checks.selectionSurvivesMode = $('git-diff').querySelector('[data-line="l1"]').checked;
        await W.mutate(true);
        const stageCall=calls.find(c=>c.url.endsWith('/stage'));
        checks.partialStagePayload = stageCall.data.lines.length===1 && stageCall.data.lines[0]==='l1' && stageCall.data.version==='v1';
        statuses.get('r1').files=[{path:'file.txt',index:'M',working:'M',kind:'text'}];
        await W.refreshStatuses(); await W.chooseFile({path:'file.txt',area:'staged'});
        const stagedLine=$('git-diff').querySelector('[data-line="l1"]'); stagedLine.checked=true; stagedLine.dispatchEvent(new Event('change',{bubbles:true}));
        await W.mutate(true);
        const unstage=calls.filter(c=>c.url.endsWith('/unstage')).at(-1);
        checks.partialUnstagePayload=unstage.data.area==='staged' && unstage.data.lines.includes('l1');
        await W.chooseFile({path:'file.txt',area:'changes'}); await W.mutate(false);
        checks.wholeFileStage=calls.filter(c=>c.url.endsWith('/stage')).at(-1).data.lines===null;
        $('git-commit-message').value='draft one'; $('git-commit-message').dispatchEvent(new Event('input'));
        await W.chooseRepository('r2'); $('git-commit-message').value='draft two'; $('git-commit-message').dispatchEvent(new Event('input'));
        await W.chooseRepository('r1'); checks.repositoryDrafts = $('git-commit-message').value==='draft one';
        holdStatuses=true;
        const oldRefresh=W.refreshStatuses(); await flush(); const old=held.splice(0);
        const newRefresh=W.refreshStatuses(); await flush(); const newer=held.splice(0);
        newer.forEach(item=>item.resolve(ok({...statuses.get(item.id),branch:'newer'}))); await newRefresh;
        old.forEach(item=>item.resolve(ok({...statuses.get(item.id),branch:'older'}))); await oldRefresh; holdStatuses=false;
        checks.staleResponsesIgnored=W.state.statuses.get('r1').branch==='newer' && $('git-commit-message').value==='draft one';
        const before=calls.filter(c=>c.url.endsWith('/actions')).length;
        W.actionDialog('discard',['r1']); await flush();
        checks.typedDiscardRequired=$('confirmation-modal-primary').disabled;
        $('confirmation-modal-cancel').click(); await flush();
        checks.cancelDiscardSendsNothing=calls.filter(c=>c.url.endsWith('/actions')).length===before;
        W.actionDialog('stash',['r1']); await flush();
        checks.stashUnchecked=!$('confirmation-modal-checkbox-input').checked;
        $('confirmation-modal-checkbox-input').checked=true; $('confirmation-modal-primary').click(); await flush();
        const stash=calls.filter(c=>c.url.endsWith('/actions')).at(-1);
        checks.stashPayload=stash.data.includeUntracked===true && stash.data.confirmation==='confirmed';
        W.actionDialog('discard',['r1'],'file.txt'); await flush();
        statuses.get('r1').version='v-after-dialog'; $('confirmation-modal-primary').click(); await flush();
        const discard=calls.filter(c=>c.url.endsWith('/actions')).at(-1);
        checks.discardKeepsConfirmedSnapshot=discard.data.targets[0].version==='v1' && discard.data.path==='file.txt';
        W.actionDialog('fetch',['r1','r2']); await flush();
        checks.selectedRepositoryActions=calls.filter(c=>c.url.endsWith('/actions')).at(-1).data.targets.length===2;
        const current=statuses.get('r1'); current.files=[{path:'file.txt',index:'M',working:'M',kind:'text'}];
        await W.refreshStatuses();
        $('git-commit-form').dispatchEvent(new Event('submit',{cancelable:true})); await flush();
        $('git-commit-message').value='edited while committing'; $('git-commit-message').dispatchEvent(new Event('input'));
        commitHeld(); await flush(); await flush();
        checks.commitPreservesNewDraft=$('git-commit-message').value==='edited while committing';
        const commit=calls.find(c=>c.url.endsWith('/commit'));
        checks.commitSnapshot=commit.data.message==='draft one' && commit.data.version==='s1';
        document.body.classList.add('git-show-diff'); $('git-back').click(); checks.mobileBack=!document.body.classList.contains('git-show-diff');
        const input=$('git-diff').querySelector('[data-line]'); input.focus(); input.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));
        checks.keyboardLineNavigation=document.activeElement!==input;

        statuses.get('r2').files=[{path:'web.txt',index:'.',working:'M',kind:'text'}];
        await W.refreshStatuses(); await W.chooseRepository('r1');
        const selectedCheckbox=(id,area,path)=>W.state.views.get(id).buttons.get(area+'\0'+path).checkbox;
        // Construct the NUL key without relying on HTML parsing of embedded NUL characters.
        const fileChoice=(id,area,path)=>[...W.state.views.get(id).buttons.entries()].find(([key])=>key.startsWith(area) && key.endsWith(path))[1].checkbox;
        fileChoice('r1','changes','file.txt').click();
        checks.fileCheckboxSelectsCurrent=W.state.files.get('r1').get('file.txt').area==='changes' && W.state.commitMode==='selected-current';
        W.state.views.get('r1').toggle.click(); await W.refreshStatuses();
        checks.repositoryCollapseSurvivesRefresh=!W.state.expanded.has('r1') && W.state.views.get('r1').toggle.getAttribute('aria-expanded')==='false' && W.state.files.get('r1').size===1;
        await W.chooseRepository('r2'); fileChoice('r2','changes','web.txt').click();
        checks.filesAcrossRepositories=W.commitTargets().length===2 && W.state.commitMode==='selected-all';
        $('git-commit-message').value='Batch common message'; $('git-commit-message').dispatchEvent(new Event('input'));
        await W.chooseRepository('r1'); checks.batchDraftAcrossRepositories=$('git-commit-message').value==='Batch common message';
        W.state.views.get('r1').toggle.click();
        fileChoice('r1','staged','file.txt').click();
        checks.oneSourcePerFile=W.state.files.get('r1').size===1 && !fileChoice('r1','changes','file.txt').checked && fileChoice('r1','staged','file.txt').checked;
        W.state.views.get('r1').groups.get('Changes').toggle.click(); await W.refreshStatuses();
        checks.groupCollapseSurvivesRefresh=W.state.views.get('r1').groups.get('Changes').list.hidden;
        W.setCommitMode('selected-current'); checks.currentCommitTargetsOnlyCurrent=W.commitTargets().length===1 && W.commitTargets()[0].id==='r1';
        $('git-commit-message').value='Current selected file'; $('git-commit-message').dispatchEvent(new Event('input'));
        $('git-commit-form').dispatchEvent(new Event('submit',{cancelable:true})); await flush(); await flush();
        const single=calls.filter(c=>c.url.endsWith('/commits')).at(-1);
        checks.currentSelectedCommitPayload=single.data.targets.length===1 && single.data.targets[0].files[0].area==='staged';
        checks.successClearsOnlySuccessfulSelections=!W.state.files.get('r1').size && W.state.files.get('r2').size===1;
        fileChoice('r1','staged','file.txt').click(); W.setCommitMode('selected-all'); batchFails=true;
        $('git-commit-message').value='Partial batch'; $('git-commit-message').dispatchEvent(new Event('input'));
        $('git-commit-form').dispatchEvent(new Event('submit',{cancelable:true})); await flush(); await flush();
        const batch=calls.filter(c=>c.url.endsWith('/commits')).at(-1);
        checks.multipleCommitPayload=batch.data.targets.length===2 && batch.data.message==='Partial batch';
        checks.failedSelectionsAndDraftRetained=!W.state.files.get('r1').size && W.state.files.get('r2').size===1 && $('git-commit-message').value==='Partial batch';
        for(const id of ['r1','r2']) { const checkbox=W.state.views.get(id).checkbox; if(!checkbox.checked) checkbox.click(); }
        checks.pushSelectedVisible=!$('git-push-selected').disabled && $('git-push-selected').textContent.includes('(2)');
        $('git-push-selected').click(); await flush(); await flush();
        const push=calls.filter(c=>c.url.endsWith('/actions')).at(-1);
        checks.pushSelectedPayload=push.data.action==='push' && push.data.targets.length===2;
        $('test-results').textContent=JSON.stringify(checks);
      }
      run().catch(error=>document.getElementById('test-results').textContent=JSON.stringify({error:String(error),stack:error.stack}));
    '''
    return template + '<pre id="test-results"></pre><script>' + stubs + '</script><script>' + (ROOT / 'src/main/resources/static/js/confirmation-modal.js').read_text() + '</script><script>' + (ROOT / 'src/main/resources/static/js/workspace-git.js').read_text() + '</script><script>' + tests + '</script></body></html>'


def main():
    with tempfile.TemporaryDirectory(prefix='workspace-git-browser-') as directory:
        page=Path(directory)/'test.html'; page.write_text(build_harness())
        result=subprocess.run(['google-chrome','--headless','--no-sandbox','--disable-dev-shm-usage','--disable-gpu','--disable-background-networking',f'--user-data-dir={directory}/profile','--virtual-time-budget=8000','--dump-dom',page.as_uri()],capture_output=True,text=True,timeout=30,check=True)
        match=re.search(r'<pre id="test-results">(.*?)</pre>',result.stdout,re.S)
        if not match or not match.group(1): raise AssertionError('No browser result: '+result.stderr[-1500:])
        results=json.loads(html.unescape(match.group(1))); print(json.dumps(results,indent=2))
        failed=[key for key,value in results.items() if value is not True]
        if failed: raise AssertionError(f'Workspace Git regressions failed: {failed}')

if __name__=='__main__': main()
