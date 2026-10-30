import { initializeApp } from 'firebase/app';
import { getAuth, setPersistence, browserSessionPersistence, GoogleAuthProvider, FacebookAuthProvider,
  OAuthProvider, signInWithPopup, signInWithRedirect, getRedirectResult,
  sendSignInLinkToEmail, isSignInWithEmailLink, signInWithEmailLink, signOut } from 'firebase/auth';
import { authContext } from '../src/auth-context.mjs';

const status = document.querySelector('#status'), login = document.querySelector('#login');
const show = message => { status.textContent = message; };
const valid = value => typeof value === 'string' && /^[A-Za-z0-9_-]{43}$/.test(value);
try {
  const configResponse = await fetch('/v1/config', { cache: 'no-store' });
  if (!configResponse.ok) throw new Error('Die Kontoanbindung ist gerade nicht erreichbar.');
  const config = await configResponse.json(), context=authContext(location.href,config);
  document.querySelector('#provider').textContent = `Anmelden mit ${({google:'Google',microsoft:'Microsoft',facebook:'Meta / Facebook',apple:'Apple',email:'E-Mail-Link'})[context.provider]}`;
  const auth = getAuth(initializeApp(config.firebase));
  await setPersistence(auth, browserSessionPersistence);
  async function finish(user) {
    login.disabled = true; show('Konto wird mit Calomatik Pro verbunden …');
    const response = await fetch(context.deviceId?'/v1/auth/device/approve':'/v1/auth/code', { method:'POST', headers:{'Content-Type':'application/json',Authorization:`Bearer ${await user.getIdToken(true)}`}, body:JSON.stringify({codeChallenge:context.challenge,...(context.deviceId?{deviceId:context.deviceId}:{})}) });
    if (!response.ok) throw new Error('Die Anmeldung konnte nicht abgeschlossen werden. Bitte erneut in Calomatik Pro beginnen.');
    const result=await response.json();
    if(context.deviceId){await signOut(auth);sessionStorage.removeItem('kitchen-email');show('Deine Küchenmaschine wurde freigegeben. Sie schließt die Anmeldung automatisch ab. Du kannst dieses Fenster schließen.');return;}
    const { code } = result;
    const callback = new URL(context.redirect); callback.searchParams.set('code',code); callback.searchParams.set('state',context.state);
    // No Firebase tokens, refresh tokens or user identity ever appear in this URL.
    await signOut(auth); sessionStorage.removeItem('kitchen-email');
    const link = document.querySelector('#return'); link.href = callback.href; link.hidden = false;
    show('Angemeldet. Tippe auf „Zurück zu Calomatik Pro“, um dein Konto auf dem Gerät zu verbinden.');
  }
  const emailForm = document.querySelector('#email-form'), email = document.querySelector('#email');
  if (isSignInWithEmailLink(auth, location.href)) {
    login.hidden = true; emailForm.hidden = false;
    email.value = sessionStorage.getItem('kitchen-email') || '';
    emailForm.querySelector('button').textContent = 'E-Mail bestätigen und anmelden';
    show('Bestätige deine E-Mail-Adresse, um die Anmeldung abzuschließen.');
    emailForm.onsubmit = async event => { event.preventDefault();
      try { await finish((await signInWithEmailLink(auth,email.value.trim(),location.href)).user); }
      catch { show('Der Link oder die E-Mail-Adresse konnte nicht bestätigt werden. Bitte neu anmelden.'); }
    };
  } else if (context.provider === 'email') {
    login.hidden = true; emailForm.hidden = false; show('Du erhältst einen Link zur Anmeldung per E-Mail.');
    emailForm.onsubmit = async event => { event.preventDefault();
      const submit = emailForm.querySelector('button'); submit.disabled = true;
      try {
        const continuation = new URL('/auth/complete',config.origin);
        continuation.search = new URLSearchParams({state:context.state,code_challenge:context.challenge,provider:context.provider,redirect_uri:context.redirect});
        if(context.deviceId)continuation.searchParams.set('device_id',context.deviceId);
        await sendSignInLinkToEmail(auth,email.value.trim(),{url:continuation.href,handleCodeInApp:true});
        sessionStorage.setItem('kitchen-email',email.value.trim()); show('Öffne den Link aus deiner E-Mail auf diesem Gerät.');
      } catch { show('Der Link konnte nicht gesendet werden. Bitte später erneut versuchen.'); }
      finally { submit.disabled = false; }
    };
  } else {
    const redirected = await getRedirectResult(auth);
    if (redirected) await finish(redirected.user);
    else { login.disabled = false; show('Die Anmeldung öffnet den ausgewählten Anbieter.'); }
    login.onclick = async () => {
      login.disabled = true;
      const provider = context.provider === 'google' ? new GoogleAuthProvider()
        : context.provider === 'facebook' ? new FacebookAuthProvider()
        : new OAuthProvider(context.provider === 'microsoft' ? 'microsoft.com' : 'apple.com');
      if (context.provider === 'apple') { provider.addScope('email'); provider.addScope('name'); }
      try { await finish((await signInWithPopup(auth,provider)).user); }
      catch (error) {
        if (error.code === 'auth/popup-blocked') {
          try { await signInWithRedirect(auth,provider); } catch { show('Der Anbieter konnte nicht geöffnet werden.'); login.disabled = false; }
        } else { show('Anmeldung abgebrochen oder Anbieter derzeit nicht verfügbar.'); login.disabled = false; }
      }
    };
  }
} catch (error) { show(error.message || 'Bitte die Anmeldung in Calomatik Pro neu beginnen.'); }
