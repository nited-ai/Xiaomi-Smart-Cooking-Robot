import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {photoPurpose,validateRecipePhoto,photoAddendumEvidence} from '../src/recipe-photo.mjs';
import {hash} from '../src/domain.mjs';
import {assistantInput,assistantResult,KitchenAssistant,GroqKitchenModel} from '../src/assistant.mjs';
import {FirebaseStore} from '../src/firebase-store.mjs';
import {FakeFirestore} from './fake-firestore.mjs';

const source=()=>({version:1,title:'Omas Pfannkuchen',transcription:'Omas Pfannkuchen\nFür 4 Portionen\n250 g Mehl\n2 Eier\nMehl und Eier verrühren.\nIn der Pfanne ausbacken.',originalServings:4,servingsEvidence:'Für 4 Portionen',originalIngredients:[{name:'Mehl',amount:250,unit:'g',amountEvidence:'250 g Mehl'},{name:'Eier',amount:2,unit:'Stück',amountEvidence:'2 Eier'}],originalSteps:[{instruction:'Mehl und Eier verrühren.',evidence:'Mehl und Eier verrühren.'},{instruction:'In der Pfanne ausbacken.',evidence:'In der Pfanne ausbacken.'}],unclear:[]});
const request=()=>({requestId:randomUUID(),message:'Das ist Omas Rezept, bereite es für die Maschine auf.',intent:'pantry',consent:true,preferences:{aiConsent:true,servings:2},pantry:[],plans:[],photo:{mimeType:'image/jpeg',data:Buffer.from([255,216,255,217]).toString('base64')}});
const modelAnswer=()=>({message:'Ich lese das Originalrezept.',photoType:'recipe',recipePhoto:source(),pantryItems:[{name:'Mehl',amount:'900',unit:'g',amountLegible:true,confidence:1}]});
const setup=(answer=modelAnswer())=>{
  const db=new FakeFirestore(),store=new FirebaseStore(db);let creationCalls=0,seen;
  const assistant=new KitchenAssistant({service:{user:async uid=>({uid})},store,model:{respond:async()=>answer},recipeCreation:{create:async(token,body)=>{creationCalls++;seen={token,body};return {message:'Originalrezept vorbereitet.',variant:{recipeId:'private-recipe'}};}}});
  return {assistant,db,calls:()=>creationCalls,seen:()=>seen};
};

test('explicit recipe-photo wording wins over a stale pantry flag',()=>{
  assert.equal(photoPurpose('Das ist Omas Rezept, bereite es für die Maschine auf.'),'recipe');
  assert.equal(photoPurpose('Scanne dieses Rezept ein.'),'recipe');
  assert.equal(photoPurpose('Erkenne die Lebensmittel in meinem Vorrat.'),'pantry');
  assert.equal(photoPurpose('Was siehst du hier?'),'unknown');
  const parsed=assistantInput(request());assert.equal(parsed.intent,'chat');assert.equal(parsed.photoPurpose,'recipe');
});
test('complete original ingredient quantities, count units and instructions retain their source evidence',()=>{
  const checked=validateRecipePhoto(source());assert.deepEqual(checked.issues,[]);assert.equal(checked.sourceRecipe.originalServings,4);
  assert.equal(checked.sourceRecipe.originalIngredients[1].amount,2);assert.equal(checked.sourceRecipe.originalIngredients[1].unit,'Stück');
  assert.equal(checked.sourceRecipe.originalSteps[1].instruction,'In der Pfanne ausbacken.');
});
test('a missing original serving count is never substituted from requested target portions',()=>{
  const value=source();value.originalServings=null;value.servingsEvidence='';
  const result=validateRecipePhoto(value);assert.equal(result.sourceRecipe,null);assert.match(result.issues.join(' '),/Portionen/);
});
test('unreadable, invented, changed or unsupported ingredient quantities block creation',()=>{
  for(const mutate of [v=>v.originalIngredients[0].amount=null,v=>v.originalIngredients[0].amount=500,
    v=>v.originalIngredients[0].unit='kg',v=>v.originalIngredients[0].amountEvidence='500 g Mehl',
    v=>v.originalIngredients[0].unit='Packung',v=>v.unclear=['Menge für das Öl ist abgeschnitten']]){
    const value=source();mutate(value);const checked=validateRecipePhoto(value);assert.equal(checked.sourceRecipe,null);assert.ok(checked.issues.length>0);
  }
});
test('literal fractions are allowed without estimating quantities',()=>{
  const value=source();value.transcription+='\n½ TL Salz';value.originalIngredients.push({name:'Salz',amount:0.5,unit:'TL',amountEvidence:'½ TL Salz'});
  assert.ok(validateRecipePhoto(value).sourceRecipe);
});
test('instructions must be literal recipe content rather than model-invented machine settings',()=>{
  const value=source();value.originalSteps[0].instruction='Bei 95 Grad für 600 Sekunden rühren.';
  assert.equal(validateRecipePhoto(value).sourceRecipe,null);
});
test('no recipe-photo response can become pantry additions or a UI command',()=>{
  const input=assistantInput(request()),response=assistantResult({...modelAnswer(),selectControlId:'start'},input);
  assert.deepEqual(response.pantryItems,[]);assert.equal(response.uiRequest,undefined);
});
test('a complete recipe photo invokes the existing recipe service once, without photo or serving defaults',async()=>{
  const run=setup(),body=request();const first=await run.assistant.message('alice',body);
  assert.equal(run.calls(),1);assert.deepEqual(await run.assistant.message('alice',body),first);assert.equal(run.calls(),1);
  assert.deepEqual(first.pantryItems,[]);assert.equal(first.recipePhotoStatus,'prepared');
  const passed=run.seen();assert.equal(passed.token,'alice');assert.equal(passed.body.photo,undefined);assert.equal(passed.body.intent,'chat');
  assert.equal(passed.body.sourceRecipe.originalServings,4);assert.equal(passed.body.preferences.servings,2);
  assert.deepEqual(passed.body.sourceRecipe.originalIngredients,source().originalIngredients);
  assert.equal(JSON.stringify([...run.db.docs.values()]).includes(body.photo.data),false);
});
test('missing evidence produces a useful clarification, no stock and no recipe-service call',async()=>{
  const answer=modelAnswer();answer.recipePhoto.originalIngredients[0].amount=null;
  const run=setup(answer),result=await run.assistant.message('alice',request());
  assert.equal(run.calls(),0);assert.equal(result.recipePhotoStatus,'needs-clarification');assert.deepEqual(result.pantryItems,[]);
  assert.match(result.message,/Mehl/);assert.match(result.message,/Menge und Einheit/);assert.equal(result.variant,undefined);
});
test('a recipe image in an ambiguous photo request asks for intent instead of registering pantry',async()=>{
  const run=setup(),body={...request(),message:'Was ist auf diesem Bild?'};
  const result=await run.assistant.message('alice',body);assert.equal(run.calls(),0);assert.deepEqual(result.pantryItems,[]);
  assert.match(result.message,/Möchtest du es/);assert.equal(result.variant,undefined);
});
test('the vision prompt separates recipe documents, quoted evidence and pantry images',async()=>{
  let sent;const model=new GroqKitchenModel({apiKey:'fixture-only',model:'fixture',fetchImpl:async(url,options)=>{sent=JSON.parse(options.body);return new Response(JSON.stringify({choices:[{finish_reason:'stop',message:{content:JSON.stringify(modelAnswer())}}]}));}});
  await model.respond(assistantInput(request()));assert.match(sent.messages[0].content,/recipePhoto/);assert.match(sent.messages[0].content,/niemals.*pantryItems/);
  assert.equal(sent.messages[1].content[1].type,'image_url');assert.equal(JSON.stringify(sent).includes('fixture-only'),false);
});

