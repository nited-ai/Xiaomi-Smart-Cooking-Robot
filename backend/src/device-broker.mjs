import { createPublicKey, verify } from 'node:crypto';
import { ApiError, requireValue, opaque, hash, challenge, equal, isOpaque } from './domain.mjs';
import { Timestamp } from 'firebase-admin/firestore';

const collection='kitchenosDeviceAuthV2';
export function deviceKey(encoded){
  requireValue(typeof encoded==='string'&&encoded.length<=256&&/^[A-Za-z0-9+/]+={0,2}$/.test(encoded),'Ungültiger Geräteschlüssel');
  try{const der=Buffer.from(encoded,'base64'),key=createPublicKey({key:der,format:'der',type:'spki'});requireValue(key.asymmetricKeyType==='ec'&&key.asymmetricKeyDetails?.namedCurve==='prime256v1');return {key,id:hash(der),encoded:der.toString('base64')};}catch{throw new ApiError(422,'Ungültiger Geräteschlüssel');}
}
function proof(key,data,signature){try{return typeof signature==='string'&&signature.length<=160&&verify('sha256',Buffer.from(data),key,Buffer.from(signature,'base64'));}catch{return false;}}
export class DeviceBroker {
  constructor({db,service,auth,appCheck,appId,apiKey,now=Date.now}){Object.assign(this,{db,service,auth,appCheck,appId,apiKey,now});}
  async start(body){
    requireValue(isOpaque(body?.codeChallenge));const key=deviceKey(body.publicKey),deviceId=opaque(),expiresAt=this.now()+600000;
    await this.db.collection(collection).doc(hash(deviceId)).create({codeChallenge:body.codeChallenge,publicKey:key.encoded,keyId:key.id,expiresAt,deleteAfter:Timestamp.fromMillis(expiresAt)});return {deviceId};
  }
  async approve(token,body){
    const user=await this.service.user(token,true);requireValue(isOpaque(body?.deviceId)&&isOpaque(body?.codeChallenge));const ref=this.db.collection(collection).doc(hash(body.deviceId));
    return this.db.runTransaction(async tx=>{const doc=await tx.get(ref);requireValue(doc.exists&&doc.data().expiresAt>this.now()&&equal(doc.data().codeChallenge,body.codeChallenge),'Geräteanmeldung ist abgelaufen',409);const data=doc.data();if(data.uid){requireValue(data.uid===user.uid,'Die Maschine wurde bereits mit einem anderen Konto bestätigt',409);return {status:'approved'};}const code=opaque();tx.create(this.db.collection('kitchenosAuthCodesV2').doc(hash(code)),{uid:user.uid,publicKey:data.publicKey,keyId:data.keyId,codeChallenge:data.codeChallenge,expiresAt:data.expiresAt,deleteAfter:Timestamp.fromMillis(data.expiresAt)});tx.update(ref,{uid:user.uid,code});return {status:'approved'};});
  }
  async poll(body){
    requireValue(isOpaque(body?.deviceId)&&isOpaque(body?.codeVerifier));return this.db.runTransaction(async tx=>{const doc=await tx.get(this.db.collection(collection).doc(hash(body.deviceId)));requireValue(doc.exists&&doc.data().expiresAt>this.now()&&equal(doc.data().codeChallenge,challenge(body.codeVerifier)),'Geräteanmeldung ist abgelaufen',401);return doc.data().code?{status:'approved',code:doc.data().code}:{status:'pending'};});
  }
  async exchange(body){
    requireValue(isOpaque(body?.code)&&isOpaque(body?.codeVerifier));
    const session=await this.db.runTransaction(async tx=>{const ref=this.db.collection('kitchenosAuthCodesV2').doc(hash(body.code)),doc=await tx.get(ref);requireValue(doc.exists&&doc.data().expiresAt>this.now()&&equal(doc.data().codeChallenge,challenge(body.codeVerifier)),'Anmeldung abgelaufen',401);const data=doc.data();requireValue(proof(deviceKey(data.publicKey).key,`kitchen-login-v1\n${body.code}\n${body.codeVerifier}`,body.deviceSignature),'Gerätenachweis ungültig',401);tx.delete(ref);tx.set(this.db.collection('kitchenosDeviceKeysV1').doc(hash(data.uid+':'+data.keyId)),{uid:data.uid,keyId:data.keyId,publicKey:data.publicKey,approvedAt:this.now(),disabled:false});return data;});
    const [customToken,appCheck]=await Promise.all([this.auth.createCustomToken(session.uid).catch(error=>{console.error(JSON.stringify({event:'kitchen_auth_signing_failed',code:String(error.code??'unknown').slice(0,80)}));throw new ApiError(503,'Die Maschinenanmeldung konnte noch nicht abgeschlossen werden. Bitte einen neuen Code verwenden.','auth_signing_failed');}),this.appCheck.createToken(this.appId,{ttlMillis:3600000}).catch(error=>{console.error(JSON.stringify({event:'kitchen_device_attestation_failed',code:String(error.code??'unknown').slice(0,80)}));throw new ApiError(503,'Der Gerätenachweis konnte noch nicht abgeschlossen werden. Bitte einen neuen Code verwenden.','device_attestation_failed');})]);
    return {uid:session.uid,firebaseApiKey:this.apiKey,customToken,appCheckToken:appCheck.token,appCheckExpiresAt:this.now()+appCheck.ttlMillis,deviceKeyId:session.keyId};
  }
  uid(value){requireValue(typeof value==='string'&&value.length>0&&value.length<=128&&!/[\x00-\x1f]/.test(value));return value;}
  async challenge(token,body){
    const user=body?.uid?{uid:this.uid(body.uid)}:await this.service.user(token);requireValue(typeof body?.deviceKeyId==='string'&&/^[a-f0-9]{64}$/.test(body.deviceKeyId));const nonce=opaque();
    await this.db.runTransaction(async tx=>{const doc=await tx.get(this.db.collection('kitchenosDeviceKeysV1').doc(hash(user.uid+':'+body.deviceKeyId)));requireValue(doc.exists&&!doc.data().disabled,'Gerät nicht freigegeben',403);const quota=this.db.collection('kitchenosDeviceChallengeQuotaV1').doc(hash(user.uid+':'+body.deviceKeyId)+':'+Math.floor(this.now()/3600000)),previous=await tx.get(quota);const used=previous.exists?previous.data().used:0;requireValue(used<30,'Bitte später erneut verbinden',429);tx.set(quota,{used:used+1,deleteAfter:Timestamp.fromMillis(this.now()+86400000)});tx.create(this.db.collection('kitchenosDeviceChallengesV1').doc(hash(nonce)),{uid:user.uid,keyId:body.deviceKeyId,expiresAt:this.now()+120000,deleteAfter:Timestamp.fromMillis(this.now()+120000)});});return {nonce};
  }
  async attest(token,body){
    const user=body?.uid?{uid:this.uid(body.uid)}:await this.service.user(token);requireValue(isOpaque(body?.nonce)&&typeof body.deviceKeyId==='string'&&/^[a-f0-9]{64}$/.test(body.deviceKeyId));
    await this.db.runTransaction(async tx=>{const ref=this.db.collection('kitchenosDeviceChallengesV1').doc(hash(body.nonce)),nonce=await tx.get(ref),device=await tx.get(this.db.collection('kitchenosDeviceKeysV1').doc(hash(user.uid+':'+body.deviceKeyId)));requireValue(nonce.exists&&device.exists&&!device.data().disabled&&nonce.data().expiresAt>this.now()&&nonce.data().uid===user.uid&&nonce.data().keyId===body.deviceKeyId,'Gerätenachweis abgelaufen',401);requireValue(proof(deviceKey(device.data().publicKey).key,`kitchen-attest-v1\n${body.nonce}\n${body.deviceKeyId}`,body.signature),'Gerätenachweis ungültig',401);tx.delete(ref);});
    const result=await this.appCheck.createToken(this.appId,{ttlMillis:3600000});return {appCheckToken:result.token,appCheckExpiresAt:this.now()+result.ttlMillis};
  }
}
