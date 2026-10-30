import { ApiError, requireValue, opaque, hash, challenge, equal, isOpaque, generationInput,
  recipe, recipeRevision, validationInput, newRecipeId } from './domain.mjs';
import { fromKalomatik } from './kalomatik.mjs';

/** Injected store/auth/model let tests exercise security and ownership without cloud credentials. */
export class KitchenService {
  constructor({ store, auth, model, config, now = Date.now }) {
    Object.assign(this, { store, auth, model, config, now });
  }
  async user(token, interactive = false) {
    requireValue(typeof token === 'string' && token.length < 16384, 'Anmeldung erforderlich', 401);
    let user;
    try { user = await this.auth.verifyIdToken(token, true); }
    catch { throw new ApiError(401, 'Anmeldung ungültig oder abgelaufen'); }
    const provider = user.firebase?.sign_in_provider;
    requireValue(user.uid && provider && provider !== 'anonymous', 'Bitte mit deinem Konto anmelden', 401);
    if (interactive) {
      requireValue(['google.com', 'microsoft.com', 'facebook.com', 'apple.com', 'password'].includes(provider),
        'Eine neue Anmeldung im Browser ist erforderlich', 401);
      requireValue(Number.isFinite(user.auth_time) && this.now() / 1000 - user.auth_time < 900,
        'Die Anmeldung ist abgelaufen', 401);
      if (provider === 'password') requireValue(user.email_verified === true, 'E-Mail noch nicht bestätigt', 401);
    }
    return user;
  }
  async issueCode(token, body) {
    const user = await this.user(token, true);
    requireValue(isOpaque(body?.codeChallenge));
    const code = opaque();
    await this.store.createCode(hash(code), { uid: user.uid, codeChallenge: body.codeChallenge,
      expiresAt: this.now() + 120000 });
    return { code };
  }
  async exchange(body) {
    requireValue(isOpaque(body?.code) && isOpaque(body?.codeVerifier));
    // Verification and one-time consumption are a single transaction. Wrong proof does not consume.
    const uid = await this.store.consumeCode(hash(body.code), challenge(body.codeVerifier), this.now());
    requireValue(uid, 'Anmeldung abgelaufen oder bereits verwendet', 401);
    return { uid, firebaseApiKey: this.config.firebase.apiKey,
      customToken: await this.auth.createCustomToken(uid) };
  }
  async startDevice(body) {
    requireValue(isOpaque(body?.codeChallenge));const deviceId=opaque();
    await this.store.startDevice(deviceId,body.codeChallenge,this.now());return {deviceId};
  }
  async approveDevice(token,body) {
    const user=await this.user(token,true);requireValue(isOpaque(body?.deviceId)&&isOpaque(body?.codeChallenge));
    requireValue(await this.store.approveDevice(body.deviceId,body.codeChallenge,user.uid,opaque(),this.now()),'Geräteanmeldung ist abgelaufen oder bereits zugeordnet',409);
    return {status:'approved'};
  }
  async pollDevice(body) {
    requireValue(isOpaque(body?.deviceId)&&isOpaque(body?.codeVerifier));
    const result=await this.store.pollDevice(body.deviceId,challenge(body.codeVerifier),this.now());
    requireValue(result,'Geräteanmeldung ist abgelaufen',401);return result;
  }
  async generate(token, body) {
    const user = await this.user(token), input = generationInput(body), fingerprint = hash(JSON.stringify(input));
    const claimed = await this.store.claimJob(user.uid, input.requestId, fingerprint, this.now());
    if (claimed.result) return claimed.result;
    requireValue(claimed.owner, 'Diese Anfrage wird bearbeitet. Bitte erneut mit derselben Anfrage-ID abrufen.', 409);
    try {
      const generated = recipe(await this.model.generate(input), newRecipeId());
      const result = { recipe: generated, revision: recipeRevision(generated), status: 'draft' };
      await this.store.finishJob(user.uid, input.requestId, fingerprint, result, this.now());
      return result;
    } catch (error) {
      // Never automatically retry an uncertain model call and charge the user twice.
      await this.store.failJob(user.uid, input.requestId, fingerprint);
      throw error;
    }
  }
  async contribute(token, body) {
    const user = await this.user(token);
    requireValue(body?.consent === true, 'Beitrag wurde nicht bestätigt');
    const projected = recipe(body.recipe), revision = recipeRevision(projected), catalogId = revision;
    const publicRecipe = { ...projected, id: `${revision.slice(0,8)}-${revision.slice(8,12)}-${revision.slice(12,16)}-${revision.slice(16,20)}-${revision.slice(20,32)}` };
    await this.store.contribute(user.uid, catalogId, { recipe: publicRecipe, revision, status: 'draft', createdAt: this.now() });
    return { catalogId, revision, status: 'draft' };
  }
  async contributeKalomatik(token, body) {
    await this.user(token);
    requireValue(body?.consent === true, 'Beitrag wurde nicht bestätigt');
    return this.contribute(token, { consent:true, recipe:fromKalomatik(body.meal,body.guide) });
  }
  async validate(token, body) {
    const user = await this.user(token), input = validationInput(body);
    const result = await this.store.validate(user.uid, input, this.now());
    requireValue(result, 'Diese Rezeptfassung ist nicht mehr verfügbar', 409);
    return result;
  }
  async catalog(token) {
    await this.user(token);
    return { recipes: await this.store.catalog(50) };
  }
  async drafts(token) {
    const user = await this.user(token);
    return { recipes: await this.store.drafts(user.uid, 50) };
  }
}
