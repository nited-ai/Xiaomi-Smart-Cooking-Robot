import {test} from 'node:test';
import assert from 'node:assert/strict';
import {profileFor,executable,controlProfile} from '../src/control-profile.mjs';
test('normal duration extends through two hours and preserves exact legacy temperatures',()=>{
  for(const seconds of [1,59,60,601,3600,7200])for(const temp of [0,35,37,53,95,100])assert.equal(profileFor(seconds,temp,-15),'normal');
  assert.equal(profileFor(7201,95,-15),null);assert.equal(profileFor(1,34,5),null);
  assert.equal(controlProfile.temperatureUiStep,5);
});
test('high motor ranges and frying are separate executable tuples, never a Cartesian product',()=>{
  for(const speed of [40,50,60,70,80,90,100])assert.equal(profileFor(300,0,speed),'motor');
  for(const tuple of [[301,0,100],[10,0,-100],[10,0,35],[10,50,100],[10,180,100],[1801,180,-10],[10,101,-10],[10,180,-5],[10,0,0],[10,100,0]])assert.equal(profileFor(...tuple),null);
  for(const temp of [105,150,180])assert.equal(profileFor(1800,temp,-10),'fry');
  const machine=(speedLevel,direction,temperatureC=0)=>({kind:'machine',settings:{durationSeconds:10,speedLevel,direction,temperatureC}});
  assert.equal(executable({steps:[machine(10,'clockwise')]}),true);
  assert.equal(executable({steps:[machine(10,'counterclockwise')]}),false);
  assert.equal(executable({steps:[machine(1,'counterclockwise',180)]}),true);
});

import {legacyProfile,resolveControlProfile} from '../src/control-profile.mjs';
import {MachineRecipes} from '../src/machine-recipes.mjs';
import {prepareCatalogEntry,publishCatalogEntry} from '../src/catalog-import.mjs';
import {starterRecipes} from '../data/starter-recipes.mjs';
import {FakeFirestore} from './fake-firestore.mjs';
import {FirebaseStore} from '../src/firebase-store.mjs';
import {assistantInput} from '../src/assistant.mjs';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
test('missing capability retains legacy boundaries; unknown profile is rejected',()=>{
 assert.equal(resolveControlProfile(),legacyProfile);assert.equal(resolveControlProfile(controlProfile.id),controlProfile);assert.throws(()=>resolveControlProfile('future-unreviewed'));
 for(const tuple of [[601,95,5],[10,100,-10],[10,0,100],[10,180,-10]])assert.equal(profileFor(...tuple,legacyProfile),null);
 assert.equal(profileFor(600,95,-30,legacyProfile),'normal');
});
test('each catalogue keeps a compatible brownie version without altering old programs or reviews',async()=>{
 const old=JSON.parse(readFileSync(new URL('./fixtures/brownies-code19.json',import.meta.url),'utf8')),db=new FakeFirestore(),path=`calomaticRecipeCatalog/${old.id}/machinePrograms/c3-p4`;
 db.docs.set(`calomaticRecipeCatalog/${old.id}`,old.record);db.docs.set(path,{...old.variant,reviewCount:3});db.docs.set(`kitchenosMachineIndexV1/${old.id}-p4`,{recipeId:old.id,servings:4,compatible:true});
 const next=await prepareCatalogEntry(starterRecipes.find(r=>r.slug==='brownies'),1);await publishCatalogEntry(db,next);const api=new MachineRecipes({db,now:()=>100});
 assert.deepEqual((await api.catalog(legacyProfile)).recipes.map(r=>r.recipeId),[old.id]);assert.deepEqual((await api.catalog(controlProfile)).recipes.map(r=>r.recipeId),[next.id]);assert.deepEqual((await api.catalog(legacyProfile)).recipes.map(r=>r.recipeId),[old.id]);assert.equal(db.docs.get(path).reviewCount,3);
});
test('preparation separates capability caches, prompts and review receipts for the same source',async()=>{
 const db=new FakeFirestore(),store=new FirebaseStore(db),id='d'.repeat(64),source={id,title:'Kalte Nussstücke',portion:1,ingredients:[{id:'nuts',name:'Nüsse',amount:20,unit:'g'}],guide:{steps:[]},guideStatus:'ready'};let calls=0;
 const api=new MachineRecipes({db,store,service:{user:async uid=>({uid})},fetchImpl:async()=>new Response(JSON.stringify({recipe:source})),model:{complete:async prompt=>{calls++;return {compatible:true,tools:['mixing-blade'],steps:[{kind:'machine',title:'Zerkleinern',instruction:'Nüsse in den Topf geben und zerkleinern.',ingredientUses:[{ingredientId:'nuts',share:1}],settings:{durationSeconds:6,temperatureC:0,speedLevel:prompt.includes('Stufe0,5–3')?6:3,direction:'clockwise'}}]};}}});
 const body={recipeId:id,servings:1,consent:true,requestId:randomUUID()},old=await api.prepare('alice',body),next=await api.prepare('alice',{...body,requestId:randomUUID(),controlProfileId:controlProfile.id});
 assert.equal(old.profileId,legacyProfile.id);assert.equal(old.program.steps[0].settings.speedLevel,3);assert.equal(next.program.steps[0].settings.speedLevel,6);assert.notEqual(old.programKey,next.programKey);assert.equal(calls,2);
 assert.equal((await api.prepare('bob',{...body,requestId:randomUUID()})).programDigest,old.programDigest);assert.equal(calls,2);
 assert.equal((await api.review('alice',{recipeId:id,servings:1,programDigest:old.programDigest,rating:4,worked:true})).reviewCount,1);assert.equal((await api.review('alice',{recipeId:id,servings:1,programDigest:next.programDigest,rating:5,worked:true})).reviewCount,1);
 assert.deepEqual((await api.catalog(legacyProfile)).recipes.map(r=>r.programDigest),[old.programDigest]);assert.deepEqual((await api.catalog(controlProfile)).recipes.map(r=>r.programDigest),[next.programDigest]);
});
test('optional capability does not enter historical assistant request fingerprints',()=>{
 const body={requestId:randomUUID(),message:'Was kann ich kochen?',intent:'chat',consent:true,preferences:{aiConsent:true},pantry:[],plans:[]},old=assistantInput(body),next=assistantInput({...body,controlProfileId:controlProfile.id});assert.equal(Object.hasOwn(old,'controlProfileId'),false);assert.deepEqual(Object.keys(next).slice(0,-1),Object.keys(old));assert.equal(next.controlProfileId,controlProfile.id);
});
