import {ApiError,requireValue,isUuid,hash} from './domain.mjs';

/** Bounded PCM dictation. Audio remains request-local; only text is cached per owner/job. */
export function dictationInput(body){
  requireValue(body?.consent===true&&isUuid(body.requestId),'Diktieren wurde nicht bestätigt');
  requireValue(body.audio?.mimeType==='audio/wav'&&typeof body.audio.data==='string'&&body.audio.data.length<=640064&&/^[A-Za-z0-9+/]+={0,2}$/.test(body.audio.data),'Ungültige Aufnahme');
  const bytes=Buffer.from(body.audio.data,'base64');
  requireValue(bytes.length>=16044&&bytes.length<=480044&&bytes.toString('ascii',0,4)==='RIFF'&&bytes.toString('ascii',8,12)==='WAVE'&&bytes.toString('ascii',12,16)==='fmt '&&bytes.toString('ascii',36,40)==='data','Ungültige WAV-Aufnahme');
  requireValue(bytes.readUInt32LE(4)===bytes.length-8&&bytes.readUInt32LE(16)===16&&bytes.readUInt16LE(20)===1&&bytes.readUInt16LE(22)===1&&bytes.readUInt32LE(24)===16000&&bytes.readUInt32LE(28)===32000&&bytes.readUInt16LE(32)===2&&bytes.readUInt16LE(34)===16&&bytes.readUInt32LE(40)===bytes.length-44&&(bytes.length-44)%2===0,'Erwartet wird eine kurze Mono-Aufnahme');
  return bytes;
}
export class KitchenDictation {
  constructor({service,store,model,now=Date.now}){Object.assign(this,{service,store,model,now});}
  async transcribe(token,body){
    const user=await this.service.user(token),bytes=dictationInput(body),fingerprint=hash(Buffer.concat([Buffer.from('dictation-v1:'),bytes]));
    const claim=await this.store.claimJob(user.uid,body.requestId,fingerprint,this.now());if(claim.result)return claim.result;
    requireValue(claim.owner,'Das Diktat wird noch verarbeitet.',409);
    try{const result=await this.model.transcribe(bytes);await this.store.finishAssistantJob(user.uid,body.requestId,fingerprint,result,this.now());return result;}
    catch(error){await this.store.failJob(user.uid,body.requestId,fingerprint);throw error;}
  }
}
export class GroqDictation {
  constructor({apiKey,fetchImpl=fetch}){Object.assign(this,{apiKey,fetchImpl});}
  async transcribe(bytes){
    const form=new FormData();form.append('file',new Blob([bytes],{type:'audio/wav'}),'dictation.wav');form.append('model','whisper-large-v3-turbo');form.append('language','de');form.append('response_format','verbose_json');form.append('temperature','0');
    const response=await this.fetchImpl('https://api.groq.com/openai/v1/audio/transcriptions',{method:'POST',headers:{Authorization:`Bearer ${this.apiKey}`},body:form,signal:AbortSignal.timeout(35000)});
    if(!response.ok)throw new ApiError(response.status===429?429:502,'Diktieren ist gerade nicht verfügbar');
    const raw=await response.text();requireValue(raw.length<=65536,'Diktierantwort zu groß',502);let result;try{result=JSON.parse(raw);}catch{throw new ApiError(502,'Diktierantwort nicht lesbar');}
    requireValue(typeof result.text==='string'&&result.text.length<=2000&&!result.text.includes('\0'),'Diktierantwort nicht lesbar',502);
    const spoken=Array.isArray(result.segments)&&result.segments.some(s=>typeof s.no_speech_prob==='number'&&s.no_speech_prob<.75&&typeof s.avg_logprob==='number'&&s.avg_logprob> -1.2);
    return {text:spoken?result.text.trim():'',noSpeech:!spoken};
  }
}
