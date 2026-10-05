/* BKFS offline-first state with optimistic sync and three-way conflict merging. */
const OFFLINE_KEY='bkfs_offline_state_v2';
const LEGACY_OFFLINE_KEY='bkfs_offline_state_v1';
const RECORD_COLLECTIONS=['customers','loans','tx','loanRequests','paymentClaims'];

function clone(v){return v==null?v:structuredClone(v)}
function same(a,b){return JSON.stringify(a)===JSON.stringify(b)}
function nowIso(){return new Date().toISOString()}

function loadOfflineRecord(){
 const current=localStorage.getItem(OFFLINE_KEY);
 if(current)return JSON.parse(current);
 const legacy=localStorage.getItem(LEGACY_OFFLINE_KEY);
 if(!legacy)return null;
 const old=JSON.parse(legacy);
 const migrated={state:old.state,baseState:old.pending?null:clone(old.state),revision:old.revision||null,pending:!!old.pending,conflicts:[],lastSync:null};
 localStorage.setItem(OFFLINE_KEY,JSON.stringify(migrated));
 return migrated;
}

let offlineRecord=loadOfflineRecord();

function offlineStatus(text,kind='info'){
 let e=document.getElementById('offlineStatus');
 if(!e){
  e=document.createElement('div');e.id='offlineStatus';e.style='position:sticky;top:0;padding:10px;z-index:100;font-weight:700;text-align:center;cursor:pointer';
  e.title='Tap to retry synchronization';e.onclick=()=>syncOffline(true);document.body.prepend(e);
 }
 e.style.background=kind==='ok'?'#dcfce7':kind==='warn'?'#fff3cd':'#e0f2fe';
 e.style.color=kind==='ok'?'#166534':kind==='warn'?'#854d0e':'#075985';
 e.textContent=text;
}

function persistOffline(){localStorage.setItem(OFFLINE_KEY,JSON.stringify(offlineRecord))}

function offlineRequest(method,path,body,revision){
 const x=new XMLHttpRequest();x.open(method,path,false);x.setRequestHeader('Content-Type','application/json');
 if(method==='POST'&&revision)x.setRequestHeader('If-Match',revision);
 try{x.send(body?JSON.stringify(body):null)}catch(e){return {status:0}}
 let payload={};try{payload=JSON.parse(x.responseText||'{}')}catch(e){}
 return {status:x.status,state:payload,revision:x.getResponseHeader('ETag')};
}

function mergeObject(base,local,remote,where,conflicts){
 const out={};
 const keys=new Set([...Object.keys(base||{}),...Object.keys(local||{}),...Object.keys(remote||{})]);
 for(const key of keys){
  const b=base?.[key],l=local?.[key],r=remote?.[key];
  if(same(l,b))out[key]=clone(r);
  else if(same(r,b)||same(l,r))out[key]=clone(l);
  else{
   out[key]=clone(l);
   conflicts.push({id:'SC'+Date.now()+Math.random().toString(16).slice(2),at:nowIso(),location:where,field:key,serverValue:clone(r),deviceValue:clone(l)});
  }
 }
 return out;
}

function mergeCollection(name,baseList,localList,remoteList,conflicts){
 const idOf=(item,index)=>item?.id||item?.requestId||'index:'+index;
 const map=list=>new Map((list||[]).map((item,index)=>[idOf(item,index),item]));
 const b=map(baseList),l=map(localList),r=map(remoteList),out=[];
 const ids=new Set([...r.keys(),...l.keys(),...b.keys()]);
 for(const id of ids){
  const bv=b.get(id),lv=l.get(id),rv=r.get(id);
  if(lv===undefined){
   if(bv===undefined&&rv!==undefined)out.push(clone(rv));
   else if(rv!==undefined&&!same(rv,bv))out.push(clone(rv));
   continue;
  }
  if(rv===undefined){
   if(bv===undefined||!same(lv,bv))out.push(clone(lv));
   continue;
  }
  if(bv===undefined)out.push(same(lv,rv)?clone(lv):mergeObject({},lv,rv,name+':'+id,conflicts));
  else out.push(mergeObject(bv,lv,rv,name+':'+id,conflicts));
 }
 return out;
}

