const CACHE='bkfs-offline-v3';
const PUBLIC=['/manifest.webmanifest','/icon-192.png','/icon-512.png','/offline.js','/loan-request','/customer-pay','/loan_request_qr.png','/payment_qr.png'];

self.addEventListener('install',event=>event.waitUntil(
 caches.open(CACHE).then(cache=>cache.addAll(PUBLIC)).then(()=>self.skipWaiting())
));

self.addEventListener('activate',event=>event.waitUntil(
 caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('bkfs-')&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())
));

self.addEventListener('fetch',event=>{
 const request=event.request,url=new URL(request.url);
 if(url.origin!==self.location.origin||request.method!=='GET'||url.pathname.startsWith('/api/'))return;
 if(['/logout','/login','/setup','/verify-device','/recover','/'].includes(url.pathname))return;
 if(url.pathname==='/app'||PUBLIC.includes(url.pathname)){
  event.respondWith(fetch(request).then(async response=>{
   if(response.ok&&!response.redirected){const cache=await caches.open(CACHE);await cache.put(request,response.clone())}
   return response;
  }).catch(async()=>{
   const cache=await caches.open(CACHE);
   return await cache.match(request)||
    (request.mode==='navigate'?await cache.match('/app'):null)||
    new Response('Open BKFS online once before using offline.',{status:503,headers:{'Content-Type':'text/plain'}});
  }));
 }
});
