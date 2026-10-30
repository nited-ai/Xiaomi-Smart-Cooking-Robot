import { ApiError, equal, hash, requireValue } from './domain.mjs';
import { Timestamp } from 'firebase-admin/firestore';

/** All collections are namespaced. Existing Kalomatik diary/recipe collections are untouched. */
export class FirebaseStore {
  constructor(db, dailyLimit = 20, globalLimit = 0, kind = "assistant") { requireValue(["assistant","dictation"].includes(kind));this.db = db; this.dailyLimit = dailyLimit; this.globalLimit=globalLimit;this.dictation=kind==="dictation"; }
  owner(uid) { return this.db.collection('kitchenosUsersV1').doc(hash(uid)); }
  job(uid, id) { return this.owner(uid).collection(this.dictation?'kitchenosDictationJobsV1':'kitchenosJobsV1').doc(id); }
  async quotaStatus(uid, now) {
    const date = new Date(now).toISOString().slice(0,10);
    return this.db.runTransaction(async tx => {
      const doc = await tx.get(this.owner(uid).collection('quotas').doc((this.dictation?'dictation-':'')+date));
      return {used:doc.exists?doc.data().used:0,limit:this.dailyLimit,
        resetAt:new Date(Date.parse(date+'T00:00:00Z')+86400000).toISOString()};
    });
  }
  async createCode(id, value) {
    await this.db.collection('kitchenosAuthCodesV1').doc(id).create({ ...value,
      deleteAfter: Timestamp.fromMillis(value.expiresAt) });
  }
  async startDevice(id, codeChallenge, now) {
    await this.db.collection('kitchenosDeviceAuthV1').doc(id).create({codeChallenge,expiresAt:now+600000,deleteAfter:Timestamp.fromMillis(now+600000)});
  }
  async approveDevice(id, codeChallenge, uid, code, now) {
    const ref=this.db.collection('kitchenosDeviceAuthV1').doc(id);
    return this.db.runTransaction(async tx=>{
      const doc=await tx.get(ref);
      if(!doc.exists||doc.data().expiresAt<=now||!equal(doc.data().codeChallenge,codeChallenge))return false;
      if(doc.data().code)return doc.data().uid===uid;
      tx.create(this.db.collection('kitchenosAuthCodesV1').doc(hash(code)),{uid,codeChallenge,expiresAt:now+120000,deleteAfter:Timestamp.fromMillis(now+120000)});
      tx.update(ref,{code,uid});return true;
    });
  }
  async pollDevice(id, proof, now) {
    // A transaction also makes this read consistent with approval. The code itself
    // remains usable only with the private verifier and is consumed by exchange().
    return this.db.runTransaction(async tx=>{
      const doc=await tx.get(this.db.collection('kitchenosDeviceAuthV1').doc(id));
      if(!doc.exists||doc.data().expiresAt<=now||!equal(doc.data().codeChallenge,proof))return null;
      return doc.data().code?{status:'approved',code:doc.data().code}:{status:'pending'};
    });
  }
  async consumeCode(id, proof, now) {
    return this.db.runTransaction(async tx => {
      const ref = this.db.collection('kitchenosAuthCodesV1').doc(id), doc = await tx.get(ref);
      if (!doc.exists || doc.data().expiresAt <= now || !equal(doc.data().codeChallenge, proof)) return null;
      tx.delete(ref); return doc.data().uid;
    });
  }
  async claimJob(uid, id, fingerprint, now, resource = null, compatibleFingerprint = null) {
    const ref = this.job(uid, id), quota = this.owner(uid).collection('quotas').doc((this.dictation?'dictation-':'')+new Date(now).toISOString().slice(0, 10));
    return this.db.runTransaction(async tx => {
      const job = await tx.get(ref);
      if (job.exists) {
        requireValue(equal(job.data().fingerprint, fingerprint) || equal(job.data().fingerprint, compatibleFingerprint), 'Anfrage-ID wurde bereits anders verwendet', 409);
        if (job.data().state === 'done') return { result: job.data().result };
        if (job.data().state === 'failed') throw new ApiError(409, 'Anfrage fehlgeschlagen. Du kannst jetzt einen neuen Versuch beginnen.', 'job_failed');
        return { owner: false };
      }
      if(resource){const shared=await tx.get(resource.ref);if(shared.exists&&shared.data().sourceRevision===resource.revision&&typeof shared.data().compatible==='boolean'&&(!resource.profileId||shared.data().profileId===resource.profileId)&&(!resource.preparationRevision||shared.data().preparationRevision===resource.preparationRevision))return {result:shared.data()};if(shared.exists&&shared.data().sourceRevision===resource.revision&&shared.data().leaseUntil>now)return {owner:false};}
      const day = await tx.get(quota), used = day.exists ? day.data().used : 0;
      if(used>=this.dailyLimit)throw new ApiError(429,this.dictation?'Tageslimit für Diktate erreicht':'Tageslimit für KI-Anfragen erreicht','personal_daily_limit');
      if(this.globalLimit){const global=this.db.collection('kitchenosAiQuotaV1').doc((this.dictation?'dictation-':'')+new Date(now).toISOString().slice(0,10)),doc=await tx.get(global),total=doc.exists?doc.data().used:0;if(total>=this.globalLimit)throw new ApiError(429,'Der KI-Dienst hat sein Tageslimit erreicht','service_daily_limit');tx.set(global,{used:total+1});}
      tx.set(quota, { used: used + 1 });if(resource)tx.set(resource.ref,{sourceRevision:resource.revision,profileId:resource.profileId??null,preparationRevision:resource.preparationRevision??null,status:"preparing",requestId:id,leaseUntil:now+120000});
      tx.create(ref, { fingerprint, state: 'running', createdAt: now,
        deleteAfter: Timestamp.fromMillis(now + 30 * 86400000) });
      return { owner: true };
    });
  }
  async finishJob(uid, id, fingerprint, result, now) {
    const ref = this.job(uid, id);
    await this.db.runTransaction(async tx => {
      const doc = await tx.get(ref);
      requireValue(doc.exists && equal(doc.data().fingerprint, fingerprint) && doc.data().state === 'running', 'Anfragezustand hat sich geändert', 409);
      // Persist only the result, never pantry, health preferences or the prompt.
      tx.update(ref, { state: 'done', result, completedAt: now });
      tx.set(this.owner(uid).collection('drafts').doc(result.recipe.id), {...result,createdAt:now});
    });
  }
  async failJob(uid, id, fingerprint) {
    const ref = this.job(uid, id);
    await this.db.runTransaction(async tx => {
      const doc = await tx.get(ref);
      if (doc.exists && equal(doc.data().fingerprint, fingerprint) && doc.data().state === 'running') tx.update(ref, { state: 'failed' });
    });
  }
  async finishAssistantJob(uid,id,fingerprint,result,now){
    const ref=this.job(uid,id);await this.db.runTransaction(async tx=>{
      const doc=await tx.get(ref);requireValue(doc.exists&&equal(doc.data().fingerprint,fingerprint)&&doc.data().state==='running','Anfragezustand hat sich geändert',409);
      tx.update(ref,{state:'done',result,completedAt:now,deleteAfter:Timestamp.fromMillis(now+7*86400000)});
    });
  }
  async contribute(uid, id, value) {
    const ref = this.db.collection('kitchenosCatalogV1').doc(id), day = this.owner(uid).collection('contributionQuotas').doc(new Date().toISOString().slice(0,10));
    await this.db.runTransaction(async tx => {
      const doc = await tx.get(ref), quota = await tx.get(day), used = quota.exists ? quota.data().used : 0;
      requireValue(used < 100, 'Beitragslimit erreicht', 429);
      tx.set(day, { used: used + 1 });
      if (!doc.exists) tx.create(ref, { ...value, validatorCount: 0, workedCount: 0, ratingTotal: 0 });
      tx.set(this.owner(uid).collection('contributions').doc(id), { revision: value.revision });
    });
  }
  async validate(uid, input, now) {
    const ref = this.db.collection('kitchenosCatalogV1').doc(input.catalogId);
    const personal = ref.collection('validations').doc(hash(uid));
    return this.db.runTransaction(async tx => {
      const doc = await tx.get(ref), previous = await tx.get(personal);
      if (!doc.exists || !equal(doc.data().revision, input.revision)) return null;
      const data = doc.data(), old = previous.exists ? previous.data() : null;
      const validatorCount = data.validatorCount + (old ? 0 : 1);
      const workedCount = data.workedCount + (input.worked ? 1 : 0) - (old?.worked ? 1 : 0);
      const ratingTotal = data.ratingTotal + input.rating - (old?.rating || 0);
      // Distinct accounts are counted once. This is self-reported experience, not a safety certificate.
      tx.set(personal, { revision: input.revision, rating: input.rating, worked: input.worked, at: now });
      tx.update(ref, { validatorCount, workedCount, ratingTotal });
      return { validatorCount, workedCount, averageRating: ratingTotal / validatorCount, status: 'community-feedback' };
    });
  }
  async catalog(limit) {
    const docs = await this.db.collection('kitchenosCatalogV1').orderBy('createdAt', 'desc').limit(limit).get();
    return bounded(docs.docs.map(doc => {
      const d = doc.data(); return { catalogId: doc.id, recipe: d.recipe, revision: d.revision,
        validatorCount: d.validatorCount, workedCount: d.workedCount,
        averageRating: d.validatorCount ? d.ratingTotal / d.validatorCount : null };
    }));
  }
  async drafts(uid, limit) {
    const docs = await this.owner(uid).collection('drafts').orderBy('createdAt','desc').limit(limit).get();
    return bounded(docs.docs.map(doc => ({ recipe: doc.data().recipe, status: 'draft' })));
  }
}
function bounded(rows){const result=[];let bytes=0;for(const row of rows){bytes+=Buffer.byteLength(JSON.stringify(row))+1;if(bytes>900000)break;result.push(row);}return result;}
