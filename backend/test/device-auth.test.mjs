import test from 'node:test';
import {strict as assert} from 'node:assert';
import {KitchenService} from '../src/service.mjs';
import {FirebaseStore} from '../src/firebase-store.mjs';
import {FakeFirestore} from './fake-firestore.mjs';
import {opaque,challenge} from '../src/domain.mjs';
function setup(){const db=new FakeFirestore();let now=1780000000000;const service=new KitchenService({store:new FirebaseStore(db),auth:{verifyIdToken:async uid=>({uid,auth_time:now/1000,firebase:{sign_in_provider:'google.com'}}),createCustomToken:async uid=>`token-${uid}`},config:{firebase:{apiKey:'public'}},now:()=>now});return {service,advance:ms=>{now+=ms;}};}
test('QR device approval reaches only the machine with the private verifier',async()=>{
 const {service}=setup(),verifier=opaque(),codeChallenge=challenge(verifier);
 const {deviceId}=await service.startDevice({codeChallenge});
 assert.equal((await service.pollDevice({deviceId,codeVerifier:verifier})).status,'pending');
 await assert.rejects(service.pollDevice({deviceId,codeVerifier:opaque()}),error=>error.status===401);
 await assert.rejects(service.approveDevice('alice',{deviceId,codeChallenge:opaque()}),error=>error.status===409);
 await service.approveDevice('alice',{deviceId,codeChallenge});
 await assert.rejects(service.approveDevice('bob',{deviceId,codeChallenge}),error=>error.status===409);
 const result=await service.pollDevice({deviceId,codeVerifier:verifier});assert.equal(result.status,'approved');
 assert.equal((await service.exchange({code:result.code,codeVerifier:verifier})).customToken,'token-alice');
 await assert.rejects(service.exchange({code:result.code,codeVerifier:verifier}),error=>error.status===401);
});
test('device approval expires and cannot be redeemed by another challenge',async()=>{
 const {service,advance}=setup(),verifier=opaque(),{deviceId}=await service.startDevice({codeChallenge:challenge(verifier)});
 advance(600001);await assert.rejects(service.pollDevice({deviceId,codeVerifier:verifier}),error=>error.status===401);
 await assert.rejects(service.approveDevice('alice',{deviceId,codeChallenge:challenge(verifier)}),error=>error.status===409);
});
