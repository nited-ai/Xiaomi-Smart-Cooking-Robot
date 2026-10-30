import test from 'node:test';
import assert from 'node:assert/strict';
import {validateProgram,programDigest,assessProgram,editStep} from '../../common/recipe-program.mjs';
import {proExample} from './fixtures/pro-example.mjs';

// Synthetic test limits; not a production Xiaomi release or mapping.
const profile={model:'xiaomi-c3',revision:'test-only-1',verified:true,speedMappingRevision:'synthetic-test-mapping',tools:['mixing-blade'],maxSeconds:1800,minTemperatureC:35,maxTemperatureC:100,speedLevels:[0,1,3,5],directions:['none','clockwise','counterclockwise']};
const sourceRevision=proExample.source.revision;
async function approval(program){return {status:'approved',programDigest:await programDigest(program),sourceRevision,deviceModel:'xiaomi-c3',profileRevision:profile.revision};}
const withinLimits=()=>{const p=structuredClone(proExample);p.steps[2].settings.temperatureC=95;return p;};

test('Complete Pro design data is not automatically a compatible recipe',async()=>{
  assert.deepEqual(validateProgram(proExample),[]);
  const result=await assessProgram(proExample);assert.equal(result.eligible,false);
  assert.ok(result.reasons.some(s=>s.includes('approval')));
});
test('General catalogue guide ready does not qualify as machine program',async()=>{
  const result=await assessProgram({guideStatus:'ready',status:'recipe',guide:{steps:[]}});
  assert.equal(result.eligible,false);
});
test('Matching trusted approval and verified profile qualify the exact revision',async()=>{
  const p=withinLimits();const result=await assessProgram(p,{sourceRevision,profile,approval:await approval(p)});
  assert.equal(result.eligible,true);
});
test('Marketing 180-degree setting is refused by the supplied 100-degree test profile',async()=>{
  const result=await assessProgram(proExample,{sourceRevision,profile,approval:await approval(proExample)});
  assert.equal(result.eligible,false);assert.ok(result.reasons.some(s=>s.includes('temperature unsupported')));
});
test('Editing a single parameter invalidates the previous recipe approval',async()=>{
  const p=withinLimits();const approved=await approval(p);const edited=editStep(p,'onion',{durationSeconds:9});
  assert.notEqual(await programDigest(p),await programDigest(edited));
  assert.equal((await assessProgram(edited,{sourceRevision,profile,approval:approved})).eligible,false);
});
test('An updated source or different device/profile cannot inherit an approval',async()=>{
  const p=withinLimits(),approved=await approval(p);
  for(const context of [{sourceRevision:'0'.repeat(64),profile},{sourceRevision,profile:{...profile,model:'other-machine'}},{sourceRevision,profile:{...profile,revision:'test-only-2'}}])assert.equal((await assessProgram(p,{...context,approval:approved})).eligible,false);
});
test('No silent defaults for missing temperature, duration or stirring',()=>{
  for(const key of ['temperatureC','durationSeconds','speedLevel','direction']){
    const p=structuredClone(proExample);delete p.steps[1].settings[key];assert.ok(validateProgram(p).length>0);
  }
});
test('Unknown tools, absent stirring mapping, external cooking and missing step image block eligibility',async()=>{
  const p=withinLimits(),approved=await approval(p);
  assert.equal((await assessProgram(p,{sourceRevision,profile:{...profile,tools:[]},approval:approved})).eligible,false);
  assert.equal((await assessProgram(p,{sourceRevision,profile:{...profile,speedMappingRevision:null},approval:approved})).eligible,false);
  const external=structuredClone(p);external.steps[0].kind='external';
  assert.equal((await assessProgram(external,{sourceRevision,profile,approval:await approval(external)})).eligible,false);
  const missing=structuredClone(p);missing.steps[0].image=null;assert.ok(validateProgram(missing).length>0);
});
test('Untrusted execution fields and malformed edits are rejected',()=>{
  assert.ok(validateProgram({...proExample,command:'START'}).length>0);
  assert.throws(()=>editStep(proExample,'onion',{speedLevel:NaN}));
  assert.throws(()=>editStep(proExample,'onion',{command:'START'}));
  assert.throws(()=>editStep(proExample,'onion',{direction:'none'}));
});
