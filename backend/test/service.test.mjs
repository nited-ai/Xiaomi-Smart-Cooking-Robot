import { test } from 'node:test';
import { strict as assert } from 'node:assert';
import { randomUUID } from 'node:crypto';
import { FirebaseStore } from '../src/firebase-store.mjs';
import { KitchenService } from '../src/service.mjs';
import { challenge, opaque, recipe, recipeRevision, generationInput } from '../src/domain.mjs';
import { FakeFirestore } from './fake-firestore.mjs';

const now = 1780000000000;
const sample = () => ({ id: randomUUID(), title:'Reispfanne', servings:2, ingredients:['200 g Reis','300 g Tomaten'], steps:['Reis und Tomaten nach Anleitung garen.'] });
const input = () => ({ requestId:randomUUID(), request:'Aus meinem Vorrat kochen', pantry:[{name:'Reis',amount:'300',unit:'g',expires:''}], plannedRecipes:[], preferences:{servings:2,diet:'vegetarisch',allergies:'',dislikes:'',notes:'',aiConsent:true} });
function setup(model, dailyLimit = 20) {
  const db = new FakeFirestore(), store = new FirebaseStore(db,dailyLimit);
  const auth = { verifyIdToken:async (token, revoked) => {
    assert.equal(revoked,true);
    if (token === 'bad') throw new Error('bad token');
    return {uid:token,email_verified:token !== 'unverified',auth_time: now/1000,
      firebase:{sign_in_provider: token === 'anonymous' ? 'anonymous' : token === 'custom' ? 'custom' : token === 'unverified' ? 'password' : 'google.com'}};
  }, createCustomToken:async uid => `custom-for-${uid}` };
  let calls = 0;
  const service = new KitchenService({store,auth,now:()=>now,config:{firebase:{apiKey:'public-test-key'}},model:{generate:async data=>{calls++;return model ? model(data) : sample();}}});
  return {db,store,service,calls:()=>calls};
}
const status = code => error => error.status === code;

