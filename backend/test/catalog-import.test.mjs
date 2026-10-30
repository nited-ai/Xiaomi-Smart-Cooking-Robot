import {test} from 'node:test';
import assert from 'node:assert/strict';
import {starterRecipes} from '../data/starter-recipes.mjs';
import {prepareCatalogEntry,publishCatalogEntry} from '../src/catalog-import.mjs';
import {FakeFirestore} from './fake-firestore.mjs';
import {validateProgram,programDigest} from '../integrations/recipe-program.mjs';
import {executable} from '../src/control-profile.mjs';
import {hash} from '../src/domain.mjs';
import {readFileSync} from 'node:fs';
import {MachineRecipes} from '../src/machine-recipes.mjs';

test('all authored recipes have complete ingredient accounting, supported settings and distinct stable identities',async()=>{
  const ids=new Set();
  for(const recipe of starterRecipes){
    const entry=await prepareCatalogEntry(recipe,1),again=await prepareCatalogEntry(recipe,2);
    assert.equal(entry.id,again.id);assert.equal(entry.variant.programDigest,again.variant.programDigest);ids.add(entry.id);
    assert.deepEqual(validateProgram(entry.variant.program),[]);assert.equal(executable(entry.variant.program),true);
    assert.equal(entry.variant.programDigest,await programDigest(entry.variant.program));
    const publicIngredients=entry.record.ingredients.map(({id,name,amount,unit})=>({id,name,amount,unit}));
    const publicGuide={...entry.record.guide,steps:entry.record.guide.steps.map(step=>({...step,heat:null}))};
    assert.equal(entry.variant.sourceRevision,hash(JSON.stringify([entry.id,entry.record.title,publicIngredients,publicGuide])));
    assert.equal(entry.record.reviewStatus,'unreviewed');assert.equal(entry.variant.reviewStatus,'ai-draft');
    assert.equal(entry.record.provenance.cookingTested,false);assert.equal(entry.variant.program.source.url,recipe.reference.url);
    assert.ok(entry.variant.program.steps.every(s=>s.settings.durationSeconds===null||s.settings.durationSeconds<=7200),'Android timer envelope');
    assert.ok(entry.variant.program.steps.some(s=>s.kind==='machine'));
    for(const ingredient of recipe.ingredients){
      const total=entry.variant.program.steps.flatMap(s=>s.ingredients).filter(i=>i.name===ingredient.name).reduce((sum,i)=>sum+i.amount,0);
      assert.ok(Math.abs(total-ingredient.amount)<0.000001,ingredient.name+' amount preserved');
    }
  }
  assert.equal(ids.size,6);
});

test('publishing uses the existing shared catalogue and preserves ratings on repeat',async()=>{
  const db=new FakeFirestore(),entry=await prepareCatalogEntry(starterRecipes[0],1);
  assert.equal((await publishCatalogEntry(db,entry)).created,true);
  const path=`calomaticRecipeCatalog/${entry.id}/machinePrograms/c3-p4`,saved=db.docs.get(path);
  db.docs.set(path,{...saved,reviewCount:2,workedCount:2,reviewStatus:'user-validated'});
  assert.equal((await publishCatalogEntry(db,entry)).created,false);assert.equal(db.docs.size,3);
  assert.equal(db.docs.get(path).reviewCount,2);assert.equal(db.docs.get(path).reviewStatus,'user-validated');
  assert.ok([...db.docs.keys()].every(key=>key.startsWith('calomaticRecipeCatalog/')||key.startsWith('kitchenosMachineIndexV1/')));
});

test('new brownies preserve favorite food identity and old programs, reviews and receipts',async()=>{
  const old=JSON.parse(readFileSync(new URL('./fixtures/brownies-code19.json',import.meta.url),'utf8')),db=new FakeFirestore();
  const parent=`calomaticRecipeCatalog/${old.id}`,program=parent+'/machinePrograms/c3-p4',index=`kitchenosMachineIndexV1/${old.id}-p4`;
  const original={...old.variant,reviewCount:4,workedCount:3,reviewStatus:'user-validated'};db.docs.set(parent,old.record);db.docs.set(program,original);db.docs.set(index,{recipeId:old.id,servings:4,compatible:true,sourceRevision:old.variant.sourceRevision});
  const next=await prepareCatalogEntry(starterRecipes.find(r=>r.slug==='brownies'),1);assert.notEqual(next.id,old.id);assert.notEqual(next.variant.programDigest,old.variant.programDigest);assert.equal(next.variant.foodIdentity,old.id);
  await publishCatalogEntry(db,next);assert.deepEqual(db.docs.get(program),original);assert.deepEqual(db.docs.get(parent),old.record);assert.equal(db.docs.get(index).compatible,true);assert.equal(db.docs.get(index).supersededBy,next.id);
  db.docs.set(parent+'/machinePrograms/c3-p2',{...old.variant,servings:2});db.docs.set(`kitchenosMachineIndexV1/${old.id}-p2`,{recipeId:old.id,servings:2,compatible:true});
  const visible=await new MachineRecipes({db}).loadCatalog();assert.deepEqual(visible.recipes.map(r=>r.recipeId),[next.id]);assert.equal(visible.recipes[0].program.steps.length,14);assert.equal(visible.recipes[0].foodIdentity,old.id);
});

test('invalid ingredient accounting, device settings and changed stored programs cannot be imported',async()=>{
  for(const mutate of [r=>r.steps.find(s=>s.kind==='machine').settings.speedLevel=7,r=>r.steps.find(s=>s.kind==='machine').settings.temperatureC=110,r=>r.steps.find(s=>s.kind==='weigh').ingredientUses=[]]){
    const recipe=structuredClone(starterRecipes[0]);mutate(recipe);await assert.rejects(prepareCatalogEntry(recipe));
  }
  const db=new FakeFirestore(),entry=await prepareCatalogEntry(starterRecipes[0]);await publishCatalogEntry(db,entry);
  const path=`calomaticRecipeCatalog/${entry.id}/machinePrograms/c3-p4`;db.docs.set(path,{...db.docs.get(path),programDigest:'f'.repeat(64)});
  await assert.rejects(publishCatalogEntry(db,entry),/weicht ab/);
  const parent=`calomaticRecipeCatalog/${entry.id}`;db.docs.set(parent,{...entry.record,withdrawn:true});
  await assert.rejects(publishCatalogEntry(db,entry),/nicht öffentlich/);
});
