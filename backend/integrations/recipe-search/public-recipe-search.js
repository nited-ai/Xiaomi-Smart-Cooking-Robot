// Shared vocabulary and ranking; called only with projected public catalogue rows.
import {normalize,prepareQuery,indexRecipe,cachedIndex,scoreRecipe} from './food-search.mjs';
export {SEARCH_VERSION} from './food-search.mjs';
const invalid=()=>Object.assign(Error('CATALOG_INVALID_SEARCH'),{code:'CATALOG_INVALID_SEARCH',status:400});
export function parsePublicRecipeSearch(value){
 if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).some(key=>!['query','include','exclude','limit'].includes(key)))throw invalid();
 const validText=(text,max)=>typeof text==='string'&&text.length<=max&&!/[\x00-\x1f\x7f]/.test(text);
 if(!validText(value.query,240))throw invalid();
 for(const [key,max]of [['include',12],['exclude',20]])if(Object.hasOwn(value,key)&&(!Array.isArray(value[key])||value[key].length>max||value[key].some(term=>!validText(term,100)||!normalize(term))))throw invalid();
 if(Object.hasOwn(value,'limit')&&(!Number.isInteger(value.limit)||value.limit<1||value.limit>24))throw invalid();
 return {query:value.query,include:value.include||[],exclude:value.exclude||[],limit:value.limit??24};
}

/** Explicit ingredient constraints never use typo correction or short prefixes. */
export function ingredientMatches(name,value){
 const query=typeof value==='string'?prepareQuery(value,{fuzzy:false}):value;
 const words=indexRecipe('',[name]).words.map(w=>w.text);
 return query.valid&&query.terms.length>0&&query.terms.every(({word,alternatives})=>[word,...alternatives].some(term=>words.some(w=>w===term||(term.length>=4&&w.startsWith(term)))));
}

export function searchPublicRecipes(rows,input){
 const query=prepareQuery(input.query),include=(input.include??[]).map(v=>prepareQuery(v,{fuzzy:false})),exclude=(input.exclude??[]).map(v=>prepareQuery(v,{fuzzy:false})),distinct=new Map();
 const preferred=(a,b)=>(Number(b.row.guideStatus==='ready')-Number(a.row.guideStatus==='ready'))||(b.row.updatedAt||0)-(a.row.updatedAt||0)||a.row.id.localeCompare(b.row.id);
 for(const row of rows){
  if(!include.every(q=>row.ingredients.some(i=>ingredientMatches(i.name,q)))||exclude.some(q=>row.ingredients.some(i=>ingredientMatches(i.name,q))))continue;
  const score=scoreRecipe(cachedIndex(row),query);if(score<0)continue;
  const entry={row,score},key=row.foodIdentity??row.id,previous=distinct.get(key);
  if(!previous||preferred(entry,previous)<0)distinct.set(key,entry);
 }
 return [...distinct.values()].sort((a,b)=>a.score-b.score||preferred(a,b)).map(entry=>entry.row);
}

/** GET keeps its existing status, explicit sorting, pagination and duplicate policy. */
export function filterPublicRecipes(rows,input){
 const query=prepareQuery(input.q),include=input.ingredient?prepareQuery(input.ingredient,{fuzzy:false}):null,exclude=input.without?prepareQuery(input.without,{fuzzy:false}):null;
 const found=[];
 for(const row of rows){
  if(input.status!=='all'&&row.status!==input.status||include&&!row.ingredients.some(i=>ingredientMatches(i.name,include))||exclude&&row.ingredients.some(i=>ingredientMatches(i.name,exclude)))continue;
  const score=scoreRecipe(cachedIndex(row),query);if(score>=0)found.push({row,score});
 }
 const tie=input.sort==='title'?(a,b)=>a.title.localeCompare(b.title,'de')||a.id.localeCompare(b.id):(a,b)=>(b.updatedAt||0)-(a.updatedAt||0)||a.id.localeCompare(b.id);
 found.sort((a,b)=>(input.sort==='title'?0:a.score-b.score)||tie(a.row,b.row));
 return found.map(x=>x.row);
}
