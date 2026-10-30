import {ApiError} from './domain.mjs';

/** At most one schema-repair call under the same leased job and quota reservation.
 * No draft is persisted or returned before the full validator accepts it. */
export async function validatedGeneration(model,prompt,context,validate){
  let previous,problem;const started=Date.now();
  for(let attempt=0;attempt<2;attempt++){
    try{
      previous=await model.complete(prompt+(attempt?'\nDie vorige JSON-Antwort ist bei der strikten Prüfung durchgefallen. Korrigiere ausschließlich den Rezeptentwurf anhand von validationError. Alle ursprünglichen Geräte-, Zutaten- und Portionsregeln bleiben verbindlich. Antworte erneut mit dem vollständigen JSON, ohne Erläuterungen.':''),attempt?{request:context,previous,validationError:problem.message}:context,undefined,{timeoutMs:attempt?25000:40000});
      return validate(previous);
    }catch(error){
      if(!(error instanceof ApiError)||![422,502].includes(error.status)||attempt>0||Date.now()-started>55000)throw error;
      problem=error;
    }
  }
}
