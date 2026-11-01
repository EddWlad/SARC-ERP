// QA opt-in: solo loopback y credenciales sintéticas del sandbox.
import { performance } from 'node:perf_hooks';
import { writeFile } from 'node:fs/promises';
const base = new URL(process.env.SARC_QA_URL ?? 'http://127.0.0.1:8081');
if (!['127.0.0.1','localhost'].includes(base.hostname)) throw new Error('Solo sandbox loopback');
const login = await fetch(new URL('/api/v1/auth/login',base),{method:'POST',headers:{'Content-Type':'application/json',Cookie:'XSRF-TOKEN=qa004','X-XSRF-TOKEN':'qa004'},body:JSON.stringify({email:'analista@qa004.example.test',password:'Prueba-Feature004-2026'})});
if (!login.ok) throw new Error(`Login QA ${login.status}`);
const cookie = login.headers.getSetCookie().map(s=>s.split(';')[0]).join('; ');
const endpoints = ['/api/v1/energy/sessions?from=2026-10-01&to=2026-11-01&size=20','/api/v1/energy/summary?from=2026-10-01&to=2026-11-01'];
const runs = Number(process.env.SARC_QA_ROUNDS ?? 3);
const results=[];
for (const endpoint of endpoints) {
 for(let i=0;i<2;i++) await (await fetch(new URL(endpoint,base),{headers:{Cookie:cookie}})).arrayBuffer();
 const samples=[];
 await Promise.all(Array.from({length:30},async()=>{for(let i=0;i<runs;i++){
  const start=performance.now();const res=await fetch(new URL(endpoint,base),{headers:{Cookie:cookie}});await res.arrayBuffer();
  if(!res.ok)throw new Error(`${endpoint}: ${res.status}`);samples.push(performance.now()-start);
 }}));
 samples.sort((a,b)=>a-b); results.push({endpoint,concurrency:30,rounds:runs,count:samples.length,p95Ms:samples[Math.ceil(samples.length*.95)-1],maxMs:samples.at(-1)});
}
for(const format of ['PDF','XLSX']){
 const start=performance.now();const res=await fetch(new URL(`/api/v1/energy/report?from=2026-10-01&to=2026-10-03&format=${format}`,base),{headers:{Cookie:cookie}});
 const bytes=await res.arrayBuffer();if(!res.ok)throw new Error(`Report ${format}: ${res.status}`);results.push({format,durationMs:performance.now()-start,bytes:bytes.byteLength});
}
const output={at:new Date().toISOString(),url:base.origin,workload:'30 closed-loop workers,2 warmups,3 rounds by default; 250k rows opt-in fixture; reports2 days',results};
await writeFile(process.env.SARC_QA_PERF_OUT ?? 'target/feature004-performance.json',JSON.stringify(output,null,2));
console.log(JSON.stringify(output,null,2));
