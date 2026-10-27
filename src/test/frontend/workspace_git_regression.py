"""Exercise the production Git page and script in Chrome with delayed transport responses."""
import html
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


# Each scenario runs after the base flow with (checks, W, $, flush); keep them independent of one another.
SCENARIOS = r'''
      gitScenarios.push(async (checks, W, $, flush) => {
        await W.chooseRepository('r1'); holdDiffs=true; const before=heldDiffs.length;
        W.chooseFile({path:'a.txt',area:'changes'}); W.chooseFile({path:'b.txt',area:'changes'}); const last=W.chooseFile({path:'file.txt',area:'changes'});
        await flush(); const pending=heldDiffs.slice(before);
        checks.supersededComparisonsAborted=pending.length===3 && pending[0].signal.aborted && pending[1].signal.aborted && !pending[2].signal.aborted;
        pending.forEach(entry=>entry.release()); holdDiffs=false; await last; await flush();
        checks.onlyLastComparisonRendered=W.state.diff?.path==='file.txt' && $('git-file-name').textContent==='file.txt' && $('git-diff').querySelectorAll('[data-line]').length===3;
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        await W.chooseRepository('r1'); await flush(); const before=calls.length;
        const item=[...W.state.views.get('r2').buttons.entries()].find(([key])=>key.startsWith('changes') && key.endsWith('web.txt'))[1];
        item.button.click(); await flush(); await flush();
        const diffs=calls.slice(before).filter(call=>call.url.includes('/diff?'));
        checks.otherRepositoryFileLoadsOnce=diffs.length===1 && diffs[0].url.includes('web.txt') && W.state.repository==='r2';
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        await W.chooseRepository('r1'); holdDiffs=true; const before=heldDiffs.length;
        const loading=W.chooseFile({path:'slow.txt',area:'changes'});
        checks.loadingNamesFileImmediately=$('git-file-name').textContent==='slow.txt' && $('git-diff').classList.contains('is-loading') && $('git-diff').getAttribute('aria-busy')==='true';
        checks.loadingDimsPreviousComparison=getComputedStyle($('git-diff')).pointerEvents==='none';
        await new Promise(resolve=>setTimeout(resolve,200)); checks.loadingSpinnerAfterDelay=!$('git-diff-loading').hidden;
        heldDiffs.slice(before).forEach(entry=>entry.release()); holdDiffs=false; await loading;
        checks.loadingClearedAfterRender=$('git-diff-loading').hidden && !$('git-diff').classList.contains('is-loading');
        failNextDiff=true; await W.chooseFile({path:'broken.txt',area:'changes'});
        const retry=$('git-diff').querySelector('.git-diff-error button');
        checks.failedComparisonOffersRetry=Boolean(retry) && $('git-diff').textContent.includes('Comparison failed in fixture') && !$('git-diff').querySelector('[data-line]');
        retry.click(); await flush(); await flush();
        checks.retryReloadsComparison=$('git-diff').querySelectorAll('[data-line]').length===3 && W.state.diff?.path==='broken.txt';
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const original=discovery.discovery.repositories;
        const many=Array.from({length:100},(_,i)=>({id:'m'+i,name:'repo'+i,path:'many/repo'+i}));
        many.forEach(repo=>statuses.set(repo.id,{branch:'main',upstream:null,version:'v-'+repo.id,stagedVersion:'s',files:[]}));
        discovery.discovery.repositories=many; holdStatuses=true; held.length=0;
        const discovering=W.discover(true);
        for (let i=0;i<40 && held.length<4;i++) await flush();
        checks.manyRepositoriesListedBeforeStatuses=$('git-repositories').querySelectorAll('section.git-repository').length===100;
        const touched=new Set(); const observer=new MutationObserver(records=>records.forEach(record=>{
          const section=(record.target.nodeType===1?record.target:record.target.parentElement)?.closest('section.git-repository');
          touched.add(section ? [...W.state.views.entries()].find(([,view])=>view.section===section)?.[0] : 'outside');
        }));
        observer.observe($('git-repositories'),{subtree:true,childList:true,attributes:true,characterData:true});
        const first=held.shift(); first.resolve(ok(statuses.get(first.id))); await new Promise(resolve=>setTimeout(resolve,80));
        observer.disconnect();
        checks.statusArrivalTouchesOnlyItsRepository=touched.size===1 && touched.has(first.id);
        W.state.views.get('m99').button.click(); await flush();
        checks.selectableBeforeAllStatuses=W.state.repository==='m99';
        holdStatuses=false; for (let i=0;i<200 && held.length;i++) { held.splice(0).forEach(entry=>entry.resolve(ok(statuses.get(entry.id)))); await flush(); }
        await discovering;
        discovery.discovery.repositories=original; await W.discover(true); await W.chooseRepository('r1');
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const current=statuses.get('r1'); const files=current.files; current.files=[...files,{path:'gen/',index:'?',working:'?',kind:'untracked-dir'}];
        await W.refreshStatuses(); W.state.expanded.add('r1'); W.state.collapsedGroups.clear(); await W.refreshStatuses();
        const folder=W.state.views.get('r1').buttons.get('changes\0gen/');
        checks.untrackedFolderListedOnce=Boolean(folder?.folder) && folder.button.textContent.includes('New folder');
        folder.folder.click(); await flush(); await flush();
        const child=W.state.views.get('r1').buttons.get('changes\0gen/a.txt');
        checks.untrackedFolderExpands=Boolean(child) && child.row.isConnected && folder.folder.getAttribute('aria-expanded')==='true';
        child.button.click(); await flush(); await flush();
        checks.untrackedFolderFileOpensComparison=calls.filter(call=>call.url.includes('/diff?')).at(-1).url.includes('path=gen%2Fa.txt');
        current.files=files; await W.refreshStatuses();
        checks.removedFolderCollapses=!W.state.openFolders.size && !child.row.isConnected;
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        await W.chooseRepository('r1'); await W.refreshStatuses();
        const statusCalls=()=>calls.filter(call=>/\/status$/.test(call.url));
        let before=statusCalls().length; window.dispatchEvent(new Event('focus')); await new Promise(resolve=>setTimeout(resolve,300));
        const quick=statusCalls().slice(before);
        checks.quickReturnRefreshesActiveOnly=quick.length===1 && quick[0].url.includes('/r1/');
        W.state.statusAt.set('r2', Date.now()-61000); before=statusCalls().length;
        window.dispatchEvent(new Event('focus')); await new Promise(resolve=>setTimeout(resolve,300));
        checks.staleRepositoriesRefreshOnReturn=statusCalls().slice(before).map(call=>call.url.includes('/r2/')).filter(Boolean).length===1;
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const polls=()=>calls.filter(call=>call.url.includes('/operations/')).length;
        checks.operationChannelOpens=stomp.connected && W.state.channel==='open';
        runningActions=true; let before=polls();
        const pushed=W.actionDialog('fetch',['r1']); await flush();
        checks.runningOperationKeepsBusy=W.state.busy.has('r1');
        publish(lastOperation); await pushed;
        checks.pushedProgressCompletesWithoutPolling=polls()===before && !W.state.busy.has('r1');
        publishEarly=true; await W.actionDialog('fetch',['r1']); publishEarly=false;
        checks.earlyMessageIsNotLost=polls()===before && !W.state.busy.has('r1');
        W.state.channel='failed'; before=polls();
        await W.actionDialog('fetch',['r1']);
        checks.pollingFallbackWhenChannelUnavailable=polls()>before && !W.state.busy.has('r1');
        operationMissing=true; await W.actionDialog('fetch',['r1']); operationMissing=false;
        const expired=[...W.state.operations.values()].at(-1);
        checks.expiredOperationIsUncertain=expired.state==='done' && expired.outcomes[0].state==='uncertain' && !W.state.busy.has('r1');
        runningActions=false; W.state.channel='open';
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        await W.chooseRepository('r1'); const scans=()=>calls.filter(call=>call.url.endsWith('/repositories?refresh=true')).length;
        let before=scans(); forgetRepositories=true;
        await W.chooseFile({path:'file.txt',area:'changes'}); for (let i=0;i<6;i++) await flush();
        checks.unknownRepositoryReadRediscoversAndLoads=scans()===before+1 && $('git-diff').querySelectorAll('[data-line]').length===3;
        before=scans(); const stages=()=>calls.filter(call=>call.url.endsWith('/stage')).length; const staged=stages();
        forgetRepositories=true; W.state.lines.add('l1'); await W.mutate(true); for (let i=0;i<6;i++) await flush();
        checks.unknownRepositoryMutationAsksForReview=scans()===before+1 && stages()===staged+1 && $('confirmation-modal-message').textContent.includes('Review the current state');
        $('confirmation-modal-primary')?.click(); await flush();
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const App=window.WorkspaceGitApp; const width=()=>parseInt($('git-layout').style.getPropertyValue('--git-sidebar-width'));
        const sidebar=$('git-sidebar-resizer'); const start=width();
        sidebar.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}));
        checks.keyboardResizesSidebar=width()===start+16 && sidebar.getAttribute('aria-valuenow')===String(start+16) && sidebar.getAttribute('aria-valuetext').includes(String(start+16));
        checks.sidebarWidthPersisted=JSON.parse(localStorage.getItem('mvnbuilder.git.layout')).sidebar===start+16;
        $('git-layout').style.setProperty('--git-sidebar-width','250px'); App.restoreLayout();
        checks.persistedLayoutRestores=width()===start+16;
        const left=$('git-layout').getBoundingClientRect().left;
        sidebar.dispatchEvent(new PointerEvent('pointerdown',{button:0,pointerId:7,clientX:left+start,bubbles:true}));
        sidebar.dispatchEvent(new PointerEvent('pointermove',{pointerId:7,clientX:left+9999,bubbles:true}));
        sidebar.dispatchEvent(new PointerEvent('pointerup',{pointerId:7,bubbles:true}));
        checks.dragIsClamped=width()===Number(sidebar.getAttribute('aria-valuemax')) && $('git-layout').clientWidth-width()>=480;
        sidebar.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowLeft',shiftKey:true,bubbles:true}));
        const restoredWidth=width();
        const commit=$('git-commit-resizer'); const before=$('git-commit-area').getBoundingClientRect().height;
        commit.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown',bubbles:true}));
        checks.keyboardResizesCommitArea=$('git-commit-area').classList.contains('is-sized') && Math.round($('git-commit-area').getBoundingClientRect().height)===Math.round(before-16)
          && commit.getAttribute('aria-valuenow')===String(Math.round(before-16));
        $('git-sidebar-toggle').click();
        checks.sidebarHides=$('git-layout').classList.contains('is-sidebar-hidden') && getComputedStyle($('git-sidebar')).display==='none' && $('git-sidebar-toggle').getAttribute('aria-expanded')==='false';
        document.dispatchEvent(new KeyboardEvent('keydown',{key:'b',ctrlKey:true,bubbles:true}));
        checks.shortcutRestoresSidebarWidth=!$('git-layout').classList.contains('is-sidebar-hidden') && width()===restoredWidth && $('git-sidebar').getBoundingClientRect().width===restoredWidth;
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const line=(id,type,text,oldNumber,newNumber,block=0)=>({id,type,text,oldNumber,newNumber,newline:true,block});
        const show=async(path,lines,mode)=>{ customDiffs[path]={version:'v1',kind:'text',partial:true,lines}; $('git-diff-mode').value=mode; await W.chooseFile({path,area:'changes'}); await flush(); };
        checks.highlighterLoaded=typeof hljs!=='undefined' && Boolean(hljs.getLanguage('java') && hljs.getLanguage('properties') && hljs.getLanguage('dockerfile'));
        const java=[line('j0',' ','class Demo {',1,1),line('j1','+','    /* start',null,2),line('j2','+','     * middle',null,3),line('j3','+','     */',null,4),line('j4','+','    int x = 1;',null,5)];
        for (const mode of ['split','unified']) {
          await show('src/Demo.java',java,mode);
          const cells=[...$('git-diff').querySelectorAll('[data-line-highlight]')];
          checks['javaCommentAcrossLines_'+mode]=cells.length===4 && cells.slice(0,3).every(cell=>cell.querySelector('.hljs-comment')) && Boolean(cells[3].querySelector('.hljs-type,.hljs-keyword'))
            && cells.every(cell=>cell.classList.contains('git-added')) && $('git-diff').querySelectorAll('[data-line]').length===4;
        }
        await show('web/page.html',[line('h0','+','<script>window.injected=true<\/script><b>bold</b>',null,1)],'unified');
        checks.markupRendersLiterally=!$('git-diff').querySelector('script,b') && !window.injected && $('git-diff').textContent.includes('<script>window.injected=true<\/script>');
        await show('notes.unknownext',[line('u0','+','plain <text> & more',null,1)],'unified');
        checks.unknownTypeIsPlainText=!$('git-diff').querySelector('[class^="hljs-"]') && $('git-diff').textContent.includes('plain <text> & more');
        await show('src/Count.java',[line('w0','-','int count = total + 1;',1,null),line('w1','+','int amount = total + 1;',null,1)],'split');
        const marks=[...$('git-diff').querySelectorAll('mark.git-word')].map(mark=>mark.textContent);
        checks.wordLevelMarks=marks.length===2 && marks[0]==='count' && marks[1]==='amount';
        await show('src/Other.java',[line('r0','-','alpha',1,null),line('r1','+','omega',null,1)],'split');
        checks.rewrittenLineHasNoWordMarks=!$('git-diff').querySelector('mark.git-word');
        const big=Array.from({length:6000},(_,i)=>line('b'+i,'+','int value'+i+' = '+i+';',null,i+1,Math.floor(i/50)));
        await show('src/Big.java',big,'unified'); for (let i=0;i<80 && $('git-diff').querySelectorAll('[data-line]').length<6000;i++) await flush();
        checks.largeComparisonSkipsHighlighting=$('git-diff').textContent.includes('Syntax highlighting is off') && !$('git-diff').querySelector('[class^="hljs-"]')
          && $('git-diff').querySelectorAll('[data-line]').length===6000;
        $('git-diff-mode').value='unified'; await W.chooseFile({path:'file.txt',area:'changes'}); await flush();
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const L=(id,type,n,o,block,text)=>({id,type,text:text||('line '+(o??n)),oldNumber:type==='+'?null:o,newNumber:type==='-'?null:n,newline:true,block});
        const normal=[2,3,4].map((n,i)=>L('l'+i,' ',n,n,0)).concat([L('l3','-',null,5,0),L('l4','+',5,null,0,'LINE 5')],[6,7,8].map((n,i)=>L('l'+(5+i),' ',n,n,0)),
          [22,23,24].map((n,i)=>L('l'+(8+i),' ',n,n,1)),[L('l11','-',null,25,1),L('l12','+',25,null,1,'LINE 25')],[26,27,28].map((n,i)=>L('l'+(13+i),' ',n,n,1)));
        const full=[]; let c=0; for (let n=1;n<=30;n++) { if (n===5) full.push(L('l3','-',null,5,0),L('l4','+',5,null,0,'LINE 5')); else if (n===25) full.push(L('l11','-',null,25,1),L('l12','+',25,null,1,'LINE 25')); else full.push(L('c'+c++,' ',n,n,-1)); }
        customDiffs['ctx.txt']={version:'v1',kind:'text',partial:true,lines:normal}; fullDiffs['ctx.txt']={version:'v1',kind:'text',partial:true,lines:full};
        $('git-diff-mode').value='unified'; await W.chooseFile({path:'ctx.txt',area:'changes'}); await flush();
        const gaps=()=>[...$('git-diff').querySelectorAll('[data-gap]')].map(button=>button.dataset.gap+':'+button.textContent);
        checks.gapsBetweenBlocks=JSON.stringify(gaps())===JSON.stringify(['1:Show 1 hidden line','9:Show 13 hidden lines','29:Show remaining lines']);
        const box=$('git-diff').querySelector('[data-line="l3"]'); box.checked=true; box.dispatchEvent(new Event('change',{bubbles:true}));
        const fullRequests=()=>calls.filter(call=>call.url.includes('context=all')).length; const before=fullRequests();
        $('git-diff').querySelector('[data-gap="9"]').click(); for (let i=0;i<4;i++) await flush();
        const text=$('git-diff').textContent;
        checks.gapRevealsHiddenLines=fullRequests()===before+1 && text.includes('line 9') && text.includes('line 21') && gaps().includes('1:Show 1 hidden line');
        checks.selectionSurvivesExpansion=$('git-diff').querySelector('[data-line="l3"]').checked && W.state.lines.has('l3');
        checks.trailingGapCountedAfterLoad=gaps().includes('29:Show 2 hidden lines') && gaps().length===2;
        $('git-full-file').click(); for (let i=0;i<4;i++) await flush();
        checks.fullFileShowsEverything=!gaps().length && $('git-diff').textContent.includes('line 30') && fullRequests()===before+1 && $('git-full-file').getAttribute('aria-pressed')==='true';
        $('git-full-file').click(); for (let i=0;i<4;i++) await flush();
        customDiffs['stale.txt']={version:'v1',kind:'text',partial:true,lines:normal}; fullDiffs['stale.txt']={version:'v2',kind:'text',partial:true,lines:full};
        await W.chooseFile({path:'stale.txt',area:'changes'}); await flush();
        $('git-diff').querySelector('[data-gap="9"]').click(); for (let i=0;i<4;i++) await flush();
        checks.staleExpansionAsksForRefresh=$('git-diff').textContent.includes('changed after the comparison was loaded') && !$('git-diff').textContent.includes('line 9');
        await W.chooseFile({path:'file.txt',area:'changes'}); await flush();
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        holdCommit=false; await W.chooseRepository('r1'); const r1=statuses.get('r1'); const files=r1.files;
        r1.files=[{path:'file.txt',index:'M',working:'.',kind:'text'}]; await W.refreshStatuses(); W.state.files.clear(); W.setCommitMode('staged');
        const message=$('git-commit-message'); const lastCall=suffix=>calls.filter(call=>call.url.endsWith(suffix)).at(-1);
        message.value='Ship it'; message.dispatchEvent(new Event('input'));
        $('git-commit-push-button').click(); await flush(); await flush();
        checks.commitAndPushPayload=lastCall('/commit').data.push===true && lastCall('/commit').data.message==='Ship it' && message.value==='';
        message.value='Keyboard commit'; message.dispatchEvent(new Event('input'));
        message.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',ctrlKey:true,bubbles:true})); await flush(); await flush();
        const plain=lastCall('/commit');
        message.value='Keyboard push'; message.dispatchEvent(new Event('input'));
        message.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',ctrlKey:true,shiftKey:true,bubbles:true})); await flush(); await flush();
        checks.commitShortcuts=plain.data.message==='Keyboard commit' && plain.data.push===false && lastCall('/commit').data.message==='Keyboard push' && lastCall('/commit').data.push===true;
        r1.files=[{path:'file.txt',index:'.',working:'M',kind:'text'}]; await W.refreshStatuses();
        W.state.views.get('r1').buttons.get('changes\0file.txt').checkbox.click(); batchUnpushed=true;
        message.value='Unpushed'; message.dispatchEvent(new Event('input')); $('git-commit-push-button').click(); await flush(); await flush(); batchUnpushed=false;
        checks.committedButUnpushedClearsDraft=message.value==='' && !W.state.files.get('r1')?.size && $('git-announcement').textContent.includes('could not be pushed');
        await W.chooseRepository('r2'); await flush();
        checks.publishOfferedWithoutUpstream=!$('git-publish').hidden;
        $('git-publish').click(); for (let i=0;i<4 && !$('git-choice-dialog').open;i++) await flush();
        checks.publishDialogDefaultsToOrigin=$('git-choice-dialog').open && $('git-choice-select').value==='origin' && $('git-choice-select').options.length===2;
        $('git-choice-select').value='backup'; $('git-choice-confirm').click(); await flush(); await flush();
        checks.publishPayload=lastCall('/actions').data.action==='publish' && lastCall('/actions').data.remote==='backup' && lastCall('/actions').data.targets[0].id==='r2';
        await W.chooseRepository('r1'); Object.assign(r1,{ahead:1,behind:2}); pullDiverges=true;
        $('git-repository-actions').querySelector('[data-action="pull"]').click(); for (let i=0;i<4;i++) await flush(); pullDiverges=false;
        checks.divergedPullOffersRebase=$('confirmation-modal-title').textContent==='Pull with rebase?';
        $('confirmation-modal-primary').click(); await flush(); await flush();
        checks.rebasePullPayload=lastCall('/actions').data.action==='pull-rebase' && lastCall('/actions').data.confirmation==='confirmed';
        Object.assign(r1,{ahead:0,behind:0}); r1.files=[...files,{path:'tmp.log',index:'?',working:'?',kind:'untracked'}]; await W.refreshStatuses();
        await W.chooseFile({path:'tmp.log',area:'changes'}); await flush();
        checks.deleteOfferedForUntracked=!$('git-delete-file').hidden && !$('git-delete-file').disabled;
        $('git-delete-file').click(); await flush();
        checks.deleteNeedsConfirmation=$('confirmation-modal-title').textContent==='Delete untracked file?' && lastCall('/actions').data.action==='pull-rebase';
        $('confirmation-modal-primary').click(); await flush(); await flush();
        checks.deletePayload=lastCall('/actions').data.action==='delete-untracked' && lastCall('/actions').data.path==='tmp.log' && lastCall('/actions').data.confirmation==='confirmed';
        r1.files=files; await W.refreshStatuses(); await W.chooseFile({path:'file.txt',area:'changes'}); holdCommit=true;
        checks.deleteHiddenForTracked=$('git-delete-file').hidden;
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const syncCalls=()=>calls.filter(call=>call.url.endsWith('/git/sync')).length; const before=syncCalls();
        $('git-sync-open').click(); for (let i=0;i<4;i++) await flush();
        const rows=()=>[...$('git-sync-rows').querySelectorAll('tr[data-branch]')];
        const row=(repo,branch)=>rows().find(item=>item.dataset.repository===repo && item.dataset.branch===branch);
        const cells=item=>[...item.children].map(cell=>cell.textContent);
        checks.syncOverviewOpens=$('git-sync-dialog').open && syncCalls()===before+1 && rows().length===5;
        checks.syncCountsPushAndPull=cells(row('r1','main'))[3]==='2' && cells(row('r2','feature'))[4]==='3' && cells(row('r1','main'))[5]==='2';
        checks.syncShowsNonCurrentAndNoUpstream=cells(row('r1','feature'))[3]==='1' && cells(row('r2','local'))[2]==='No upstream' && cells(row('r2','local'))[3]==='—';
        checks.syncMarksDivergence=row('r1','hotfix').querySelector('.is-diverged')!==null && !row('r1','main').querySelector('.is-diverged');
        checks.syncFlagsStaleFetch=row('r2','feature').querySelector('.git-sync-fetch.is-stale')!==null && !row('r1','main').querySelector('.is-stale');
        $('git-sync-filter').value='ahead'; $('git-sync-filter').dispatchEvent(new Event('change'));
        checks.syncFilterAhead=rows().length===3 && rows().every(item=>item.dataset.repository==='r1');
        $('git-sync-filter').value='no-upstream'; $('git-sync-filter').dispatchEvent(new Event('change'));
        checks.syncFilterNoUpstream=rows().length===1 && rows()[0].dataset.branch==='local';
        $('git-sync-filter').value='all'; $('git-sync-filter').dispatchEvent(new Event('change'));
        checks.syncBulkCounts=$('git-sync-push').textContent==='Push ahead (1)' && $('git-sync-pull').textContent==='Pull behind (1)';
        $('git-sync-push').click(); for (let i=0;i<6;i++) await flush();
        const push=calls.filter(call=>call.url.endsWith('/actions')).at(-1);
        checks.syncPushTargetsOnlyAhead=push.data.action==='push' && push.data.targets.map(t=>t.id).join()==='r1' && syncCalls()===before+2;
        $('git-sync-pull').click(); for (let i=0;i<6;i++) await flush();
        const pull=calls.filter(call=>call.url.endsWith('/actions')).at(-1);
        checks.syncPullTargetsOnlyBehind=pull.data.action==='pull' && pull.data.targets.map(t=>t.id).join()==='r2';
        $('git-sync-fetch').click(); for (let i=0;i<6;i++) await flush();
        checks.syncFetchAll=calls.filter(call=>call.url.endsWith('/actions')).at(-1).data.targets.length===2;
        $('git-sync-close').click(); checks.syncCloses=!$('git-sync-dialog').open;
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        await W.chooseRepository('r1'); await W.chooseFile({path:'file.txt',area:'changes'}); await flush(); const errorsBefore=errorDialogs.length;
        $('git-history-toggle').click(); for (let i=0;i<4;i++) await flush();
        const commits=()=>[...$('git-history-list').querySelectorAll('.git-history-commit > button')];
        checks.historyLists=!$('git-history').hidden && commits().length===2 && commits()[0].textContent.includes('Commit 0') && commits()[0].querySelector('.git-history-badge')!==null && !commits()[1].querySelector('.git-history-badge');
        $('git-history-more').click(); for (let i=0;i<4;i++) await flush();
        checks.historyLoadsMore=commits().length===3 && calls.some(call=>call.url.includes('/log?skip=2')) && $('git-history-more').hidden;
        commits()[0].click(); for (let i=0;i<4;i++) await flush();
        const files=[...$('git-history-list').querySelectorAll('.git-history-files button')];
        checks.historyCommitFiles=files.length===2 && files[1].textContent.includes('old.txt → new.txt');
        files[0].click(); for (let i=0;i<4;i++) await flush();
        checks.historyComparisonIsReadOnly=W.state.diff?.area==='commit' && $('git-diff').querySelectorAll('[data-line-highlight]').length===2 && !$('git-diff').querySelector('[data-line]')
          && $('git-stage-file').disabled && $('git-discard-file').disabled && $('git-comparison-label').textContent.includes('c000') && !$('git-diff').querySelector('[data-gap]');
        await W.refreshStatuses(); checks.historyComparisonSurvivesRefresh=W.state.diff?.area==='commit';
        $('git-history-toggle').click(); await W.chooseFile({path:'file.txt',area:'changes'}); await flush();
        W.setCommitMode('staged'); await W.refreshStatuses(); const r1=statuses.get('r1'); const files0=r1.files; r1.files=[]; await W.refreshStatuses();
        $('git-commit-message').value='draft before amend'; $('git-commit-message').dispatchEvent(new Event('input'));
        checks.amendAvailableWithoutStagedChanges=!$('git-amend').disabled && !$('git-amend-row').hidden;
        $('git-amend').click(); await flush();
        checks.amendPrefillsMessage=$('git-commit-message').value==='Previous message\n\nBody' && $('git-commit-button').textContent==='Amend last commit' && !$('git-commit-button').disabled;
        $('git-amend').click(); checks.amendRestoresDraft=$('git-commit-message').value==='draft before amend';
        $('git-amend').click(); $('git-commit-message').value='Better message'; $('git-commit-message').dispatchEvent(new Event('input'));
        holdCommit=false; $('git-commit-button').click(); for (let i=0;i<4;i++) await flush();
        const amendCall=calls.filter(call=>call.url.endsWith('/commit')).at(-1);
        checks.amendPayload=amendCall.data.amend===true && amendCall.data.head==='abc1234567' && amendCall.data.message==='Better message' && !$('git-amend').checked;
        amendState={head:'def',message:'Pushed',allowed:false,reason:'The last commit is already pushed; amending it would require a force push.'};
        await W.chooseRepository('r2'); await W.chooseRepository('r1'); for (let i=0;i<4;i++) await flush();
        checks.amendDisabledWithReason=$('git-amend').disabled && $('git-amend-row').title.includes('already pushed');
        checks.historyAndAmendRaiseNoErrors=errorDialogs.length===errorsBefore || errorDialogs.slice(errorsBefore).join(' | ');
        r1.files=files0; await W.refreshStatuses(); holdCommit=true;
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        await W.chooseRepository('r1'); $('git-stash-toggle').click(); for (let i=0;i<4;i++) await flush();
        const entries=()=>[...$('git-stash-list').querySelectorAll('.git-stash-entry')];
        checks.stashesListed=!$('git-stashes').hidden && entries().length===2 && entries()[0].textContent.includes('Try layout') && entries()[0].textContent.includes('on main');
        const lastAction=()=>calls.filter(call=>call.url.endsWith('/actions')).at(-1);
        entries()[0].querySelector('[aria-label^="Pop"]').click(); for (let i=0;i<4;i++) await flush();
        checks.stashPopPayload=lastAction().data.action==='stash-pop' && lastAction().data.stash==='stash@{0}' && lastAction().data.stashSha==='5ta5h0000000';
        const listed=calls.filter(call=>call.url.endsWith('/stashes')).length;
        entries()[1].querySelector('[aria-label^="Drop"]').click(); await flush();
        checks.stashDropNeedsConfirmation=$('confirmation-modal-title').textContent==='Drop stash?' && $('confirmation-modal-message').textContent.includes('Old idea') && lastAction().data.action==='stash-pop';
        $('confirmation-modal-primary').click(); for (let i=0;i<4;i++) await flush();
        checks.stashDropPayload=lastAction().data.action==='stash-drop' && lastAction().data.confirmation==='confirmed' && lastAction().data.stash==='stash@{1}'
          && calls.filter(call=>call.url.endsWith('/stashes')).length>listed;
        stashes=[]; $('git-stash-toggle').click(); $('git-stash-toggle').click(); for (let i=0;i<4;i++) await flush();
        checks.stashEmptyState=$('git-stash-list').textContent.includes('No stashes');
        $('git-stash-toggle').click();
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const until=async(test,tries=80)=>{ for (let i=0;i<tries && !test();i++) await flush(); return test(); };
        await W.chooseRepository('r1'); await flush(); const posted=suffix=>calls.filter(call=>call.url.endsWith(suffix));
        checks.branchControlShowsCurrent=!$('git-branch-button').hidden && $('git-branch-name').textContent==='main';
        const open=async()=>{ await until(()=>!W.state.busy.has('r1')); $('git-branch-button').click(); await until(()=>$('git-choice-dialog').open); };
        await open();
        checks.branchPickerGroupsReferences=$('git-choice-dialog').open && $('git-choice-select').querySelectorAll('optgroup').length===3
          && $('git-choice-select').value==='LOCAL\0refs/heads/main' && !$('git-choice-text-row').hidden;
        $('git-choice-dialog').querySelector('button[value="cancel"]').click(); await flush();
        checks.branchPickerCancelChangesNothing=!$('git-choice-dialog').open && !posted('/checkout').length && !posted('/branches').length;
        await open(); const beforeCheckout=calls.length; $('git-choice-select').value='LOCAL\0refs/heads/feature'; $('git-choice-confirm').click(); await until(()=>$('git-announcement').textContent.includes('now on feature') && !W.state.busy.has('r1'));
        checks.branchCheckoutPayload=posted('/checkout').length===1 && posted('/checkout')[0].data.reference==='refs/heads/feature' && posted('/checkout')[0].data.type==='LOCAL'
          && calls.slice(beforeCheckout).some(call=>/\/r1\/status$/.test(call.url)) && $('git-announcement').textContent.includes('now on feature')
          || JSON.stringify({checkouts:posted('/checkout').map(c=>c.data),ann:$('git-announcement').textContent,busy:[...W.state.busy],tail:calls.slice(-8).map(c=>c.url.replace('/workspaces/1/git',''))});
        await open(); $('git-choice-text').value='feature/new'; $('git-choice-confirm').click(); await until(()=>$('git-announcement').textContent.includes('now on feature/new') && !W.state.busy.has('r1'));
        checks.branchCreatePayload=posted('/branches').length===1 && posted('/branches')[0].data.name==='feature/new' && posted('/checkout').length===1
          || JSON.stringify({branches:posted('/branches').map(c=>c.data),checkouts:posted('/checkout').length,ann:$('git-announcement').textContent,busy:[...W.state.busy],dialog:$('git-choice-dialog').open});
        $('git-open-editor').click(); await until(()=>posted('/open-editor').length>0);
        checks.openEditorRequest=posted('/open-editor').length===1 && $('git-announcement').textContent.includes('Visual Studio Code');
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const r1=statuses.get('r1'); const files0=r1.files;
        r1.files=[{path:'file.txt',index:'.',working:'M',kind:'text'},{path:'other.txt',index:'.',working:'M',kind:'text'},{path:'done.txt',index:'M',working:'.',kind:'text'}];
        await W.chooseRepository('r1'); W.state.expanded.add('r1'); W.state.collapsedGroups.clear(); await W.refreshStatuses();
        await W.chooseFile({path:'other.txt',area:'changes'}); await flush();
        const item=key=>W.state.views.get('r1').buttons.get(key); const stages=()=>calls.filter(call=>call.url.endsWith('/stage'));
        let before=stages().length; const fullBefore=calls.filter(call=>call.url.endsWith('/status?full=true')).length;
        item('changes\0file.txt').stage.click(); for (let i=0;i<4;i++) await flush();
        const staged=stages().at(-1);
        checks.listStagePayload=stages().length===before+1 && staged.data.path==='file.txt' && staged.data.area==='changes' && staged.data.lines===null && staged.data.version==='full-'+r1.version
          && calls.filter(call=>call.url.endsWith('/status?full=true')).length===fullBefore+1;
        checks.listStageKeepsComparison=W.state.file?.path==='other.txt' && W.state.diff?.path==='other.txt';
        checks.listUnstageControl=item('staged\0done.txt').stage.textContent==='−' && item('staged\0done.txt').stage.getAttribute('aria-label')==='Unstage done.txt';
        before=stages().length; W.state.views.get('r1').groups.get('Changes').action.click(); for (let i=0;i<6;i++) await flush();
        checks.groupStageAll=stages().length===before+2 && stages().slice(-2).map(call=>call.data.path).join()==='file.txt,other.txt';
        W.state.views.get('r1').groups.get('Staged Changes').action.click(); for (let i=0;i<4;i++) await flush();
        const unstage=calls.filter(call=>call.url.endsWith('/actions')).at(-1);
        checks.groupUnstageAll=unstage.data.action==='unstage-all' && unstage.data.targets[0].version==='full-'+r1.version;
        r1.files=files0; await W.refreshStatuses(); await W.chooseFile({path:'file.txt',area:'changes'});
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const r1=statuses.get('r1'); const files0=r1.files;
        r1.files=[{path:'a.txt',index:'.',working:'M',kind:'text'},{path:'b.txt',index:'.',working:'M',kind:'text'}];
        await W.chooseRepository('r1'); W.state.expanded.add('r1'); W.state.collapsedGroups.clear(); await W.refreshStatuses(); await W.chooseFile({path:'a.txt',area:'changes'}); await flush();
        const key=(k,target=document.body,extra={})=>target.dispatchEvent(new KeyboardEvent('keydown',{key:k,bubbles:true,cancelable:true,...extra}));
        $('git-diff').focus(); key('j',$('git-diff')); for (let i=0;i<4;i++) await flush();
        checks.shortcutNextFile=W.state.file?.path==='b.txt' && calls.filter(call=>call.url.includes('/diff?')).at(-1).url.includes('path=b.txt');
        key('k'); for (let i=0;i<4;i++) await flush(); checks.shortcutPreviousFile=W.state.file?.path==='a.txt';
        const stages=calls.filter(call=>call.url.endsWith('/stage')).length; key('s'); for (let i=0;i<4;i++) await flush();
        checks.shortcutStagesCurrentFile=calls.filter(call=>call.url.endsWith('/stage')).length===stages+1 && calls.filter(call=>call.url.endsWith('/stage')).at(-1).data.path==='a.txt';
        key('c'); checks.shortcutFocusesMessage=document.activeElement===$('git-commit-message');
        $('git-commit-message').value=''; const before=W.state.file?.path; key('j',$('git-commit-message'));
        checks.shortcutsIgnoredWhileTyping=W.state.file?.path===before;
        $('git-commit-message').blur(); key('?'); checks.shortcutHelpOpens=$('git-shortcuts-dialog').open && $('git-shortcuts-dialog').textContent.includes('Next / previous file');
        key('j'); await flush(); checks.shortcutsIgnoredInDialogs=W.state.file?.path===before; $('git-shortcuts-close').click();
        r1.files=files0; await W.refreshStatuses(); await W.chooseFile({path:'file.txt',area:'changes'});
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const r1=statuses.get('r1'); const files0=r1.files;
        r1.files=[{path:'file.txt',index:'.',working:'M',kind:'text'},{path:'other.txt',index:'.',working:'M',kind:'text'},{path:'locked.bin',index:'.',working:'M',kind:'text'}];
        await W.chooseRepository('r1'); W.state.expanded.add('r1'); W.state.collapsedGroups.clear(); await W.refreshStatuses(); await W.chooseFile({path:'file.txt',area:'changes'}); await flush();
        const App=window.WorkspaceGitApp; const puts=()=>calls.filter(call=>call.url.endsWith('/file') && call.data);
        checks.editAvailableForChangedText=!$('git-edit-file').disabled;
        $('git-edit-file').click(); for (let i=0;i<4;i++) await flush();
        const cm=()=>W.state.editor?.cm;
        checks.editorOpensWithContent=!$('git-editor').hidden && $('git-diff').hidden && cm()?.getValue()==='line one\n' && $('git-editor-state').textContent.includes('CRLF');
        cm().setValue('line one\nline two\n'); checks.editorTracksDirtyState=App.editorDirty() && $('git-editor-state').textContent==='Unsaved changes';
        $('git-editor-save').click(); for (let i=0;i<4;i++) await flush();
        checks.editorSavePayload=puts().length===1 && puts()[0].data.content==='line one\nline two\n' && puts()[0].data.hash==='h1' && puts()[0].data.path==='file.txt'
          && $('git-editor').hidden && !$('git-diff').hidden && !W.state.editor;
        $('git-edit-file').click(); for (let i=0;i<4;i++) await flush(); cm().setValue('mine\n'); putConflict=true;
        $('git-editor-save').click(); for (let i=0;i<4;i++) await flush(); putConflict=false;
        checks.editorConflictKeepsEdits=$('confirmation-modal-title').textContent==='File changed on disk' && !$('git-editor').hidden && cm().getValue()==='mine\n';
        $('confirmation-modal-cancel').click(); await flush();
        const other=W.state.views.get('r1').buttons.get('changes\0other.txt');
        other.button.click(); for (let i=0;i<3;i++) await flush();
        checks.unsavedEditsGuardSwitch=$('confirmation-modal-title').textContent==='Discard unsaved edits?' && W.state.file.path==='file.txt';
        $('confirmation-modal-cancel').click(); for (let i=0;i<3;i++) await flush();
        checks.cancelKeepsEditor=!$('git-editor').hidden && cm().getValue()==='mine\n' && W.state.file.path==='file.txt';
        other.button.click(); for (let i=0;i<3;i++) await flush(); $('confirmation-modal-primary').click(); for (let i=0;i<4;i++) await flush();
        checks.confirmDiscardSwitches=$('git-editor').hidden && W.state.file.path==='other.txt' && !W.state.editor;
        await W.chooseFile({path:'locked.bin',area:'changes'}); await flush(); $('git-edit-file').click(); for (let i=0;i<4;i++) await flush();
        checks.uneditableFileExplained=$('confirmation-modal-message').textContent.includes('Binary') && $('git-editor').hidden;
        $('confirmation-modal-primary').click(); r1.files=files0; await W.refreshStatuses(); await W.chooseFile({path:'file.txt',area:'changes'});
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        const r2=statuses.get('r2'); const files=r2.files; r2.files=[]; await W.refreshStatuses();
        checks.uncommittedCountShown=$('git-changed-count').textContent==='(1)';
        $('git-changed-only').click(); await flush();
        checks.uncommittedFilterHidesClean=!W.state.views.get('r1').section.hidden && W.state.views.get('r2').section.hidden;
        checks.uncommittedFilterRemembered=localStorage.getItem('mvnbuilder.git.changedOnly')==='true';
        r2.files=files; await W.refreshStatuses();
        $('git-changed-only').click(); await flush();
        checks.uncommittedFilterOff=!W.state.views.get('r2').section.hidden && $('git-changed-count').textContent==='(2)';
      });
      gitScenarios.push(async (checks, W, $, flush) => {
        $('git-search').value='web'; $('git-search').dispatchEvent(new Event('input'));
        const immediate=!W.state.views.get('r1').section.hidden;
        await new Promise(resolve=>setTimeout(resolve,200));
        checks.searchIsDebounced=immediate && W.state.views.get('r1').section.hidden && !W.state.views.get('r2').section.hidden;
        $('git-search').value=''; $('git-search').dispatchEvent(new Event('input')); await new Promise(resolve=>setTimeout(resolve,200));
      });
'''


