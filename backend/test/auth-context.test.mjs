import test from 'node:test';
import {strict as assert} from 'node:assert';
import {authContext} from '../src/auth-context.mjs';
const config={origin:'https://kitchen.example',providers:['google','email']};
const query=new URLSearchParams({state:'s'.repeat(43),code_challenge:'c'.repeat(43),provider:'email',redirect_uri:'kitchenos://account/callback'});
test('direct and Firebase email continuation retain the original native PKCE context',()=>{
 const url=`${config.origin}/auth/complete?${query}`;
 assert.equal(authContext(url,config).provider,'email');
 const wrapped=`${config.origin}/auth/complete?mode=signIn&oobCode=test&continueUrl=${encodeURIComponent(url)}`;
 assert.deepEqual(authContext(wrapped,config),authContext(url,config));
});
test('foreign email continuations, alternate return schemes and disabled providers fail closed',()=>{
 assert.throws(()=>authContext(`${config.origin}/auth/complete?continueUrl=${encodeURIComponent('https://evil.example/auth/complete?'+query)}`,config));
 const bad=new URLSearchParams(query);bad.set('redirect_uri','otherapp://account/callback');assert.throws(()=>authContext(`${config.origin}/auth/start?${bad}`,config));
 bad.set('redirect_uri','kitchenos://account/callback');bad.set('provider','apple');assert.throws(()=>authContext(`${config.origin}/auth/start?${bad}`,config));
});
