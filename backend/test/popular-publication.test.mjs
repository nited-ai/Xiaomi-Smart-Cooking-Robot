import {test} from 'node:test';
import assert from 'node:assert/strict';
import {checkedAssets,publishPopularEntry} from '../src/popular-publication.mjs';
import {preparePopularEntry,finalizePopularEntry} from '../src/popular-catalog.mjs';
import {popular50Recipes} from '../data/popular-50-recipes.mjs';
import {FakeFirestore} from './fake-firestore.mjs';

test('changed and unreviewed images prevent publication',()=>{
 const entry={id:'recipe',slug:'test',record:{title:'Test'}},job={key:'cover',recipeId:'recipe',kind:'cover',id:'asset',path:'a.jpg'},asset={...job,sha256:'current'};
 assert.throws(()=>checkedAssets(entry,[job],[asset],{}),/geprüft/);
 assert.throws(()=>checkedAssets(entry,[job],[asset],{cover:{sha256:'old',status:'approved'}}),/geprüft/);
 assert.throws(()=>checkedAssets(entry,[job],[{...asset,id:'old'}],{cover:{sha256:'current',status:'approved'}}),/geprüft/);
 assert.equal(checkedAssets(entry,[job],[asset],{cover:{sha256:'current',status:'approved',hardwareChecked:true}}).length,1);
});
test('future kneading plans go into drafts with no runnable index or machine program',async()=>{
 const e=await preparePopularEntry(popular50Recipes.find(r=>r.slug==='pizzateig'),1);await finalizePopularEntry(e);
 const db=new FakeFirestore();const result=await publishPopularEntry(db,e,[],2);
 assert.equal(result.status,'awaiting-function-bindings');assert.equal(db.docs.size,1);
 const draft=db.docs.get('calomaticRecipeDrafts/'+e.id);
 assert.equal(draft.publicationStatus,'draft');assert.ok(draft.machinePlan.steps.some(s=>s.kind==='function'&&s.function.id==='dough'));
 assert.equal((await publishPopularEntry(db,e,[],3)).created,false);
});
test('immutable image states cannot be replaced with a different image hash',async()=>{
 const e=await preparePopularEntry(popular50Recipes[0],1),db=new FakeFirestore();
 const a={kind:'cover',id:'x',path:'cover.jpg',sha256:'old',library:{id:'library-x',sha256:'old',status:'approved',searchKeywords:[],searchTokens:[],recipeContext:e.record.title}};
 await publishPopularEntry(db,e,[a],2);
 await assert.rejects(()=>publishPopularEntry(db,e,[{...a,sha256:'new'}],3),/Bildzustand/);
 assert.equal(db.docs.get('dishImages/x').sha256,'old');
});
