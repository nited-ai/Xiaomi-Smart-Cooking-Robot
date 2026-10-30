import test from 'node:test';
import { strict as assert } from 'node:assert';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { fromKalomatik } from '../src/kalomatik.mjs';

const sandbox=vm.createContext({window:{}});
vm.runInContext(readFileSync(new URL('../integrations/kalomatik219/recipe-skill.js',import.meta.url),'utf8'),sandbox);
const skill=sandbox.window.NutriSnapRecipeSkill;
const meal=()=>({gericht:'Reispfanne',portion:1,cookingPortions:4,ingredients:[{id:'rice',name:'Reis',amount:100,unit:'g'},{id:'tomato',name:'Tomaten',amount:150,unit:'g'}]});
function guide(m){return skill.bind({title:'Reispfanne',prepMinutes:5,cookMinutes:20,restMinutes:0,difficulty:'einfach',equipment:['Topf'],steps:[{title:'Vorbereiten',instruction:'Zutaten vorbereiten.',ingredients:[{ingredientId:'rice',share:.5},{ingredientId:'tomato',share:1}],minutes:5,temperatureC:null,heat:null,cue:'Gleichmäßig geschnitten.',imagePrompt:null},{title:'Garen',instruction:'Restliche Zutaten garen.',ingredients:[{ingredientId:'rice',share:.5}],minutes:20,temperatureC:100,heat:'mittel',cue:'Reis ist gar.',imagePrompt:null}],tips:['Nach Geschmack würzen.']},m);}
test('existing Kalomatik household quantities and step shares enter the same recipe contract',()=>{
  const m=meal(), r=fromKalomatik(m,guide(m));
  assert.equal(r.servings,4);assert.deepEqual(r.ingredients,['400 g Reis','600 g Tomaten']);
  assert.ok(r.steps.some(s=>s.includes('200 g Reis')));assert.ok(r.steps.some(s=>s.includes('600 g Tomaten')));
});
test('stale Kalomatik ingredient basis cannot inherit cooking instructions',()=>{
  const m=meal(), g=guide(m);m.ingredients[0].amount=120;
  assert.throws(()=>fromKalomatik(m,g),/anderen Zutatenstand/);
});
test('unknown ingredient and duplicated use are rejected by the pinned original recipe skill',()=>{
  const m=meal(), g=guide(m);g.steps[0].ingredients[0].ingredientId='invented';
  assert.throws(()=>fromKalomatik(m,g));
  const duplicate=guide(m);duplicate.steps[0].ingredients[0].share=1;
  assert.throws(()=>fromKalomatik(m,duplicate));
});