const addendum=()=>{
  const photoDigest=hash(Buffer.from(request().photo.data,'base64'));
  const userMessages=['Nachtrag zum selben Rezeptfoto: Originalrezept für 4 Portionen; Mehl: 300 g.'];
  const value=source();value.photoDigest=photoDigest;value.originalServings=4;value.servingsEvidence='Originalrezept für 4 Portionen';value.servingsEvidenceSource='user-addendum';
  Object.assign(value.originalIngredients[0],{amount:300,amountEvidence:'Mehl: 300 g',evidenceSource:'user-addendum'});
  return {value,evidence:{photoDigest,userMessages}};
};
test('explicit same-photo user addenda bind quantities and portions without changing image transcription',()=>{
  const {value,evidence}=addendum(),checked=validateRecipePhoto(value,evidence);
  assert.ok(checked.sourceRecipe);assert.equal(checked.sourceRecipe.originalIngredients[0].amount,300);
  assert.equal(checked.sourceRecipe.originalIngredients[0].evidenceSource,'user-addendum');
  assert.equal(checked.sourceRecipe.transcription,source().transcription);assert.equal(checked.sourceRecipe.transcription.includes('300 g'),false);
});
test('unbound, invented and assistant-supplied addendum quotes cannot establish ingredient evidence',()=>{
  for(const mutate of [ctx=>ctx.photoDigest='f'.repeat(64),ctx=>ctx.userMessages=['Nachtrag zum selben Rezeptfoto: Originalrezept für 4 Portionen; Mehl: 250 g.'],
    ctx=>ctx.userMessages=['Der Assistent glaubt es sind 300 g Mehl.'],ctx=>ctx.userMessages=['Nachtrag zum selben Rezeptfoto: 300 g Mehl.', 'Alte Unterhaltung: Mehl: 300 g']]){
    const {value,evidence}=addendum();mutate(evidence);assert.equal(validateRecipePhoto(value,evidence).sourceRecipe,null);
  }
  assert.equal(validateRecipePhoto(addendum().value).sourceRecipe,null);
  assert.equal(photoAddendumEvidence('f'.repeat(64),['Nachtrag zum selben Rezeptfoto: ']),null);
});
test('photo clarification can finish on the same attached image with an explicit user answer',async()=>{
  const {value}=addendum(),run=setup({...modelAnswer(),recipePhoto:value});
  const body={...request(),message:'Nachtrag zum selben Rezeptfoto: Originalrezept für 4 Portionen; Mehl: 300 g.'};
  const result=await run.assistant.message('alice',body);assert.equal(run.calls(),1);assert.deepEqual(result.pantryItems,[]);
  assert.equal(result.recipePhotoStatus,'prepared');assert.match(result.message,/Nutzernachtrag/);
  const passed=run.seen().body;assert.equal(passed.sourceRecipe.photoDigest,hash(Buffer.from(body.photo.data,'base64')));
  assert.deepEqual(passed.sourceRecipeEvidence.userMessages,[body.message]);assert.equal(passed.photo,undefined);
});
test('the backend never accepts a model addendum whose quantity is absent from the current user reply',async()=>{
  const {value}=addendum(),run=setup({...modelAnswer(),recipePhoto:value});
  const result=await run.assistant.message('alice',{...request(),message:'Nachtrag zum selben Rezeptfoto: Originalrezept für 4 Portionen.'});
  assert.equal(run.calls(),0);assert.deepEqual(result.pantryItems,[]);assert.equal(result.recipePhotoStatus,'needs-clarification');
  assert.match(result.message,/Mehl/);
});
