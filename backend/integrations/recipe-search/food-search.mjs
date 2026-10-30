import {readFileSync} from 'node:fs';
export const dictionary=JSON.parse(readFileSync(new URL('./food-dictionary.json',import.meta.url),'utf8'));
export const SEARCH_VERSION=dictionary.version;
export const normalize=value=>String(value??'').normalize('NFC').toLowerCase().replaceAll('ä','ae').replaceAll('ö','oe').replaceAll('ü','ue').replaceAll('ß','ss').normalize('NFD').replace(/\p{M}+/gu,'').replaceAll('ae','a').replaceAll('oe','o').replaceAll('ue','u').replace(/[^\p{L}\p{N}]+/gu,' ').trim().replace(/ +/g,' ');
const aliases=new Map(),groups=new Map();
const add=(term,values)=>{term=normalize(term);if(!aliases.has(term))aliases.set(term,new Set());for(const v of values)aliases.get(term).add(normalize(v));};
for(const group of dictionary.groups){groups.set(group.id,group.terms);for(const term of group.terms)add(term,group.terms);}
for(const category of dictionary.categories)for(const term of category.terms)add(term,[...category.terms,...category.members.flatMap(id=>groups.get(id))]);
const foods=[...aliases.keys()],phrases=foods.filter(v=>v.includes(' ')).sort((a,b)=>b.length-a.length),fillers=new Set(dictionary.queryFillers.map(normalize));

export function oneEdit(a,b){
 if(Math.abs(a.length-b.length)>1)return false;
 let i=0;while(i<Math.min(a.length,b.length)&&a[i]===b[i])i++;
 if(i===Math.min(a.length,b.length))return true;
 if(a.length===b.length)return a.slice(i+1)===b.slice(i+1)||(i+1<a.length&&a[i]===b[i+1]&&a[i+1]===b[i]&&a.slice(i+2)===b.slice(i+2));
 return a.length>b.length?a.slice(i+1)===b.slice(i):a.slice(i)===b.slice(i+1);
}

export function prepareQuery(value,{fuzzy=true,maxLength=240}={}){
 const phrase=normalize(value),tokens=phrase?phrase.split(' '):[],terms=[];
 for(let i=0;i<tokens.length;){
  const rest=tokens.slice(i).join(' '),multi=phrases.find(p=>rest===p||rest.startsWith(p+' '));
  const word=multi??tokens[i];i+=word.split(' ').length;
  if(tokens.length>1&&fillers.has(word))continue;
  const alternatives=aliases.get(word)??new Set(),corrections=new Set();
  // Correct an unknown food spelling before expanding its synonyms.
  if(fuzzy&&!aliases.has(word)&&word.length>=5&&!word.includes(' '))for(const food of foods)if(!food.includes(' ')&&oneEdit(word,food))for(const alias of aliases.get(food))corrections.add(alias);
  terms.push({word,alternatives,corrections});
 }
 return {phrase,terms,fuzzy,valid:phrase.length<=maxLength&&tokens.length<=32};
}

export function indexRecipe(title,ingredients=[]){
 const normalized=normalize(title),words=new Map();
 const add=(text,inTitle)=>{
  text=normalize(text);if(!text)return;
  const insert=word=>{if(!words.has(word)||inTitle)words.set(word,{text:word,title:inTitle});};
  for(const word of text.split(' ')){
   insert(word);
   if(!aliases.has(word)){
    const parts=foods.filter(food=>!food.includes(' ')&&food.length>=4&&word.length>food.length&&(word.startsWith(food)||word.indexOf(food)>=3));
    for(const food of parts)if(!parts.some(longer=>longer.length>food.length&&longer.includes(food)))insert(food);
   }
  }
  for(const phrase of phrases)if((' '+text+' ').includes(' '+phrase+' '))insert(phrase);
 };
 add(normalized,true);for(const ingredient of ingredients)add(ingredient,false);
 return {title:normalized,words:[...words.values()]};
}

function lexical(query,word){return word===query?0:aliases.has(query)?-1:word.startsWith(query)?1:query.length>=4&&word.includes(query)?2:-1;}
export function scoreRecipe(index,query){
 if(!query.valid)return -1;if(!query.phrase)return 0;if(!query.terms.length)return -1;
 let worst=0,relevance=0;
 for(const {word,alternatives,corrections} of query.terms){
  let best=Infinity;
  for(const candidate of index.words){
   const direct=lexical(word,candidate.text),field=candidate.title?0:10;
   if(direct>=0)best=Math.min(best,direct+field);
   for(const alternative of alternatives)if(lexical(alternative,candidate.text)>=0)best=Math.min(best,100+field);
   if(query.fuzzy){
    if(word.length>=4&&candidate.text.length>=4&&oneEdit(word,candidate.text))best=Math.min(best,200+field);
    for(const correction of corrections)if(lexical(correction,candidate.text)>=0)best=Math.min(best,200+field);
   }
  }
  if(!Number.isFinite(best))return -1;worst=Math.max(worst,Math.floor(best/100));relevance+=best%100;
 }
 const phrase=index.title===query.phrase?0:index.title.startsWith(query.phrase)?1:2;
 return worst*10000+phrase*1000+relevance;
}

// Cached only for the lifetime of the sanitized server snapshot; no private records.
const indexes=new WeakMap();
export function cachedIndex(row){if(!indexes.has(row))indexes.set(row,indexRecipe(row.title,row.ingredients.map(i=>i.name)));return indexes.get(row);}
export function rankRecipes(rows,query){const prepared=prepareQuery(query);return rows.map(row=>({row,score:scoreRecipe(cachedIndex(row),prepared)})).filter(x=>x.score>=0).sort((a,b)=>a.score-b.score).map(x=>x.row);}
