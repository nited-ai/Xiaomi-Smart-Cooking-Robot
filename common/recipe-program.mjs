/** Data validation and catalogue eligibility only. This module never sends device commands. */
export const PROGRAM_VERSION=1;
const KINDS=new Set(['prepare','weigh','machine','rest','finish','external']);
const DIRECTIONS=new Set(['none','clockwise','counterclockwise']);
const text=(v,max)=>typeof v==='string'&&v.trim().length>0&&v.length<=max&&!/[\x00-\x08\x0b\x0c\x0e-\x1f]/.test(v);
const finite=(v,min,max)=>typeof v==='number'&&Number.isFinite(v)&&v>=min&&v<=max;
const https=v=>{try{return typeof v==='string'&&v.length<=4096&&new URL(v).protocol==='https:';}catch{return false;}};
const digest=v=>typeof v==='string'&&/^[a-f0-9]{64}$/.test(v);
const keys=(o,allowed)=>o&&typeof o==='object'&&!Array.isArray(o)&&Object.keys(o).every(k=>allowed.includes(k));

export function validateProgram(program){
  const errors=[];const add=(ok,reason)=>{if(!ok)errors.push(reason);};
  if(!keys(program,['version','id','title','source','deviceModel','servings','tools','cover','steps']))return ['Invalid program envelope'];
  add(program.version===PROGRAM_VERSION,'Unsupported program version');
  add(text(program.id,100)&&text(program.title,180),'Program identity missing');
  add(text(program.deviceModel,80),'Target device missing');
  add(Number.isInteger(program.servings)&&program.servings>=1&&program.servings<=20,'Invalid base servings');
  add(keys(program.source,['url','recipeId','revision'])&&(program.source.url===null||https(program.source.url))&&text(program.source.recipeId,100)&&digest(program.source.revision),'Source revision missing');
  add(Array.isArray(program.tools)&&program.tools.length<=15&&program.tools.every(t=>text(t,80))&&new Set(program.tools).size===program.tools.length,'Invalid tool requirements');
  const image=(v,label)=>add(keys(v,['url','alt','kind','source'])&&https(v.url)&&text(v.alt,200)&&['illustration','photo'].includes(v.kind)&&https(v.source),label);
  image(program.cover,'Cover image missing');
  if(!Array.isArray(program.steps)||program.steps.length<1||program.steps.length>40)return [...errors,'Invalid step count'];
  const ids=new Set();
  for(const [index,step] of program.steps.entries()){
    const p=`Step ${index+1}: `;
    if(!keys(step,['id','kind','title','instruction','ingredients','image','settings'])){errors.push(p+'invalid envelope');continue;}
    add(text(step.id,80)&&!ids.has(step.id),p+'invalid identity');ids.add(step.id);
    add(KINDS.has(step.kind),p+'unknown kind');
    add(text(step.title,160)&&text(step.instruction,2400),p+'instructions missing');
    image(step.image,p+'image missing');
    add(Array.isArray(step.ingredients)&&step.ingredients.length<=80&&step.ingredients.every(i=>keys(i,['name','amount','unit'])&&text(i.name,160)&&(i.amount===null||finite(i.amount,0.001,1000000))&&typeof i.unit==='string'&&i.unit.length<=24&&(i.amount===null||i.unit.trim().length>0)),p+'invalid ingredients');
    const s=step.settings;
    if(!keys(s,['durationSeconds','temperatureC','speedLevel','direction'])){errors.push(p+'settings missing');continue;}
    add(s.durationSeconds===null||Number.isInteger(s.durationSeconds)&&finite(s.durationSeconds,1,259200),p+'invalid duration');
    add(s.temperatureC===null||finite(s.temperatureC,0,250),p+'invalid temperature');
    add(s.speedLevel===null||finite(s.speedLevel,0,30),p+'invalid stirring level');
    add(s.direction===null||DIRECTIONS.has(s.direction),p+'invalid direction');
    if(step.kind==='machine'){
      add(s.durationSeconds!==null&&s.temperatureC!==null&&s.speedLevel!==null&&s.direction!==null,p+'machine parameters incomplete');
      add(s.speedLevel===0?s.direction==='none':s.direction!=='none',p+'direction does not match stirring');
    }else add(s.temperatureC===null&&s.speedLevel===null&&s.direction===null,p+'manual step contains machine commands');
  }
  return errors;
}

function canonical(value){if(Array.isArray(value))return value.map(canonical);if(value&&typeof value==='object')return Object.fromEntries(Object.keys(value).sort().map(k=>[k,canonical(value[k])]));return value;}
export async function programDigest(program){
  const bytes=new TextEncoder().encode(JSON.stringify(canonical(program)));
  if(bytes.length>262144)throw Error('Program too large');
  return [...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(b=>b.toString(16).padStart(2,'0')).join('');
}

/** approval MUST come from the trusted server review store, never the imported recipe. */
export async function assessProgram(program,{sourceRevision,profile,approval}={}){
  const reasons=validateProgram(program);
  if(reasons.length)return {eligible:false,reasons,digest:null};
  const hash=await programDigest(program);
  if(program.source.revision!==sourceRevision)reasons.push('Source recipe has changed or revision is unknown');
  if(!profile||profile.model!==program.deviceModel||!text(profile.revision,100)||profile.verified!==true||!text(profile.speedMappingRevision,100))reasons.push('Verified device and stirring mapping missing');
  if(profile){
    if(!Array.isArray(profile.tools)||!program.tools.every(t=>profile.tools.includes(t)))reasons.push('Required accessory unavailable');
    for(const [i,step] of program.steps.entries()){
      if(step.kind==='external')reasons.push(`Step ${i+1}: external cooking required`);
      if(step.kind!=='machine')continue;
      const s=step.settings;
      if(!Number.isInteger(profile.maxSeconds)||s.durationSeconds>profile.maxSeconds)reasons.push(`Step ${i+1}: duration unsupported`);
      if(s.temperatureC!==0&&(!finite(profile.minTemperatureC,0,250)||!finite(profile.maxTemperatureC,0,250)||s.temperatureC<profile.minTemperatureC||s.temperatureC>profile.maxTemperatureC))reasons.push(`Step ${i+1}: temperature unsupported`);
      if(!Array.isArray(profile.speedLevels)||!profile.speedLevels.includes(s.speedLevel)||!Array.isArray(profile.directions)||!profile.directions.includes(s.direction))reasons.push(`Step ${i+1}: stirring unsupported`);
    }
  }
  if(!approval||approval.status!=='approved'||approval.programDigest!==hash||approval.sourceRevision!==sourceRevision||approval.deviceModel!==program.deviceModel||approval.profileRevision!==profile?.revision)reasons.push('No approval for this exact recipe and device revision');
  return {eligible:reasons.length===0,reasons,digest:hash};
}

/** Any edit creates a different digest and invalidates the old approval. No implicit coercion. */
export function editStep(program,stepId,patch){
  if(!keys(patch,['durationSeconds','temperatureC','speedLevel','direction']))throw Error('Invalid step edit');
  const next=structuredClone(program);const step=next.steps.find(s=>s.id===stepId);if(!step)throw Error('Unknown step');
  Object.assign(step.settings,patch);const errors=validateProgram(next);if(errors.length)throw Error(errors[0]);return next;
}
