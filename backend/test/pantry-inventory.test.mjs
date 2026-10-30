import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {pantryPhotos,pantryDraft,pantrySuggestionInput,pantrySuggestions,PantryInventory,GeminiPantryModel} from '../src/pantry-inventory.mjs';
import {FirebaseStore} from '../src/firebase-store.mjs';
import {FakeFirestore} from './fake-firestore.mjs';
import {kitchenHandler} from '../src/cloud-http.mjs';
const photo=(id='photo-1',extra=1)=>({id,mimeType:'image/jpeg',data:Buffer.from([255,216,extra,255,217]).toString('base64')});
const request=()=>({requestId:randomUUID(),consent:true,photos:[photo()]});
const item=()=>({id:'lot-1',name:'Gurken',category:'Gemüse',photoIds:['photo-1'],identity:'recognized',count:{min:2,max:2},packageQuantity:null,remaining:{basis:'estimated',min:600,max:1000,unit:'g'},evidence:'Zwei getrennte Gurken sichtbar.',question:null});
const answer=()=>({items:[item()],coverage:'Verdeckte Gegenstände nicht erkennbar.'});

test('exact duplicate images are analyzed once; MIME, consent, IDs and batch limits are enforced',()=>{
  const body=request();body.photos.push(photo('photo-2'));assert.equal(pantryPhotos(body).photos.length,1);
  for(const change of [b=>b.consent=false,b=>b.photos=[],b=>b.photos=Array.from({length:9},(_,i)=>photo('p'+i)),b=>b.photos.push(photo()),b=>b.photos[0].mimeType='image/png',b=>b.photos[0].data='not a photo']){const b=request();change(b);assert.throws(()=>pantryPhotos(b));}
});
test('all distinct photos reach one draft; unknown containers and uncertain identities are retained',()=>{
  const body=request();body.photos.push(photo('photo-2',2));const input=pantryPhotos(body),value=answer();value.items.push({...item(),id:'lot-2',identity:'unknown',name:'Weiße Dose',photoIds:['photo-2']});
  const result=pantryDraft({...value,command:'START',publish:true},input);assert.equal(result.status,'review-required');assert.equal(result.items.length,2);assert.equal(result.items[1].remaining,null);assert.equal(result.command,undefined);assert.equal(result.photos[0].data,undefined);assert.equal(result.items[0].reviewed,false);
});
test('package label never silently becomes remaining quantity; separate range is preserved',()=>{
  const value=answer();value.items[0]={...item(),packageQuantity:{amount:200,unit:'g',label:'200 g'},remaining:null};assert.equal(pantryDraft(value,pantryPhotos(request())).items[0].remaining,null);
  value.items[0].remaining={basis:'label',min:200,max:200,unit:'g'};assert.throws(()=>pantryDraft(value,pantryPhotos(request())));
  value.items[0].remaining={basis:'label',min:400,max:400,unit:'g'};assert.equal(pantryDraft(value,pantryPhotos(request())).items[0].remaining.min,400);
});
test('invalid lot identities, evidence references and impossible ranges fail as a whole',()=>{
  for(const alter of [v=>v.items.push(item()),v=>v.items[0].photoIds=['nonexistent'],v=>v.items[0].count={min:2,max:1},v=>v.items[0].count={min:1.2,max:2},v=>v.items[0].remaining={basis:'counted',min:2,max:2,unit:'g'},v=>v.items[0].remaining.min=-1]){const v=answer();alter(v);assert.throws(()=>pantryDraft(v,pantryPhotos(request())));}
});
test('batch result replay is private and idempotent; pixels are never persisted',async()=>{
  const db=new FakeFirestore(),store=new FirebaseStore(db);let calls=0;
  const api=new PantryInventory({service:{user:async token=>{assert.ok(token);return {uid:token};}},store,model:{complete:async(system,context,photos)=>{calls++;assert.equal(photos.length,1);return answer();}}});
  const body=request();await assert.rejects(api.analyze(null,body));const result=await api.analyze('alice',body);assert.deepEqual(await api.analyze('alice',body),result);assert.equal(calls,1);await api.analyze('bob',body);assert.equal(calls,2);
  assert.equal(JSON.stringify([...db.docs]).includes(body.photos[0].data),false);await assert.rejects(api.analyze('alice',{...body,photos:[photo('other',3)]}),e=>e.status===409);
});
test('unconfigured Gemini does not consume quota or substitute another provider',async()=>{
  const db=new FakeFirestore(),api=new PantryInventory({service:{user:async()=>({uid:'a'})},store:new FirebaseStore(db),model:null});await assert.rejects(api.analyze('a',request()),e=>e.status===503);assert.equal(db.docs.size,0);
});
test('dish ideas use only recognized stock; uncertain quantities always prompt a check',()=>{
  const input=pantrySuggestionInput({requestId:randomUUID(),consent:true,servings:2,items:[item(),{...item(),id:'lot-2',identity:'uncertain'}]});
  const value={suggestions:[{id:'dish-1',title:'Gurkensalat',minutes:10,uses:['lot-1'],missing:['Salz'],check:[],reason:'Verwendet Gurken'}],createRecipe:true};
  const result=pantrySuggestions(value,input);assert.deepEqual(result.suggestions[0].check,['Menge prüfen: Gurken']);assert.equal(result.createRecipe,undefined);assert.equal(result.status,'choose-recipe');
  value.suggestions[0].uses=['lot-2'];assert.throws(()=>pantrySuggestions(value,input));value.suggestions[0].uses=['invented'];assert.throws(()=>pantrySuggestions(value,input));
});
test('Gemini receives all labeled photos and omits thought parts; no images in context JSON',async()=>{
  let sent;const model=new GeminiPantryModel({apiKey:'test',fetchImpl:async(url,options)=>{assert.match(url,/gemini-3\.8-flash:generateContent$/);sent=JSON.parse(options.body);assert.equal(options.headers['x-goog-api-key'],'test');return new Response(JSON.stringify({candidates:[{finishReason:'STOP',content:{parts:[{thought:true,text:'private'},{text:JSON.stringify(answer())}]}}]}));}});
  assert.deepEqual(await model.complete('system',{photos:[{id:'photo-1'},{id:'photo-2'}]},[photo(),photo('photo-2',2)]),answer());assert.equal(sent.contents[0].parts.filter(p=>p.inlineData).length,2);assert.equal(sent.contents[0].parts[0].text.includes('base64'),false);
});
test('empty stocks cannot feed a dish; label ranges require quantity checks',()=>{
  const input=pantrySuggestionInput({requestId:randomUUID(),consent:true,servings:2,items:[{...item(),remaining:{basis:'user',min:0,max:0,unit:'g'}}]});
  const response={suggestions:[{id:'a',title:'Salat',minutes:5,uses:['lot-1'],missing:[],check:[],reason:'Gurken verwenden'}]};
  assert.throws(()=>pantrySuggestions(response,input));input.items[0].remaining={basis:'label',min:200,max:400,unit:'g'};
  assert.ok(pantrySuggestions(response,input).suggestions[0].check.includes('Menge prüfen: Gurken'));
});
test('two closed egg boxes never validate as two counted eggs',()=>{
  const value=answer();value.items[0]={...item(),name:'Eier',countKind:'packages',remaining:{basis:'counted',min:2,max:2,unit:'Stück'}};
  assert.throws(()=>pantryDraft(value,pantryPhotos(request())));
  value.items[0].remaining.unit='Packung';assert.equal(pantryDraft(value,pantryPhotos(request())).items[0].countKind,'packages');
});
test('Gemini provider errors and truncated responses never become successful scans',async()=>{
  for(const [response,status] of [[new Response('{}',{status:403}),503],[new Response('{}',{status:429}),429],[new Response(JSON.stringify({candidates:[{finishReason:'MAX_TOKENS'}]})),502],[new Response('broken'),502]]){
    const model=new GeminiPantryModel({apiKey:'test',fetchImpl:async()=>response});await assert.rejects(model.complete('s',{},[]),e=>e.status===status);
  }
});
test('pantry HTTP routes require App Check and pass bearer identity; legacy size limit stays',async()=>{
  let calls=0;const handler=kitchenHandler({origin:'https://kitchen.example',appId:'app',pantryInventory:{analyze:async(token)=>{assert.equal(token,'owner');calls++;return {status:'review-required'};},suggest:async()=>{calls++;return {status:'choose-recipe'};}},appCheck:{verifyToken:async token=>({appId:token})}});
  async function send(path,attestation='app',size=2){const out={};const res={setHeader(){},status(s){out.status=s;return this;},type(){return this;},send(body){out.body=JSON.parse(body);}};await handler({url:path,method:'POST',ip:'test',headers:{'content-type':'application/json',authorization:'Bearer owner','x-firebase-appcheck':attestation},rawBody:{length:size},body:{}},res);return out;}
  assert.equal((await send('/v1/pantry/analyze','wrong')).status,403);assert.equal(calls,0);
  assert.equal((await send('/v1/pantry/analyze','app',2000000)).status,200);assert.equal((await send('/v1/pantry/suggest')).status,200);
  assert.equal((await send('/v1/assistant/message','app',2000000)).status,413);assert.equal((await send('/v1/pantry/analyze','app',17000001)).status,413);assert.equal(calls,2);
});
