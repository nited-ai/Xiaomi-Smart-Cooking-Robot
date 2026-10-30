import {publishCatalogEntry} from './catalog-import.mjs';
import {programDigest} from '../integrations/recipe-program.mjs';
import {requireValue} from './domain.mjs';
import {describeRecipeImage,imageLibraryRecord,IMAGE_LIBRARY,IMAGE_USES} from './recipe-image-library.mjs';

export function checkedAssets(entry,jobs,packed,reviews){
 const expected=jobs.filter(j=>j.recipeId===entry.id);
 requireValue(expected.some(j=>j.kind==='cover'),'Rezeptbild fehlt');
 return expected.map(job=>{
  const asset=packed.find(a=>a.key===job.key&&a.id===job.id&&a.path===job.path);
  requireValue(asset&&reviews[job.key]?.sha256===asset.sha256&&reviews[job.key]?.status==='approved','Bild noch nicht geprüft: '+job.key);
  return {...asset,library:imageLibraryRecord(asset,describeRecipeImage(job,entry),reviews[job.key])};
 });
}
export async function publishPopularEntry(db,entry,assets,now=Date.now()){
 // Images must already exist in shared storage; this only records immutable
 // reviewed references. The caller verifies JPEG hashes and upload responses.
 for(const asset of assets){
  requireValue(asset.library?.status==='approved','Geprüfter Bibliothekseintrag fehlt');
  const ref=db.collection(asset.kind==='cover'?'dishImages':'recipeStepImages').doc(asset.id);
  await db.runTransaction(async tx=>{
   const old=await tx.get(ref);
   if(old.exists){requireValue(old.data().status==='ready'&&old.data().sha256===asset.sha256,'Vorhandener Bildzustand weicht ab');if(old.data().libraryAssetId!==asset.library.id)tx.set(ref,{...old.data(),libraryAssetId:asset.library.id,updatedAt:now});return;}
   tx.create(ref,{id:asset.id,style:asset.kind==='cover'?'dish-photo-v1':'recipe-step-v1',path:asset.path,status:'ready',sha256:asset.sha256,libraryAssetId:asset.library.id,model:'built-in-imagegen',imageKind:'illustration',reviewedAt:now,updatedAt:now,batch:'popular-50-2026-10-21',...(asset.kind==='step'?{recipeId:entry.id,index:asset.index}:{})});
  });
  await db.runTransaction(async tx=>{
   const library=db.collection(IMAGE_LIBRARY).doc(asset.library.id),old=await tx.get(library);
   if(old.exists){requireValue(old.data().sha256===asset.sha256,'Bibliotheksbild weicht ab');const prior=old.data();tx.set(library,{...prior,searchKeywords:[...new Set([...(prior.searchKeywords??[]),...asset.library.searchKeywords])],searchTokens:[...new Set([...(prior.searchTokens??[]),...asset.library.searchTokens])],recipeContexts:[...new Set([...(prior.recipeContexts??[prior.recipeContext]),entry.record.title])],updatedAt:now});}
   else tx.create(library,{...asset.library,recipeContexts:[entry.record.title],updatedAt:now});
   tx.set(db.collection(IMAGE_USES).doc(entry.id+'-'+(asset.kind==='cover'?'cover':asset.index)),{recipeId:entry.id,slug:entry.slug,kind:asset.kind,...(asset.index===undefined?{}:{index:asset.index}),libraryAssetId:asset.library.id,imageId:asset.id,compatibilityPath:asset.path,sha256:asset.sha256,updatedAt:now});
  });
 }
 if(entry.pendingBindings.length){
  const ref=db.collection('calomaticRecipeDrafts').doc(entry.id);
  const digest=await programDigest(entry.previewProgram);
  return db.runTransaction(async tx=>{
   const old=await tx.get(ref);
   if(old.exists){requireValue(old.data().previewDigest===digest,'Vorhandener Rezeptentwurf weicht ab');return {id:entry.id,title:entry.record.title,status:'awaiting-function-bindings',created:false};}
   tx.create(ref,{...entry.record,slug:entry.slug,status:'awaiting-function-bindings',publicationStatus:'draft',machinePlan:entry.machinePlan,previewProgram:entry.previewProgram,previewDigest:digest,images:assets.map(({key,id,path,sha256,kind,index,library})=>({key,id,path,sha256,kind,libraryAssetId:library.id,...(index===undefined?{}:{index})})),createdAt:now});
   return {id:entry.id,title:entry.record.title,status:'awaiting-function-bindings',created:true};
  });
 }
 return {...await publishCatalogEntry(db,entry),status:'published'};
}
