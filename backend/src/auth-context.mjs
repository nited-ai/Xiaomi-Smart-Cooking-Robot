/** Supports both Firebase's redirected continuation and a custom email action handler. */
export function authContext(address, config) {
  const url=new URL(address), query=url.searchParams;
  let continuation;
  if(query.has('continueUrl')) {
    continuation=new URL(query.get('continueUrl'));
    if(continuation.origin!==config.origin||continuation.pathname!=='/auth/complete') throw new Error('Ungültige Anmeldeweiterleitung.');
  }
  const value=name=>query.get(name)??continuation?.searchParams.get(name);
  const result={state:value('state'),challenge:value('code_challenge'),provider:value('provider'),redirect:value('redirect_uri')};
  const valid=value=>typeof value==='string'&&/^[A-Za-z0-9_-]{43}$/.test(value);
  if(!valid(result.state)||!valid(result.challenge)||!config.providers.includes(result.provider)||result.redirect!=='kitchenos://account/callback') throw new Error('Diese Anmeldung ist ungültig. Bitte in KitchenOS neu beginnen.');
  const deviceId=value('device_id');if(deviceId!==null&&deviceId!==undefined){if(!valid(deviceId))throw new Error('Ungültige Geräteanmeldung.');result.deviceId=deviceId;}
  return result;
}
