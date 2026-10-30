import {test} from 'node:test';
import assert from 'node:assert/strict';
import {checkRecipePlausibility,RECIPE_GUIDANCE} from '../src/recipe-guidance.mjs';
import {starterRecipes} from '../data/starter-recipes.mjs';
import {prepareCatalogEntry} from '../src/catalog-import.mjs';

const source={title:'Schokoladen-Brownies',ingredients:[{id:'egg',name:'Eier'},{id:'chocolate',name:'Schokolade'}]};
const melt={kind:'machine',title:'Schokolade schmelzen',instruction:'Schokolade schmelzen.',settings:{temperatureC:50,speedLevel:1,direction:'counterclockwise'}};
const eggs={kind:'prepare',title:'Eier zugeben',instruction:'Eier in den Topf geben.',ingredientUses:[{ingredientId:'egg',share:1}]};
test('backup rejects egg addition after hot chocolate without a cooling condition',()=>{
  assert.throws(()=>checkRecipePlausibility(source,{steps:[melt,eggs]}),/Abkühlen/);
  assert.doesNotThrow(()=>checkRecipePlausibility(source,{steps:[melt,{kind:'rest',title:'Abkühlen',instruction:'Bis höchstens handwarm abkühlen lassen.'},eggs]}));
  assert.doesNotThrow(()=>checkRecipePlausibility({title:'Crêpes',ingredients:source.ingredients},{steps:[eggs]}));
});
test('backup catches a cutting direction for explicitly gentle folding and excessive whisk speed',()=>{
  assert.throws(()=>checkRecipePlausibility(source,{steps:[{kind:'machine',instruction:'Sanft verrühren.',settings:{temperatureC:0,speedLevel:2,direction:'clockwise'}}]}),/Rührbewegung/);
  assert.throws(()=>checkRecipePlausibility(source,{steps:[{kind:'machine',instruction:'Mit Rühraufsatz aufschlagen.',settings:{temperatureC:0,speedLevel:6,direction:'clockwise'}}]}),/Rühraufsatz/);
});
test('curated brownies preserve amounts, use specific pieces and stir the egg and flour stages',async()=>{
  const recipe=starterRecipes.find(r=>r.slug==='brownies'),entry=await prepareCatalogEntry(recipe,1);
  assert.match(recipe.steps.find(s=>s.title==='Schokolade vorbereiten').instruction,/2–3 cm/);
  for(const title of ['Eier einarbeiten','Teig kurz verrühren'])assert.equal(entry.variant.program.steps.find(s=>s.title===title).settings.direction,'counterclockwise');
  assert.equal(entry.variant.provenance.cookingTested,false);
  assert.match(RECIPE_GUIDANCE,/Plausibilitätsprüfung/);
  assert.match(RECIPE_GUIDANCE,/Rühraufsatz.*eigenes Zubehör/);
});