def page_scripts():
    """Inline the Git view scripts in the template's own load order."""
    template = (ROOT / 'src/main/resources/templates/workspace-git.html').read_text()
    # Real libraries the page relies on are inlined too; SockJS/STOMP are replaced by stubs.
    sources = re.findall(r'<script src="(/js/workspace-git[^"]*|/vendor/@highlightjs/[^"]*)"', template)
    assert sources and sources[-1] == '/js/workspace-git.js', sources
    # Scripts share one namespace; a name exported twice silently replaces the earlier function.
    exported = [name.strip().split(':')[0] for source in sources if source.startswith('/js/workspace-git/')
                for block in re.findall(r'Object\.assign\(App, \{([^}]*)\}\)', (ROOT / 'src/main/resources/static' / source.lstrip('/')).read_text())
                for name in block.split(',')]
    duplicates = {name for name in exported if exported.count(name) > 1}
    assert not duplicates, f'Names exported by more than one script: {sorted(duplicates)}'
    # The editor loads its library lazily from this list; preloading it here keeps the file:// harness self-contained.
    editor = json.loads(re.search(r'<script type="application/json" id="git-editor-assets">(.*?)</script>', template, re.S).group(1))
    static = ROOT / 'src/main/resources/static'
    assets = ''.join(('<style>' if url.endswith('.css') else '<script>') + (static / url.lstrip('/')).read_text() + ('</style>' if url.endswith('.css') else '</script>') for url in editor)
    return assets + ''.join('<script>' + (static / source.lstrip('/')).read_text() + '</script>' for source in sources)


