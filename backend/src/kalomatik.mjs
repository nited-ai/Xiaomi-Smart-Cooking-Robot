import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { requireValue, recipe, newRecipeId } from './domain.mjs';

// Trusted, pinned repository source, never user-supplied code. This preserves the
// existing ingredient-basis and per-step share checks rather than approximating them.
const context = vm.createContext({ window: {} });
vm.runInContext(readFileSync(new URL('../integrations/kalomatik219/recipe-skill.js', import.meta.url),'utf8'), context, { timeout:1000 });
const skill = context.window.NutriSnapRecipeSkill;

/** Bridge an existing saved Kalomatik meal+guide into the same recipe catalog. */
export function fromKalomatik(meal, guide) {
  requireValue(meal && Array.isArray(meal.ingredients) && meal.ingredients.length > 0 && meal.ingredients.length <= 100);
  requireValue(typeof meal.portion === 'number' && Number.isFinite(meal.portion) && meal.portion > 0 && meal.portion <= 20);
  requireValue(Number.isInteger(meal.cookingPortions) && meal.cookingPortions >= 1 && meal.cookingPortions <= 20);
  const seen = new Set();
  for (const [index,item] of meal.ingredients.entries()) {
    requireValue(typeof item.name === 'string' && item.name.trim() && item.name.length <= 100);
    const id = item.id || `ingredient-${index}`;
    requireValue(typeof id === 'string' && id.length <= 100 && !seen.has(id)); seen.add(id);
    requireValue(!item.unit || typeof item.unit === 'string' && item.unit.length <= 16);
    const value = item.amount ?? item.menge_g;
    requireValue(value === null || value === undefined || value === '' || Number.isFinite(Number(value)) && Number(value) > 0 && Number(value) <= 1000000);
  }
  requireValue(skill.current(guide,meal), 'Diese Kalomatik-Anleitung gehört zu einem anderen Zutatenstand');
  let bound;
  try { bound = skill.bind(guide,meal); }
  catch { requireValue(false, 'Die Kalomatik-Anleitung hat unvollständige Zutaten oder Schritte'); }
  const cooking = skill.context(meal,true), ingredients = new Map(cooking.ingredients.map(i=>[i.id,i]));
  const amount = (item, share = 1) => {
    if (item.amount === null) return `${item.name} · Menge offen`;
    requireValue(item.amount > 0 && item.amount <= 1000000);
    return `${Number((item.amount * share).toFixed(6))} ${item.unit} ${item.name}`;
  };
  const steps = bound.steps.map(step => [step.title,
    step.ingredients.map(use=>amount(ingredients.get(use.ingredientId),use.share)).join(' · '),
    step.instruction,
    [step.minutes === null ? '' : `${step.minutes} Minuten`,step.temperatureC === null ? '' : `${step.temperatureC} °C`,step.heat].filter(Boolean).join(' · '),
    `So erkennst du es: ${step.cue}`].filter(Boolean).join('\n'));
  if (bound.equipment.length) steps.unshift(`Du brauchst: ${bound.equipment.join(', ')}`);
  if (bound.tips.length) steps.push(`Tipps: ${bound.tips.join('\n')}`);
  return recipe({title:bound.title,servings:cooking.servings,ingredients:cooking.ingredients.map(i=>amount(i)),steps},newRecipeId());
}
