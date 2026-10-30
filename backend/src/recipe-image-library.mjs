import {createHash} from 'node:crypto';
const hash=value=>createHash('sha256').update(value).digest('hex');
export const IMAGE_LIBRARY='calomaticRecipeImageLibrary';
export const IMAGE_USES='calomaticRecipeImageUses';
export const normalized=value=>String(value).toLocaleLowerCase('de-DE').replaceAll('ä','ae').replaceAll('ö','oe').replaceAll('ü','ue').replaceAll('ß','ss').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,' ').trim();
const tokens=value=>[...new Set(normalized(value).split(' ').filter(s=>s.length>1))];
const doughTypes={pizzateig:'weizen-hefeteig-pizza',koernerbrot:'vollkorn-koerner-hefeteig',naan:'weizen-joghurt-hefeteig', 'focaccia-popular':'weicher-olivenoel-hefeteig',zimtschnecken:'suesser-angereicherter-hefeteig',traumstuecke:'butter-muerbeteig',scones:'buttermilch-backpulverteig',bananenbrot:'bananen-ruehrteig',muffins:'vanille-ruehrteig',karottenkuchen:'karotten-ruehrteig',schokokuchen:'kakao-ruehrteig',cookies:'schokoladen-cookie-teig',pfannkuchen:'duenner-pfannkuchenteig',pancakes:'pancake-backpulverteig'};
const labels={peel:'schälen vorbereiten',wash:'waschen abwaschen reinigen',eggs:'Eier Ei aufschlagen Schale Schüssel',prepare_pan:'Backform Kuchenform bereitstellen Backpapier auslegen',proof:'Teig gehen lassen aufgehen Teigruhe',cool:'abkühlen auskühlen Gitter',bake:'backen Ofen Backofen',transfer:'umfüllen herausnehmen',serve:'servieren anrichten portionieren',check:'prüfen Konsistenz Temperatur',prepare:'vorbereiten',finished:'fertiges Gericht Rezeptbild'};
export function describeRecipeImage(job,entry){
 const step=job.kind==='step'?entry.record.guide.steps[job.index]:null;
 const title=step?.title??entry.record.title,instruction=step?.instruction??'';
 const context=title+' '+instruction;
 let action=job.kind==='cover'?'finished':/^Eier (?:vorbereiten|zugeben)$/i.test(title)?'eggs':/Form und Ofen vorbereiten|Form bereitstellen/i.test(title)?'prepare_pan':/gehen lassen|nachgehen lassen/i.test(title)?'proof':/abkühlen|auskühlen/i.test(title)?'cool':/schälen/i.test(context)?'peel':/waschen|abwaschen/i.test(context)?'wash':/backen/i.test(title)?'bake':/umfüllen|herausnehmen/i.test(title)?'transfer':/servieren|portionieren|anrichten/i.test(title)?'serve':/prüfen|prüf|probe/i.test(title)?'check':'prepare';
 let group=null,subjects=[],displayName=title,matchNote='Nur bei gleicher Handlung, Lebensmittelart, Verarbeitung, Gefäß und Form verwenden.';
 if(title==='Zwiebel vorbereiten'&&instruction==='Zwiebel schälen. Das Zerkleinern übernimmt anschließend das Gerät.'){
  group='zwiebel-schaelen-ganz';subjects=['Zwiebel','Zwiebeln'];displayName='Ganze Zwiebel schälen';
 }else if(action==='eggs'&&/einzeln in einer (?:kleinen )?Schale aufschlagen/i.test(instruction)){
  group='ei-einzeln-in-schale-aufschlagen';subjects=['Ei','Eier'];displayName='Ei einzeln in eine Schale aufschlagen';
  matchNote='Neutraler Handgriff in einer separaten Schale. Kein Teig und keine weiteren Zutaten im Bild; Stückzahl wird von der Rezeptanleitung bestimmt.';
 }else if(action==='prepare_pan'&&/runde Springform mit 24 cm/.test(instruction)){
  group='springform-rund-24cm-backpapier-leer';subjects=['Springform','Backpapier'];displayName='Runde 24-cm-Springform mit Backpapier bereitstellen';
 }else if(job.slug==='kartoffelgratin'&&job.index===0){
  group='kartoffeln-waschen-schaelen';subjects=['Kartoffel','Kartoffeln'];displayName='Kartoffeln waschen und schälen';
 }else if(job.slug==='beef-tacos'&&job.index===0){
  group='blattsalat-waschen-abtropfen';subjects=['Salat','Blattsalat','Salatblätter'];displayName='Salatblätter waschen und abtropfen lassen';
 }
 const doughType=doughTypes[job.slug]??null;
 const shape=['koernerbrot','bananenbrot'].includes(job.slug)?'kasten-25cm':['karottenkuchen','schokokuchen'].includes(job.slug)?'rund-24cm':job.slug==='muffins'?'muffins':null;
 if(!group&&action==='proof'&&title==='Teig gehen lassen'&&doughType){group='teig-aufgehen-'+doughType+'-schuessel';subjects=['Teig','Hefeteig'];displayName='Teig geht auf – '+doughType;matchNote='Nur für diese Teigart mit gleicher sichtbarer Struktur und Abdeckung in einer Schüssel; kein Austausch zwischen Pizza-, Körner-, süßem oder weichem Focacciateig.';}
 if(!group&&action==='cool'&&doughType&&shape){group='gebaeck-abkuehlen-'+doughType+'-'+shape;subjects=['Gebäck','Gitter',...(shape==='kasten-25cm'?['Brot']:['Kuchen'])];displayName='Gebäck kühlt auf Gitter ab – '+doughType+' – '+shape;matchNote='Teigart, Farbe, Kruste, Form und sichtbare Zutaten müssen passen; keine andere Brotsorte oder Kuchenform einsetzen.';}
 const vessel=group?.includes('schale')?'separate-schale':group?.includes('springform')?'springform-rund-24cm':group?'ohne-maschinengefaess':'schrittbezogen';
 const semantic={action,subjects,doughType:group?null:doughType,vessel,group,instruction:group?null:instruction,recipeFamily:group?null:job.slug,kind:job.kind};
 const reuseKey=hash(JSON.stringify(semantic));
 const slug=normalized(displayName).replaceAll(' ','-').slice(0,100);
 const foodAliases=[[/zwiebel/i,['Zwiebel','Zwiebeln']],[/möhre|karotte/i,['Möhre','Möhren','Karotte','Karotten']],[/kartoffel/i,['Kartoffel','Kartoffeln']],[/salat/i,['Salat','Blattsalat']],[/champignon|pilz/i,['Champignons','Pilze']],[/erdbeer/i,['Erdbeeren']],[/\bEi(?:er)?\b/i,['Ei','Eier']],[/ingwer/i,['Ingwer']],[/knoblauch/i,['Knoblauch']],[/brot/i,['Brot']],[/teig/i,['Teig']],[/kuchen/i,['Kuchen']]].filter(([pattern])=>pattern.test(context)).flatMap(([,names])=>names);
 const toolKeywords=['Schüssel','Schale','Springform','Kastenform','Muffinblech','Gitter','Backpapier','Auflaufform','Mixtopf','Pfanne','Backofen'].filter(word=>context.toLocaleLowerCase('de-DE').includes(word.toLocaleLowerCase('de-DE')));
 const searchKeywords=[...new Set([displayName,title,labels[action],...subjects,...foodAliases,...toolKeywords,doughType??'',shape??'',entry.record.title,job.slug])];
 return {schemaVersion:1,displayName,name:slug,kind:job.kind,action,subjects,doughType,breadType:shape==='kasten-25cm'?doughType:null,shape,vessel,reuseScope:group?(doughType&&['proof','cool'].includes(action)?'food-specific-action':'generic-action'):'content-specific',reuseGroup:group,reuseKey,matchNote,searchKeywords,searchTokens:tokens(searchKeywords.join(' ')),sourceInstruction:instruction,recipeContext:entry.record.title};
}
export function imageLibraryRecord(asset,descriptor,review,now=Date.now()){
 if(review?.status!=='approved'||review.sha256!==asset.sha256||review.hardwareChecked!==true)throw Error('Bild und Gerätebezug müssen vor Aufnahme in die Bibliothek geprüft sein');
 const id=hash('recipe-image-library-v1:'+asset.sha256),path=`recipe-library/v1/${descriptor.name}/${id}.jpg`;
 const hardwareKeywords=review.hardware==='xiaomi-c3'?['Mixtopf','Topf','Xiaomi C3','dunkle Außenhülle','Bügelgriff']:[];
 return {id,...descriptor,searchKeywords:[...descriptor.searchKeywords,...hardwareKeywords],searchTokens:[...new Set([...descriptor.searchTokens,...tokens(hardwareKeywords.join(' '))])],path,sha256:asset.sha256,mimeType:'image/jpeg',bytes:asset.bytes,width:asset.width,height:asset.height,imageKind:'illustration',generator:'built-in-imagegen',status:'approved',reviewedAt:now,createdAt:now,hardware:review.hardware??'not-visible',hardwareReferenceId:review.hardware==='xiaomi-c3'?'xiaomi-c3-owner-originals-2026-10-21':null,originalReferences:review.originalReferences??[],visualReviewNote:review.note??'',license:{kind:'generated-for-project',sourcePhotos:review.hardware==='xiaomi-c3'?'original-user-hardware-photos':null},cookingTested:false};
}
export function reusableImage(records,descriptor){return records.find(r=>r.status==='approved'&&r.reuseKey===descriptor.reuseKey&&r.kind===descriptor.kind)??null;}
export function searchImageLibrary(records,query){const wanted=tokens(query);return records.map(r=>({record:r,score:wanted.reduce((score,t)=>score+(r.searchTokens?.some(v=>v===t||v.startsWith(t))?1:0),0)})).filter(x=>x.score===wanted.length&&x.record.status==='approved').sort((a,b)=>a.record.displayName.localeCompare(b.record.displayName,'de')).map(x=>x.record);}
