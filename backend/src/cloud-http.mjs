import {resolveControlProfile} from './control-profile.mjs';
import {readFile} from 'node:fs/promises';
import {ApiError,requireValue,hash} from './domain.mjs';

/** Dedicated appliance endpoint; existing Kalomatik functions and hosting remain untouched. */
export function kitchenHandler({firebase,origin,providers,broker,assistant,machineRecipes,recipeCreation,dictation,pantryInventory,appCheck,appId,db}){
  const assets=new Map([['/auth/start',['auth.html','text/html; charset=utf-8']],['/auth/complete',['auth.html','text/html; charset=utf-8']],['/assets/auth.js',['auth.js','text/javascript; charset=utf-8']],['/assets/auth.css',['auth.css','text/css; charset=utf-8']],['/assets/recipe-placeholder.svg',['recipe-placeholder.svg','image/svg+xml']]]);
  const requests=new Map();let active=0,activePhotoBatch=0;
  return async(req,res)=>{
    res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('Cross-Origin-Opener-Policy','same-origin-allow-popups');
    res.setHeader('Content-Security-Policy',"default-src 'none'; script-src 'self' https://apis.google.com https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; style-src 'self'; connect-src 'self' https://*.googleapis.com https://*.firebaseapp.com https://*.firebaseio.com; frame-src https://*.firebaseapp.com https://*.web.app https://accounts.google.com https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
    const send=(code,data)=>{res.status(code).type('application/json').send(JSON.stringify(data));};
    let counted=false,countedPhotoBatch=false;
    try{
      const minute=Math.floor(Date.now()/60000),ip=hash(req.ip??req.socket?.remoteAddress??'unknown');if(requests.size>1000)requests.clear();const rate=requests.get(ip);if(!rate||rate.minute!==minute)requests.set(ip,{minute,count:1});else requireValue(++rate.count<=90,'Zu viele Anfragen',429);
      const path=new URL(req.url,origin).pathname;
      if(req.method==='GET'&&path==='/v1/config'){send(200,{firebase,origin,providers});return;}
      if(req.method==='GET'&&assets.has(path)){const [file,type]=assets.get(path);res.status(200).type(type).send(await readFile(new URL(`../public/${file}`,import.meta.url)));return;}
      if(req.method==='GET'&&path==='/v1/recipes/machine-catalog'){send(200,await machineRecipes.catalog(resolveControlProfile(new URL(req.url,origin).searchParams.get('controlProfileId')??undefined)));return;}
      if(req.method==='GET'&&path==='/health'){send(200,{service:'kalomatik-kitchen',schemaVersion:1});return;}
      requireValue(req.method==='POST','Nicht gefunden',404);requireValue(!req.headers.origin||req.headers.origin===origin,'Fremde Origin',403);
      requireValue((req.headers['content-type']??'').split(';')[0]==='application/json','JSON erforderlich',415);requireValue((req.rawBody?.length??Buffer.byteLength(JSON.stringify(req.body??{})))<=(path==='/v1/pantry/analyze'?17000000:1000000),'Anfrage zu groß',413);
      requireValue(active<6,'Der Dienst ist ausgelastet',429);active++;counted=true;
      if(path==='/v1/pantry/analyze'){requireValue(activePhotoBatch<1,'Eine Fotoaufnahme wird gerade geprüft',429);activePhotoBatch++;countedPhotoBatch=true;}
      const body=req.body,token=/^Bearer (\S+)$/.exec(req.headers.authorization??'')?.[1];let result;
      if(path==='/v1/auth/device/start'){
        // An unauthenticated device creates no AI request. Bound durable quota prevents registry flooding.
        const key=String(Math.floor(Date.now()/3600000));await db.runTransaction(async tx=>{const ref=db.collection('kitchenosAuthQuotaV1').doc(key),doc=await tx.get(ref);const used=doc.exists?doc.data().used:0;requireValue(used<100,'Bitte später erneut anmelden',429);tx.set(ref,{used:used+1});});result=await broker.start(body);
      }else if(path==='/v1/auth/device/approve')result=await broker.approve(token,body);
      else if(path==='/v1/auth/device/poll')result=await broker.poll(body);
      else if(path==='/v1/auth/exchange')result=await broker.exchange(body);
      else if(path==='/v1/device/challenge')result=await broker.challenge(token,body);
      else if(path==='/v1/device/attest')result=await broker.attest(token,body);
      else if(path==='/v1/pantry/analyze'||path==='/v1/pantry/suggest'){
        let attested;try{attested=await appCheck.verifyToken(req.headers['x-firebase-appcheck']??'');}catch{throw new ApiError(401,'Geräteanmeldung erforderlich');}
        requireValue(attested.appId===appId,'Gerät nicht freigegeben',403);
        requireValue(pantryInventory,'Vorratsanalyse ist noch nicht eingerichtet',503);
        result=path==='/v1/pantry/analyze'?await pantryInventory.analyze(token,body):await pantryInventory.suggest(token,body);
      }
      else if(path==='/v1/assistant/status'||path==='/v1/assistant/transcribe'||path==='/v1/assistant/message'||path==='/v1/recipes/prepare'||path==='/v1/recipes/review'||path==='/v1/recipes/private'||path==='/v1/recipes/create'){
        let attested;try{attested=await appCheck.verifyToken(req.headers['x-firebase-appcheck']??'');}catch{throw new ApiError(401,'Geräteanmeldung erforderlich');}
        requireValue(attested.appId===appId,'Gerät nicht freigegeben',403);result=path==='/v1/assistant/status'?await assistant.status(token):path==='/v1/assistant/transcribe'?await dictation.transcribe(token,body):path==='/v1/recipes/create'?await recipeCreation.create(token,body):path==='/v1/recipes/private'?await machineRecipes.privateCatalog(token,body):path==='/v1/recipes/prepare'?await machineRecipes.prepare(token,body):path==='/v1/recipes/review'?await machineRecipes.review(token,body):await assistant.message(token,body);
      }else throw new ApiError(404,'Nicht gefunden');send(200,result);
    }catch(error){send(error instanceof ApiError?error.status:503,{error:error instanceof ApiError?error.message:'Dienst vorübergehend nicht verfügbar',code:error instanceof ApiError?error.code:'unavailable'});}
    finally{if(counted)active--;if(countedPhotoBatch)activePhotoBatch--;}
  };
}
