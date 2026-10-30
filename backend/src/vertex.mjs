import { ApiError, recipePrompt, recipeSchema } from './domain.mjs';

export class VertexRecipes {
  constructor(credential, { project, location, model }) {
    if (!/^[a-z][a-z0-9-]+$/.test(project || '') || !/^[a-z0-9-]+$/.test(location || '') || !/^[a-z0-9.-]+$/.test(model || ''))
      throw new Error('KI-Projekt, Standort und Modell müssen explizit konfiguriert sein');
    this.credential = credential;
    const host = location === 'global' ? 'aiplatform.googleapis.com' : `${location}-aiplatform.googleapis.com`;
    this.url = `https://${host}/v1/projects/${project}/locations/${location}/publishers/google/models/${model}:generateContent`;
  }
  async generate(input) {
    const { access_token } = await this.credential.getAccessToken();
    const response = await fetch(this.url, { method: 'POST', signal: AbortSignal.timeout(35000),
      headers: { Authorization: `Bearer ${access_token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ systemInstruction: { parts: [{ text: recipePrompt }] },
        contents: [{ role: 'user', parts: [{ text: JSON.stringify(input) }] }],
        generationConfig: { maxOutputTokens: 4096, responseMimeType: 'application/json', responseSchema: recipeSchema } }) });
    if (!response.ok) throw new ApiError(response.status === 429 ? 429 : 502, 'Der Rezeptassistent ist derzeit nicht verfügbar');
    const bytes = await response.text();
    if (bytes.length > 1048576) throw new ApiError(502, 'Antwort des Assistenten ist zu groß');
    const candidate = JSON.parse(bytes).candidates?.[0];
    if (candidate?.finishReason !== 'STOP') throw new ApiError(502, 'Der Assistent hat keinen vollständigen Entwurf geliefert');
    try { return JSON.parse(candidate.content.parts.filter(p => !p.thought).map(p => p.text || '').join('')); }
    catch { throw new ApiError(502, 'Der Assistent hat keinen lesbaren Entwurf geliefert'); }
  }
}
