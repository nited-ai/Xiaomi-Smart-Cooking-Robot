import {prepareCatalogEntry} from './catalog-import.mjs';
import {programDigest} from '../integrations/recipe-program.mjs';

/** Future OEM functions are declarative plans, never normal-speed substitutes. */
export async function preparePopularEntry(recipe,now=Date.now()){
 const pending=recipe.steps.flatMap((s,index)=>s.kind==='function'?[{index,...s.function}]:[]);
 const projection={...recipe,steps:recipe.steps.map(s=>s.kind==='function'?{...s,kind:'prepare'}:s)};
 const entry=await prepareCatalogEntry(projection,now);
 const requirements={functions:recipe.requiredFunctions,pendingBindings:pending,editorialRevision:recipe.editorial.revision};
 Object.assign(entry.record.provenance,requirements);Object.assign(entry.variant.provenance,requirements);
 entry.pendingBindings=pending;
 if(pending.length){
  // This projection exists only for preview and image alignment. Do not put it
  // in machinePrograms or the runnable index: it omits native function calls.
  entry.previewProgram=entry.variant.program;
  entry.machinePlan={version:1,recipeId:entry.id,servings:recipe.servings,status:'awaiting-function-bindings',steps:recipe.steps,requiredFunctions:recipe.requiredFunctions,pendingBindings:pending,cookingTested:false};
  entry.variant={...entry.variant,compatible:false,reason:'Benötigt noch Rezeptbindungen für: '+[...new Set(pending.map(p=>p.id+':'+p.profile))].join(', ')};
  delete entry.variant.program;delete entry.variant.programDigest;
 }
 return entry;
}
export async function finalizePopularEntry(entry){
 if(entry.pendingBindings.length){
  entry.machinePlan.previewDigest=await programDigest(entry.previewProgram);
  return;
 }
 entry.variant.programDigest=await programDigest(entry.variant.program);
}