def build_harness():
    template = (ROOT / 'src/main/resources/templates/workspace-git.html').read_text()
    template = re.sub(r'<script[^>]*src=[^>]*></script>', '', template)
    styles = [ROOT / 'src/main/resources/static' / href.lstrip('/') for href in re.findall(r'<link rel="stylesheet" href="(/vendor/[^"]+|/css/workspace-git\.css)"', template)]
    template = re.sub(r'<link[^>]+>', '', template)
    template = template.replace('</head>', ''.join('<style>' + path.read_text() + '</style>' for path in styles) + '</head>')
    template = re.sub(r'<body[^>]*>', '<body class="git-page" data-workspace-id="1" data-workspace-name="Fixture" data-refresh-interval="60" data-highlight-max-lines="5000" data-stale-fetch-hours="24">', template)
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
      const stomp = {handlers:{}, connected:false}; window.SockJS = function(url){ this.url=url; };
      window.Stomp = {over:()=>({debug:null, connect(headers,onOpen){ stomp.connected=true; setTimeout(onOpen,0); }, subscribe(topic,callback){ stomp.handlers[topic]=callback; }})};
      const publish = operation => stomp.handlers['/topic/workspace-git/1']({body:JSON.stringify(operation)});
      const B=(name,current,upstream,ahead,behind)=>({name,current,upstream,ahead,behind,gone:false});
      const syncSummary={repositories:[
        {id:'r1',path:'api',branch:'main',changes:2,lastFetch:Date.now()-3600000,error:null,branches:[B('main',true,'origin/main',2,0),B('feature',false,'origin/feature',1,0),B('hotfix',false,'origin/hotfix',1,1)]},
        {id:'r2',path:'web',branch:'feature',changes:0,lastFetch:null,error:null,branches:[B('feature',true,'origin/feature',0,3),B('local',false,null,null,null)]}]};
      let amendState={head:'abc1234567',message:'Previous message\n\nBody',allowed:true,reason:null};
      const commitAt=(i,pushed)=>({sha:'c'+i+'0000000',shortSha:'c'+i+'00',subject:'Commit '+i,author:'Fixture',time:Math.floor(Date.now()/1000)-3600*(i+1),pushed});
      let stashes=[{ref:'stash@{0}',sha:'5ta5h0000000',message:'Try layout',branch:'main',time:Math.floor(Date.now()/1000)-7200},{ref:'stash@{1}',sha:'5ta5h1111111',message:'Old idea',branch:null,time:Math.floor(Date.now()/1000)-86400*3}];
      let putConflict = false; let holdCommit = true; let batchUnpushed = false; let pullDiverges = false; let forgetRepositories = false; let runningActions = false; let publishEarly = false; let operationMissing = false; let lastOperation = null;
      const customDiffs = {}; const fullDiffs = {}; const heldDiffs = []; let holdDiffs = false; let failNextDiff = false; window.gitScenarios = [];
      const calls = []; const held = []; let holdStatuses = false; let commitHeld; let batchFails = false;
      const ok = body => ({ok:true,status:200,json:async()=>structuredClone(body)});
      const discovery = {id:'scan',state:'done',outcomes:[{repositoryId:'discovery',state:'success',message:'Completed'}],discovery:{repositories:[{id:'r1',name:'api',path:'api'},{id:'r2',name:'web',path:'web'}],warnings:[],complete:true}};
      window.fetch = async (url, options={}) => {
        const data = options.body ? JSON.parse(options.body) : null; calls.push({url,data});
        if (url.endsWith('/repositories') || url.endsWith('/repositories?refresh=true')) { if (url.endsWith('?refresh=true')) forgetRepositories=false; return ok(discovery); }
        if (forgetRepositories && url.includes('/repositories/')) return {ok:false,status:404,json:async()=>({message:'Repository is not available in this workspace. Refresh repositories.',code:'unknown-repository'})};
        const id = decodeURIComponent(url.match(/repositories\/([^/?]+)\//)?.[1] || 'r1');
        if (url.includes('/untracked?dir=')) return ok([{path:'gen/a.txt',index:'?',working:'?',kind:'untracked'}]);
        if (url.endsWith('/status?full=true')) return ok({...statuses.get(id),version:'full-'+statuses.get(id).version});
        if (url.endsWith('/status')) {
          if (holdStatuses) return await new Promise(resolve=>held.push({id,resolve}));
          return ok(statuses.get(id));
        }
        if (url.includes('/commits/') && url.endsWith('/files')) return ok([{status:'M',path:'src/Old.java',oldPath:null},{status:'R',path:'new.txt',oldPath:'old.txt'}]);
        if (url.includes('/commits/') && url.includes('/diff?')) return ok({path:'src/Old.java',oldPath:null,area:'commit',version:'c0',kind:'text',reason:null,partial:false,lines:[{id:'l0',type:'-',text:'int a;',oldNumber:1,newNumber:null,newline:true,block:0},{id:'l1',type:'+',text:'int b;',oldNumber:null,newNumber:1,newline:true,block:0}]});
        if (url.includes('/diff?')) {
          const params=new URL(url,'http://test').searchParams; const source=params.get('context')==='all' ? fullDiffs[params.get('path')] : customDiffs[params.get('path')];
          const response={...(source||diff),path:params.get('path'),area:params.get('area')};
          if (failNextDiff) { failNextDiff=false; return {ok:false,status:500,json:async()=>({message:'Comparison failed in fixture'})}; }
          if (!holdDiffs) return ok(response);
          return await new Promise((resolve,reject)=>{ const entry={url,signal:options.signal,release:()=>resolve(ok(response))};
            options.signal?.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError'))); heldDiffs.push(entry); });
        }
        if (url.endsWith('/stage') || url.endsWith('/unstage')) return ok(statuses.get(id));
        if (url.endsWith('/commits')) return ok({id:'batch-'+calls.length,state:'done',outcomes:data.targets.map(t=>({repositoryId:t.id,state:batchUnpushed?'committed':batchFails && t.id==='r2'?'failed':'success',message:batchFails && t.id==='r2'?'Hook refused':'Completed'}))});
        if (url.endsWith('/remotes')) return ok(['origin','backup']);
        if (url.endsWith('/git/sync')) return ok(syncSummary);
        if (url.endsWith('/amend')) return ok(amendState);
        if (url.endsWith('/stashes')) return ok(stashes);
        if (url.endsWith('/references')) return ok({current:'main',local:[{type:'LOCAL',ref:'refs/heads/main',name:'main'},{type:'LOCAL',ref:'refs/heads/feature',name:'feature'}],
          remote:[{type:'REMOTE',ref:'refs/remotes/origin/release',name:'origin/release'}],tags:[{type:'TAG',ref:'refs/tags/v1',name:'v1'}]});
        if (url.endsWith('/checkout')) return ok({current:data.reference.replace('refs/heads/','')});
        if (url.endsWith('/branches')) return ok({current:data.name});
        if (url.endsWith('/open-editor')) return ok({opened:'api'});
        if (url.includes('/file?path=')) { const path=new URL(url,'http://test').searchParams.get('path');
          return ok(path==='locked.bin' ? {path,editable:false,reason:'Binary or non-UTF-8 files cannot be edited here.'} : {path,content:'line one\n',hash:'h1',eol:'crlf',bom:false,finalNewline:true,editable:true,reason:null}); }
        if (url.endsWith('/file') && options.method==='PUT') return putConflict ? {ok:false,status:409,json:async()=>({message:'The file changed on disk.'})} : ok({...data,editable:true,hash:'h2',eol:'crlf'});
        if (url.includes('/log?')) { const skip=Number(new URL(url,'http://test').searchParams.get('skip')); return ok(skip ? {commits:[commitAt(2,true)],more:false,upstream:true} : {commits:[commitAt(0,false),commitAt(1,true)],more:true,upstream:true}); }
        if (url.endsWith('/commit') && !holdCommit) return ok({id:'commit-'+calls.length,state:'done',outcomes:[{repositoryId:id,state:'success',message:'Committed abc and pushed'}]});
        if (url.endsWith('/commit')) return await new Promise(resolve=>commitHeld=()=>resolve(ok({id:'commit',state:'done',outcomes:[{repositoryId:'r1',state:'success',message:'Completed'}]})));
        if (url.endsWith('/actions')) {
          const id='action-'+calls.length; const refused=pullDiverges && data.action==='pull';
          const done={id,state:'done',outcomes:data.targets.map(t=>({repositoryId:t.id,state:refused?'failed':'success',message:refused?'Not possible to fast-forward, aborting.':'Completed'}))};
          if (!runningActions) return ok(done);
          lastOperation=done; if (publishEarly) publish(done);
          return ok({id,state:'running',outcomes:data.targets.map(t=>({repositoryId:t.id,state:'running',message:'Running…'}))});
        }
        if (url.includes('/operations/')) return operationMissing ? {ok:false,status:404,json:async()=>({message:'Operation is unavailable or expired.'})} : ok(lastOperation);
        throw new Error('Unexpected route '+url);
      };
      window.onerror = message => { document.getElementById('test-results').textContent=JSON.stringify({error:String(message)}); };
    '''
    tests = r'''
      const checks = {}; const flush = () => new Promise(resolve=>setTimeout(resolve,25));
      async function run() {
        const W=WorkspaceGit; const $=id=>document.getElementById(id);
        window.errorDialogs=[]; const showError=ConfirmationModal.error; ConfirmationModal.error=options=>{ errorDialogs.push(options.message); return showError(options); };
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
        checks.discardKeepsConfirmedSnapshot=discard.data.targets[0].version==='full-v1' && discard.data.path==='file.txt';
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
        checks.multipleCommitPayload=batch.data.targets.length===2 && batch.data.message==='Partial batch' && batch.data.targets.every(t=>t.version.startsWith('full-'));
        checks.failedSelectionsAndDraftRetained=!W.state.files.get('r1').size && W.state.files.get('r2').size===1 && $('git-commit-message').value==='Partial batch';
        for(const id of ['r1','r2']) { const checkbox=W.state.views.get(id).checkbox; if(!checkbox.checked) checkbox.click(); }
        checks.pushSelectedVisible=!$('git-push-selected').disabled && $('git-push-selected').textContent.includes('(2)');
        $('git-push-selected').click(); await flush(); await flush();
        const push=calls.filter(c=>c.url.endsWith('/actions')).at(-1);
        checks.pushSelectedPayload=push.data.action==='push' && push.data.targets.length===2;
        checks.allScenariosRegistered=window.gitScenarios.length===EXPECTED_SCENARIOS || `registered ${window.gitScenarios.length} of ${EXPECTED_SCENARIOS}`;
        // Partial results name the running scenario, so a scenario that never settles is identifiable.
        for (const [index, scenario] of window.gitScenarios.entries()) {
          $('test-results').textContent=JSON.stringify({...checks, unfinishedScenario:index+1});
          await scenario(checks, W, $, flush);
        }
        // Only the deliberate unknown-repository mutation may raise an error dialog; anything else is a missing stub or a bug.
        const unexpected=errorDialogs.filter(message=>!message.includes('Review the current state'));
        checks.noUnexpectedErrorDialogs=!unexpected.length || unexpected.join(' | ');
        $('test-results').textContent=JSON.stringify(checks);
      }
      run().catch(error=>document.getElementById('test-results').textContent=JSON.stringify({error:String(error),stack:error.stack}));
    '''
    # An inline script ends at the first literal closing script tag, so scenario code must never contain one unescaped.
    assert '</script' not in SCENARIOS.lower() and '</script' not in tests.lower() and '</script' not in stubs.lower()
    expected = '<script>const EXPECTED_SCENARIOS=' + str(SCENARIOS.count('gitScenarios.push(')) + ';</script>'
    return template + '<pre id="test-results"></pre><script>' + stubs + '</script>' + expected + '<script>' + SCENARIOS + '</script><script>' + (ROOT / 'src/main/resources/static/js/confirmation-modal.js').read_text() + '</script>' + page_scripts() + '<script>' + tests + '</script></body></html>'


def main():
    with tempfile.TemporaryDirectory(prefix='workspace-git-browser-') as directory:
        page=Path(directory)/'test.html'; page.write_text(build_harness())
        result=subprocess.run(['google-chrome','--headless','--no-sandbox','--disable-dev-shm-usage','--disable-gpu','--disable-background-networking',f'--user-data-dir={directory}/profile','--virtual-time-budget=120000','--window-size=1400,900','--dump-dom',page.as_uri()],capture_output=True,text=True,timeout=180,check=True)
        match=re.search(r'<pre id="test-results">(.*?)</pre>',result.stdout,re.S)
        if not match or not match.group(1): raise AssertionError('No browser result: '+result.stderr[-1500:])
        results=json.loads(html.unescape(match.group(1))); print(json.dumps(results,indent=2))
        failed=[key for key,value in results.items() if value is not True]
        if failed: raise AssertionError(f'Workspace Git regressions failed: {failed}')

if __name__=='__main__': main()