function mergeState(base,local,remote){
 const conflicts=[];
 const merged=clone(remote||{});
 for(const name of RECORD_COLLECTIONS)merged[name]=mergeCollection(name,base?.[name],local?.[name],remote?.[name],conflicts);
 merged.settings=mergeObject(base?.settings||{},local?.settings||{},remote?.settings||{},'settings',conflicts);
 const prior=[...(remote?.syncConflicts||[]),...(local?.syncConflicts||[])];
 const byId=new Map(prior.map(x=>[x.id,x]));for(const c of conflicts)byId.set(c.id,c);
 if(byId.size)merged.syncConflicts=[...byId.values()].slice(-200);
 return {state:merged,conflicts};
}

function acceptServer(state,revision){
 offlineRecord={state:clone(state),baseState:clone(state),revision,pending:false,conflicts:[],lastSync:nowIso()};
 persistOffline();offlineStatus('Online · all data synced','ok');
}

function get(){
 if(offlineRecord?.pending){offlineStatus('Offline changes pending · tap here to retry sync','warn');return clone(offlineRecord.state)}
 const r=offlineRequest('GET','/api/state');
 if(r.status===200){acceptServer(r.state,r.revision);return clone(r.state)}
 if(!offlineRecord)throw Error('First login and load data with internet before using offline');
 if(r.status===401)offlineStatus('Saved offline mode · login required before next sync','warn');
 else offlineStatus('Offline · using safely saved device data','warn');
 return clone(offlineRecord.state);
}

function save(data){
 if(!offlineRecord?.revision)throw Error('Load data online once before saving offline');
 offlineRecord.state=clone(data);offlineRecord.pending=true;persistOffline();
 syncOffline(false);
 return {ok:true,pending:offlineRecord.pending,conflicts:offlineRecord.conflicts||[]};
}

function syncOffline(showResult=false){
 if(!offlineRecord?.pending){if(showResult)offlineStatus('Online · nothing pending','ok');return true}
 let r=offlineRequest('POST','/api/state',offlineRecord.state,offlineRecord.revision);
 if(r.status===200){acceptServer(offlineRecord.state,r.revision);return true}
 if(r.status===401){offlineStatus('Changes safe on this device · login to sync','warn');return false}
 if(r.status===0){offlineStatus('Offline · changes saved; reconnect to sync','warn');return false}
 if(r.status!==409){offlineStatus('Sync paused · server error '+r.status+'; device copy is safe','warn');return false}

 const latest=offlineRequest('GET','/api/state');
 if(latest.status!==200){offlineStatus('Sync conflict · device copy is safe; reconnect/login and retry','warn');return false}
 const merged=mergeState(offlineRecord.baseState||{},offlineRecord.state,latest.state);
 offlineRecord.state=merged.state;offlineRecord.baseState=clone(latest.state);offlineRecord.revision=latest.revision;
 offlineRecord.conflicts=merged.conflicts;persistOffline();
 r=offlineRequest('POST','/api/state',offlineRecord.state,offlineRecord.revision);
 if(r.status===200){
  const count=merged.conflicts.length;acceptServer(offlineRecord.state,r.revision);
  offlineStatus(count?'Online · merged and synced; '+count+' field conflict(s) saved in audit':'Online · server and device changes merged','ok');
  return true;
 }
 offlineStatus('Merged copy saved on device · tap to retry sync','warn');return false;
}

async function logoutBKFS(){
 if(offlineRecord?.pending){alert('Pending changes are safe on this device. Connect and sync before logout.');return}
 localStorage.removeItem(OFFLINE_KEY);localStorage.removeItem(LEGACY_OFFLINE_KEY);offlineRecord=null;
 if('caches' in window)for(const key of await caches.keys())if(key.startsWith('bkfs-'))await caches.delete(key);
 location.href='/logout';
}

window.addEventListener('online',()=>syncOffline(false));
window.addEventListener('load',()=>{syncOffline(false);if('serviceWorker' in navigator)navigator.serviceWorker.register('/service-worker.js')});