test('planning context retains recipe basis separately from desired servings',()=>{
  const body=input();body.plannedRecipes=[{title:'Reispfanne',date:'2026-10-19',ingredients:['200 g Reis'],servings:4,baseServings:2}];
  const planned=generationInput(body).plannedRecipes[0];
  assert.equal(planned.servings,4);assert.equal(planned.baseServings,2);
  delete body.plannedRecipes[0].baseServings;
  assert.throws(()=>generationInput(body),status(422));
});
test('PKCE code: wrong verifier cannot consume; correct proof succeeds exactly once under concurrency',async()=>{
  const {service} = setup(), verifier=opaque();
  const {code}=await service.issueCode('alice',{codeChallenge:challenge(verifier)});
  await assert.rejects(service.exchange({code,codeVerifier:opaque()}),status(401));
  const results=await Promise.allSettled([service.exchange({code,codeVerifier:verifier}),service.exchange({code,codeVerifier:verifier})]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
  assert.equal(results.find(r=>r.status==='fulfilled').value.customToken,'custom-for-alice');
});
test('expired code and anonymous/custom/unverified browser identities are rejected',async()=>{
  const {service,store}=setup(), verifier=opaque(), code=opaque();
  const {hash}=await import('../src/domain.mjs');await store.createCode(hash(code),{uid:'alice',codeChallenge:challenge(verifier),expiresAt:now-1});
  await assert.rejects(service.exchange({code,codeVerifier:verifier}),status(401));
  for(const token of ['bad','anonymous','custom','unverified']) await assert.rejects(service.issueCode(token,{codeChallenge:challenge(verifier)}),status(401));
});
test('unauthenticated generation and missing AI consent fail before any model call',async()=>{
  const {service,calls}=setup(), body=input();
  await assert.rejects(service.generate('bad',body),status(401));
  body.preferences.aiConsent=false;await assert.rejects(service.generate('alice',body),status(422));assert.equal(calls(),0);
});
test('same job returns saved result without another model charge; collision and quota are transactional',async()=>{
  const {service,calls}=setup(null,1), body=input();
  const first=await service.generate('alice',body), retry=await service.generate('alice',body);
  assert.deepEqual(retry,first);assert.equal(calls(),1);assert.equal(first.status,'draft');
  await assert.rejects(service.generate('alice',{...body,request:'Anderer Inhalt'}),status(409));
  await assert.rejects(service.generate('alice',input()),status(429));
  await service.generate('bob',body);assert.equal(calls(),2);
});
test('parallel duplicate generation has one model owner',async()=>{
  let release;const pending=new Promise(resolve=>{release=resolve;});const {service,calls}=setup(async()=>{await pending;return sample();});
  const body=input(), first=service.generate('alice',body);
  await new Promise(resolve=>setImmediate(resolve));
  await assert.rejects(service.generate('alice',body),status(409));release();await first;assert.equal(calls(),1);
});
test('uncertain or invalid model result is not automatically charged again',async()=>{
  const {service,calls}=setup(()=>({title:'Ohne Zutaten'})), body=input();
  await assert.rejects(service.generate('alice',body));await assert.rejects(service.generate('alice',body),status(409));assert.equal(calls(),1);
});
test('private draft retrieval is scoped by authenticated UID; pantry and health context are not persisted',async()=>{
  const {service,db}=setup(), body=input();body.preferences.allergies='TEST_PRIVATE_ALLERGY';body.request='TEST_PRIVATE_PROMPT';
  const generated=await service.generate('alice',body);
  assert.equal((await service.drafts('alice')).recipes[0].recipe.id,generated.recipe.id);
  assert.equal((await service.drafts('bob')).recipes.length,0);
  const stored=JSON.stringify([...db.docs.values()]);assert.ok(!stored.includes('TEST_PRIVATE_ALLERGY')&&!stored.includes('TEST_PRIVATE_PROMPT'));
});
test('catalog contributions project text only, deduplicate content, and remain drafts without cooking feedback',async()=>{
  const {service}=setup(), value=sample();
  const body={recipe:{...value,pantry:'PRIVATE',command:'START'},consent:true,preferences:{allergies:'PRIVATE'}};
  const a=await service.contribute('alice',body), b=await service.contribute('bob',{...body,recipe:{...value,id:randomUUID()}});
  assert.equal(a.catalogId,b.catalogId);const rows=(await service.catalog('alice')).recipes;assert.equal(rows.length,1);
  assert.equal(rows[0].validatorCount,0);assert.equal(rows[0].workedCount,0);assert.ok(!JSON.stringify(rows).includes('PRIVATE'));
  assert.ok(!Object.hasOwn(rows[0].recipe,'command'));
  await assert.rejects(service.contribute('alice',{recipe:value,consent:false}),status(422));
});
test('cooking feedback counts distinct accounts once, can be corrected, and rejects other revisions',async()=>{
  const {service}=setup(), shared=await service.contribute('alice',{recipe:sample(),consent:true});
  const body={...shared,cooked:true,worked:true,rating:5};
  await service.validate('alice',body);let feedback=await service.validate('alice',body);assert.equal(feedback.validatorCount,1);assert.equal(feedback.workedCount,1);
  feedback=await service.validate('alice',{...body,worked:false,rating:2});assert.equal(feedback.workedCount,0);assert.equal(feedback.averageRating,2);
  feedback=await service.validate('bob',{...body,rating:4});assert.equal(feedback.validatorCount,2);assert.equal(feedback.workedCount,1);assert.equal(feedback.averageRating,3);
  await assert.rejects(service.validate('eve',{...body,revision:'0'.repeat(64)}),status(409));
  await assert.rejects(service.validate('eve',{...body,cooked:false}),status(422));
});
test('recipe revision changes on edits and private metadata is excluded from the recipe contract',()=>{
  const r=recipe(sample());assert.notEqual(recipeRevision(r),recipeRevision({...r,steps:['Anders garen.']}));
  const body=input();assert.ok(!Object.hasOwn(generationInput({...body,uid:'spoofed',command:'START'}),'uid'));
  assert.throws(()=>recipe({...sample(),servings:0}));assert.throws(()=>recipe({...sample(),ingredients:[]}));
});
