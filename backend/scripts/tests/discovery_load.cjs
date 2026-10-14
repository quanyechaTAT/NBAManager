// Native HTTP load generator for the isolated Java API. No credentials are printed.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { performance } = require('node:perf_hooks');
const { parseArgs } = require('node:util');
const { values } = parseArgs({ options: {
  runtime: { type: 'string' }, case: { type: 'string' }, requests: { type: 'string', default: '500' },
  concurrency: { type: 'string', default: '50' }, query: { type: 'string' },
}});
const count = Number(values.requests), concurrency = Number(values.concurrency), operation = values.case;
if (!Number.isInteger(count) || count < 10 || count > 5000 || !Number.isInteger(concurrency) || concurrency < 1 || concurrency > 50
    || !['suggest','lexical','hybrid','recommendations'].includes(operation) || !values.runtime) throw Error('Invalid isolated load options');
const person = JSON.parse(fs.readFileSync(path.join(values.runtime, 'test-users.json'), 'utf8'))[1];
const queries = values.query ? [values.query] : ['库里','勇士','湖人','詹姆斯','凯尔特人','防守','进攻','三分'];
const agent = new http.Agent({ keepAlive: true, maxSockets: concurrency, maxFreeSockets: concurrency });
const route = operation === 'suggest' ? 'suggest' : operation === 'recommendations' ? 'recommendations' : 'search';
function call(number) {
  const params = new URLSearchParams(operation === 'recommendations' ? {scene:'HOME'} :
    {q:queries[number % queries.length], mode:operation === 'hybrid' ? 'hybrid' : 'lexical'});
  const start = performance.now();
  return new Promise(resolve => {
    let settled = false;
    const done = row => { if (!settled) { settled = true; resolve({ms:performance.now()-start,...row}); } };
    const request = http.get({ hostname:'127.0.0.1', port:18080, path:'/api/discovery/'+route+'?'+params,
      headers:{Authorization:'Bearer '+person.token}, agent, timeout:10000 }, response => {
      const chunks=[];
      response.on('data', chunk => chunks.push(chunk));
      response.on('error', () => done({status:0,degraded:true,count:0,strategy:'ResponseError'}));
      response.on('end', () => {
        try {
          const body=JSON.parse(Buffer.concat(chunks).toString('utf8'));
          done({status:response.statusCode,degraded:body.degraded !== false,count:(body.items||[]).length,
            strategy:body.strategyVersion||'error'});
        } catch { done({status:response.statusCode,degraded:true,count:0,strategy:'InvalidJson'}); }
      });
    });
    request.on('error', () => done({status:0,degraded:true,count:0,strategy:'NetworkError'}));
    request.on('timeout', () => request.destroy(new Error('Request timeout')));
  });
}
(async () => {
  for (let n=0;n<16;n++) await call(n);
  let next=0; const rows=[]; const started=performance.now(), cpu=process.cpuUsage();
  await Promise.all(Array.from({length:concurrency},async () => {
    while(next<count) { const number=next++; rows.push(await call(number)); }
  }));
  const elapsed=performance.now()-started, usage=process.cpuUsage(cpu);
  const times=rows.map(row=>row.ms).sort((a,b)=>a-b);
  const percentile=fraction=>+times[Math.ceil(count*fraction)-1].toFixed(1);
  const counts=key=>rows.reduce((result,row)=>{result[row[key]]=(result[row[key]]||0)+1;return result;},{});
  const report={p50Ms:percentile(.5),p95Ms:percentile(.95),p99Ms:percentile(.99),
    requestsPerSecond:+(count*1000/elapsed).toFixed(1),clientCpuMs:+((usage.user+usage.system)/1000).toFixed(1),
    errors:rows.filter(row=>row.status!==200).length,empty:rows.filter(row=>row.count===0).length,
    degraded:rows.filter(row=>row.degraded).length,statusCounts:counts('status'),strategies:counts('strategy')};
  console.log(JSON.stringify({client:'Node '+process.version+' native HTTP',case:operation,requests:count,concurrency,metrics:report}));
})().catch(error=>{console.error(error.message);process.exitCode=1;}).finally(()=>agent.destroy());
