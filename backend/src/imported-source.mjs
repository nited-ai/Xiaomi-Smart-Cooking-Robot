import {recipe,requireValue} from './domain.mjs';
export const PLACEHOLDER='https://nutrisnap-17e83-kitchen.web.app/assets/recipe-placeholder.svg';
const amount=value=>{const parts=value.replace(',','.').split('/').map(Number);return parts.length===2?parts[0]/parts[1]:parts[0];};
export function ingredientLine(line,index){
  const matched=/^\s*(\d+(?:[.,]\d+)?(?:\s*\/\s*\d+)?)\s*(kg|ml|g|l|EL|TL|Stück|Stk\.?|Packung(?:en)?|Prise(?:n)?|Dose(?:n)?|Bund|Zehe(?:n)?)?\s+(.+)$/i.exec(line);
  if(!matched)return {id:`ingredient-${index}`,name:line.trim().slice(0,160),amount:null,unit:''};
  const number=amount(matched[1].replace(/\s/g,'')),unit=matched[2]??'Stück';requireValue(Number.isFinite(number)&&number>0&&number<=1000000,'Ungültige Zutatenmenge');
  return {id:`ingredient-${index}`,name:matched[3].trim().slice(0,160),amount:number,unit};
}
/** Already extracted recipe text. No URL fetching, browser credentials or attachments. */
export function importedSource(value){
  const input=recipe(value);requireValue(input.ingredients.length>0&&input.steps.length>0,'Zutaten und Zubereitung fehlen');requireValue(input.servings>=1&&input.servings<=20,'Die ursprüngliche Portionszahl fehlt');
  let url=null;try{const source=new URL(input.source);if(source.protocol==='https:'&&!source.username&&!source.password){source.search='';source.hash='';url=source.href;}}catch{}
  return {id:input.id,title:input.title,portion:input.servings,ingredients:input.ingredients.map(ingredientLine),guide:{steps:input.steps.map((instruction,index)=>({title:`Schritt ${index+1}`,instruction}))},coverUrl:PLACEHOLDER,sourceUrl:url,private:true};
}
