import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validatedGeneration} from '../src/validated-generation.mjs';
import {ApiError} from '../src/domain.mjs';
test('invalid model output is repaired once before it can escape validation',async()=>{let calls=0;const model={complete:async(prompt,context)=>{calls++;if(calls===2){assert.equal(context.validationError,'missing setting');assert.deepEqual(context.request,{private:'context'});assert.equal(context.previous.invalid,true);}return calls===1?{invalid:true}:{valid:true};}};const result=await validatedGeneration(model,'rules',{private:'context'},v=>{if(!v.valid)throw new ApiError(502,'missing setting');return v;});assert.deepEqual(result,{valid:true});assert.equal(calls,2);});
test('persistent invalid output is never returned and retries stop after two calls',async()=>{let calls=0;await assert.rejects(validatedGeneration({complete:async()=>{calls++;return {}; }},'rules',{},()=>{throw new ApiError(502,'invalid');}));assert.equal(calls,2);});
test('network and quota failures are not repeated as paid model repairs',async()=>{let calls=0;await assert.rejects(validatedGeneration({complete:async()=>{calls++;throw new ApiError(429,'quota');}},'rules',{},v=>v));assert.equal(calls,1);});
