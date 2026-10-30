import {sharedSnapshot} from './recipe-creation.mjs';
import {machineVariant,PREPARATION_REVISION} from './machine-recipes.mjs';
import {controlProfile,executable} from './control-profile.mjs';
import {hash,requireValue} from './domain.mjs';
import {programDigest} from '../integrations/recipe-program.mjs';

/** Offline preparation: same identity, portion basis and validators as creation. */
export async function prepareCatalogEntry(entry,now=Date.now()){
  requireValue(entry&&Number.isInteger(entry.servings)&&entry.servings>=1&&entry.servings<=6,'Portionen fehlen');
  const ids=new Map();
  const ingredients=entry.ingredients.map((item,index)=>{
    requireValue(typeof item.id==='string'&&!ids.has(item.id)&&typeof item.name==='string'&&item.name.trim()&&Number.isFinite(item.amount)&&item.amount>0&&['g','ml','TL','EL','Stück'].includes(item.unit),'Ungültige Zutat');
    ids.set(item.id,`ingredient-${index}`);return {...item,id:`ingredient-${index}`};
  });
  const answer={compatible:true,tools:entry.tools,steps:entry.steps.map(step=>({...step,ingredientUses:step.ingredientUses.map(use=>{requireValue(ids.has(use.ingredientId),'Unbekannte Zutat');return {...use,ingredientId:ids.get(use.ingredientId)};})}))};
  const source={id:'0'.repeat(64),title:entry.title,ingredients,portion:entry.servings,guide:{steps:[]}};
  const shared=sharedSnapshot(source,answer);requireValue(shared,'Rezept ist nicht veröffentlichbar');
  // Timing and notes belong to the content identity; provenance stays separate.
  Object.assign(shared.record.guide,entry.timing,{difficulty:'einfach',tips:entry.tips});
  const {version,title,portion,status,guide}=shared.record;
  const base={version,title,portion,ingredients:shared.record.ingredients,status,guide};
  const id=hash(JSON.stringify(base)),coverUrl=`https://app.kalomatik.ai/api/recipes/${id}/cover`;
  // Match the existing public GET projection exactly: nutrient fields are not
  // included by publicSource(), and the PWA represents an empty heat as null.
  // Otherwise preparing the same recipe would silently miss this cached version.
  const projectedGuide={...guide,steps:guide.steps.map(step=>({...step,heat:step.heat||null}))};
  const publicSource={id,title,portion:1,ingredients:shared.record.ingredients.map(({id,name,amount,unit})=>({id,name,amount,unit})),guide:projectedGuide,coverUrl};
  const variant=machineVariant(publicSource,entry.servings,answer);requireValue(variant.compatible,'Nicht im App-Profil ausführbar');
  const reference=entry.reference;
  requireValue(reference&&new URL(reference.url).protocol==='https:'&&typeof reference.title==='string','Quellenlink fehlt');
  // Original inspiration is visible without changing the canonical recipe ID.
  variant.program.source.url=reference.url;
  const result={...variant,recipeId:id,servings:entry.servings,private:false,curated:true,timing:{...entry.timing},profileId:controlProfile.id,preparationRevision:PREPARATION_REVISION,stepImagesMatch:true,createdAt:now,programDigest:await programDigest(variant.program),provenance:{...reference,kind:'authored-adaptation',cookingTested:false,batch:reference.batch??'starter-recipes-2026-10-19'}};
  if(entry.supersedesRecipeId){requireValue(/^[a-f0-9]{64}$/.test(entry.supersedesRecipeId)&&entry.supersedesRecipeId!==id,"Ungültige vorherige Rezeptfassung");result.foodIdentity=entry.supersedesRecipeId;result.supersedesRecipeId=entry.supersedesRecipeId;shared.record.foodIdentity=entry.supersedesRecipeId;}
  return {id,slug:entry.slug,record:{...shared.record,origin:'kitchen-curated',provenance:result.provenance,createdAt:now,updatedAt:now},variant:result};
}

/** One atomic contribution, no overwrite of existing recipes, reviews or programs. */
export async function publishCatalogEntry(db,prepared){
  requireValue(prepared.variant?.compatible===true&&prepared.variant.program&&executable(prepared.variant.program)&&!prepared.pendingBindings?.length,'Rezept benötigt noch Maschinenfunktionen und darf nicht als ausführbar veröffentlicht werden');
  const parent=db.collection('calomaticRecipeCatalog').doc(prepared.id),program=parent.collection('machinePrograms').doc(`c3-p${prepared.variant.servings}`),index=db.collection('kitchenosMachineIndexV1').doc(`${prepared.id}-p${prepared.variant.servings}`);
  return db.runTransaction(async tx=>{
    const [existing,saved]=await Promise.all([tx.get(parent),tx.get(program)]);
    const predecessor=prepared.variant.supersedesRecipeId,oldParent=predecessor?db.collection("calomaticRecipeCatalog").doc(predecessor):null,oldRecord=oldParent?await tx.get(oldParent):null;
    const oldIndexRef=predecessor?db.collection('kitchenosMachineIndexV1').doc(`${predecessor}-p${prepared.variant.servings}`):null,oldIndex=oldIndexRef?await tx.get(oldIndexRef):null;
    if(oldRecord?.exists)requireValue(oldRecord.data().origin==='kitchen-curated'&&oldRecord.data().title===prepared.record.title,"Vorherige Fassung gehört nicht zu dieser Redaktion");
    requireValue(!existing.exists||existing.data().withdrawn!==true&&existing.data().publicationStatus!=='withdrawn'&&existing.data().private!==true&&existing.data().reviewExcluded!==true,'Vorhandenes Rezept ist nicht öffentlich');
    requireValue(!saved.exists||saved.data().sourceRevision===prepared.variant.sourceRevision&&saved.data().programDigest===prepared.variant.programDigest,'Vorhandene Maschinenfassung weicht ab');
    if(!existing.exists)tx.create(parent,prepared.record);
    if(!saved.exists)tx.create(program,prepared.variant);
    if(oldRecord?.exists)tx.set(oldIndexRef,{...(oldIndex.exists?oldIndex.data():{recipeId:predecessor,servings:prepared.variant.servings,compatible:false}),supersededBy:prepared.id});
    tx.set(index,{recipeId:prepared.id,servings:prepared.variant.servings,compatible:true,sourceRevision:prepared.variant.sourceRevision});
    return {id:prepared.id,title:prepared.record.title,created:!existing.exists,programCreated:!saved.exists};
  });
}
