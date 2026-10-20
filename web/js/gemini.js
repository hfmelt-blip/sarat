// Gemini مباشرة من المتصفح بمفتاح المستخدم المجاني.

import { hhmm } from './ui.js';

const BASE = 'https://generativelanguage.googleapis.com/v1beta';
export const MODELS = ['gemini-3.8-flash', 'gemini-3.5-flash-lite'];

export class GeminiError extends Error {}

const MEAL_SYSTEM = `You are a nutrition assistant inside an Arabic calorie-tracking app used mainly in Iraq.
You know Iraqi and Middle Eastern dishes well (timman, marqa, dolma, kubba, tashreeb, qeemar, kahi, samoon, falafel, etc.).

Task: identify the foods in the meal (from a photo or a text description), estimate realistic portions,
and estimate calories and macros for each item. Then judge the meal against the user's diet goals.

Rules:
- Write every user-facing string (mealName, item names, portion, verdictReason, suggestion, medicationNote) in simple Iraqi-friendly Arabic.
- Estimate portions from visual cues (plate size, utensils). When unsure, assume a normal adult portion. Never return 0 calories for real food.
- verdict must be one of: GOOD, OK_WITH_CHANGES, NOT_SUITABLE.
- fit for each item must be one of: GOOD, MODERATE, AVOID.
- verdictReason: one short sentence explaining the verdict in relation to the goals.
- suggestion: one short, practical change or a better alternative. Empty string if the meal is already GOOD.
- If the meal is larger than the calories remaining today, mention it in verdictReason.
- If the photo is not food, return isFood=false with an empty items list.
- Do not diagnose or claim the diet treats a disease. Food choices support, and never replace, medical treatment.

Goal guidance:
- INSULIN_RESISTANCE: penalise added sugar, sweet drinks, syrup, white flour, large portions of white rice/bread, sweets.
  Favour protein, vegetables, legumes, fibre, whole grains, healthy fats. Eating vegetables/protein before starch helps.
- HYPOTHYROID: favour a balanced diet with iodine (iodised salt, fish, eggs, dairy) and selenium (fish, eggs, legumes).
  Levothyroxine is usually taken on an empty stomach; coffee, soy, very high fibre and calcium- or iron-rich foods or supplements
  can reduce its absorption if eaten too close to the dose. If a medication time is given and the meal is within 60 minutes after it,
  or within 4 hours for calcium/iron-rich items, write a short note in medicationNote. Otherwise leave medicationNote empty.
- OBESITY_LIPIDS (obesity, high triglycerides, visceral fat): penalise added sugar and fructose drinks, refined carbs, fried food,
  saturated and trans fats, processed meat, large portions. Favour vegetables, legumes, fish/omega-3, lean protein, fibre.
- If several goals are selected, the strictest guidance wins.`;

const PLAN_SYSTEM = `You are a home-cooking nutrition coach inside an Arabic weight-loss app used mainly in Iraq.
You know Iraqi and Middle Eastern home cooking well.

Task: suggest ONE meal the user can cook now from the ingredients they have at home, sized to a calorie budget.

Rules:
- Use only the listed ingredients, plus basic staples every kitchen has: salt, pepper, common spices, water, lemon, and a small amount of cooking oil (count the oil's calories).
- If a good meal really needs one missing item, you may add at most ONE, with available=false. Prefer meals with none missing.
- Total calories must be within about 10% of the budget. Aim for the protein, carbs and fat targets given, protein first.
- Give realistic home amounts in simple Arabic ("٢ بيضة", "نص كوب", "ملعقة كبيرة") and grams.
- Steps: short, practical, in order, 3 to 7 steps, simple Iraqi-friendly Arabic.
- Respect the user's diet goals (INSULIN_RESISTANCE: low sugar and refined carbs, more fibre and protein; HYPOTHYROID: balanced, avoid soy/coffee/high calcium near the medication time; OBESITY_LIPIDS: low sugar, little saturated fat, no deep frying).
- Fit the meal type: breakfast foods for breakfast, light for a snack, lighter for a late dinner.
- If the user names a dish they want, make that dish (or the closest healthy version) adapted to the budget and ingredients; say in note what you changed.
- If the user asks to modify a previous suggestion, apply the change and keep the rest.
- If no sensible meal is possible from the ingredients, set possible=false and explain in note what to add.
- All user-facing text in simple Arabic. verdict: GOOD, OK_WITH_CHANGES or NOT_SUITABLE for the goals.`;

const VERDICT = { type: 'STRING', format: 'enum', enum: ['GOOD', 'OK_WITH_CHANGES', 'NOT_SUITABLE'] };

const MEAL_SCHEMA = {
  type: 'OBJECT',
  properties: {
    isFood: { type: 'BOOLEAN' },
    mealName: { type: 'STRING' },
    items: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        properties: {
          name: { type: 'STRING' }, portion: { type: 'STRING' }, grams: { type: 'INTEGER' }, calories: { type: 'INTEGER' },
          proteinG: { type: 'NUMBER' }, carbsG: { type: 'NUMBER' }, fatG: { type: 'NUMBER' },
          fit: { type: 'STRING', format: 'enum', enum: ['GOOD', 'MODERATE', 'AVOID'] },
        },
        required: ['name', 'portion', 'grams', 'calories', 'proteinG', 'carbsG', 'fatG', 'fit'],
      },
    },
    verdict: VERDICT,
    verdictReason: { type: 'STRING' },
    suggestion: { type: 'STRING' },
    medicationNote: { type: 'STRING' },
  },
  required: ['isFood', 'mealName', 'items', 'verdict', 'verdictReason', 'suggestion', 'medicationNote'],
};

const PLAN_SCHEMA = {
  type: 'OBJECT',
  properties: {
    possible: { type: 'BOOLEAN' },
    dishName: { type: 'STRING' },
    summary: { type: 'STRING' },
    ingredients: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        properties: {
          name: { type: 'STRING' }, amount: { type: 'STRING' }, grams: { type: 'INTEGER' }, calories: { type: 'INTEGER' },
          proteinG: { type: 'NUMBER' }, carbsG: { type: 'NUMBER' }, fatG: { type: 'NUMBER' }, available: { type: 'BOOLEAN' },
        },
        required: ['name', 'amount', 'grams', 'calories', 'proteinG', 'carbsG', 'fatG', 'available'],
      },
    },
    steps: { type: 'ARRAY', items: { type: 'STRING' } },
    prepMinutes: { type: 'INTEGER' },
    verdict: VERDICT,
    note: { type: 'STRING' },
  },
  required: ['possible', 'dishName', 'summary', 'ingredients', 'steps', 'prepMinutes', 'verdict', 'note'],
};

function mealText(ctx, description) {
  const lines = [
    `User goals: ${ctx.goals.length ? ctx.goals.join(', ') : 'GENERAL_WEIGHT_LOSS'}`,
    `Daily calorie target: ${ctx.dailyTarget}`,
    `Calories remaining today before this meal: ${ctx.remaining}`,
    `Current time: ${hhmm(ctx.nowMinute)}`,
  ];
  if (ctx.medMinute != null) lines.push(`Thyroid medication time: ${hhmm(ctx.medMinute)}`);
  lines.push('');
  lines.push(description ? `The user described the meal (in Arabic): "${description}"` : 'Analyze the attached meal photo.');
  return lines.join('\n');
}

export function planText(o) {
  const b = o.budget;
  const L = [
    `Meal type: ${o.slotEnglish}`,
    `Current time: ${hhmm(o.nowMinute)}`,
    `Calorie budget for this meal: ${b.kcal}`,
    `Targets for this meal: protein ${b.proteinG} g, carbs ${b.carbsG} g, fat ${b.fatG} g`,
    `Calories left today before this meal: ${b.remainingBefore}`,
  ];
  if (b.later.length) L.push('Meals still to come today: ' + b.later.map(([s, k]) => `${o.slotNames[s]} ~${k} kcal`).join(', '));
  L.push(`Diet goals: ${o.goals.length ? o.goals.join(', ') : 'GENERAL_WEIGHT_LOSS'}`);
  if (o.medMinute != null) L.push(`Thyroid medication time: ${hhmm(o.medMinute)}`);
  L.push(`Ingredients at home (Arabic): ${o.pantry}`);
  if (o.wish) L.push(`The user wants this dish: ${o.wish}`);
  if (o.exclude.length) L.push(`Suggest something different from: ${o.exclude.join(', ')}`);
  const f = o.feedback;
  if (f) {
    if (f.disliked.length) L.push(`The user did NOT like these earlier suggestions, avoid them and similar dishes: ${f.disliked.join(', ')}`);
    if (f.liked.length) L.push(`The user liked these earlier suggestions (similar styles are welcome): ${f.liked.join(', ')}`);
    if (f.hungry.length) L.push(`The user was still hungry after: ${f.hungry.join(', ')}. Make meals more filling within the same calories: more protein, fibre, vegetables and volume, fewer calorie-dense small portions.`);
  }
  if (o.previous && o.change) {
    L.push(`Previous suggestion: ${o.previous.dishName} — ` + o.previous.ingredients.map((i) => `${i.name} ${i.amount}`).join(', '));
    L.push(`The user asks to change it: ${o.change}`);
  }
  return L.join('\n');
}

function errorMessage(code, model) {
  if (code === 400) return 'الطلب مرفوض من Gemini (400).';
  if (code === 401 || code === 403) return 'مفتاح Gemini غير صالح. غيّره من الملف.';
  if (code === 404) return `النموذج ${model} غير متاح.`;
  if (code === 429) return 'خلصت الحصة المجانية لهذي الدقيقة أو اليوم. انتظر شوية وحاول.';
  if (code >= 500) return 'خدمة Gemini مشغولة حالياً. حاول بعد قليل.';
  return `خطأ من Gemini (${code}).`;
}

class HttpError extends Error { constructor(code, msg) { super(msg); this.code = code; } }

async function call(model, key, body) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 60000);
  let res;
  try {
    res = await fetch(`${BASE}/models/${model}:generateContent`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
      body: JSON.stringify(body),
      signal: ctrl.signal,
    });
  } catch (e) {
    if (e.name === 'AbortError') throw new HttpError(408, 'Gemini طوّل بالرد. حاول مرة ثانية.');
    if (!navigator.onLine) throw new GeminiError('ما كو اتصال بالإنترنت. تأكد من الشبكة وحاول مرة ثانية.');
    throw new HttpError(503, 'تعذّر الوصول لخدمة Gemini. حاول بعد شوية.');
  } finally {
    clearTimeout(timer);
  }
  const text = await res.text();
  if (!res.ok) throw new HttpError(res.status, errorMessage(res.status, model));
  return text;
}

function extract(raw) {
  const root = JSON.parse(raw);
  const cand = root.candidates && root.candidates[0];
  if (!cand) throw new GeminiError('ما وصل رد من التحليل. جرّب صورة أوضح أو وصفاً أدق.');
  const parts = (cand.content && cand.content.parts) || [];
  let text = parts.filter((p) => !p.thought).map((p) => p.text || '').join('').trim();
  text = text.replace(/^```json/, '').replace(/^```/, '').replace(/```$/, '').trim();
  if (!text) throw new GeminiError('الرد كان فارغاً. حاول مرة ثانية.');
  return text;
}

async function requestJson(key, system, text, imageBase64, schema) {
  if (!key) throw new GeminiError('اربط Gemini أولاً: الصق مفتاحك المجاني من شاشة الملف.');
  const body = (low) => {
    const parts = [];
    if (imageBase64) parts.push({ inlineData: { mimeType: 'image/jpeg', data: imageBase64 } });
    parts.push({ text });
    const generationConfig = { responseMimeType: 'application/json', responseSchema: schema };
    if (low) generationConfig.thinkingConfig = { thinkingLevel: 'low' };
    return { systemInstruction: { parts: [{ text: system }] }, contents: [{ role: 'user', parts }], generationConfig };
  };
  let last;
  for (const model of MODELS) {
    try {
      let raw;
      try { raw = await call(model, key, body(true)); }
      catch (e) { if (e instanceof HttpError && e.code === 400) raw = await call(model, key, body(false)); else throw e; }
      return extract(raw);
    } catch (e) {
      if (e instanceof HttpError) {
        last = e;
        if ([429, 404, 408].includes(e.code) || e.code >= 500) continue;
        throw new GeminiError(e.message);
      }
      throw e;
    }
  }
  throw new GeminiError(last ? last.message : 'خدمة Gemini غير متاحة حالياً.');
}

function parse(text, msg) {
  try { return JSON.parse(text); } catch (_) { throw new GeminiError(msg); }
}

export async function analyzeMeal(key, ctx, { description = null, imageBase64 = null }) {
  const r = parse(await requestJson(key, MEAL_SYSTEM, mealText(ctx, description), imageBase64, MEAL_SCHEMA), 'ما قدرنا نفهم رد التحليل. حاول مرة ثانية.');
  r.items = (r.items || []).map((i) => ({ name: i.name || '', portion: i.portion || '', grams: i.grams || 0, calories: Math.round(i.calories || 0), proteinG: i.proteinG || 0, carbsG: i.carbsG || 0, fatG: i.fatG || 0, fit: i.fit || 'MODERATE' }));
  return r;
}

export async function suggestMeal(key, text) {
  const r = parse(await requestJson(key, PLAN_SYSTEM, text, null, PLAN_SCHEMA), 'ما قدرنا نفهم الاقتراح. حاول مرة ثانية.');
  r.ingredients = (r.ingredients || []).map((i) => ({ name: i.name || '', amount: i.amount || '', grams: i.grams || 0, calories: Math.round(i.calories || 0), proteinG: i.proteinG || 0, carbsG: i.carbsG || 0, fatG: i.fatG || 0, available: i.available !== false }));
  r.steps = r.steps || [];
  return r;
}

export async function verifyKey(key) {
  try {
    const res = await fetch(`${BASE}/models?pageSize=1`, { headers: { 'x-goog-api-key': key.trim() } });
    if (res.ok) return null;
    if ([400, 401, 403].includes(res.status)) return 'المفتاح غير صحيح. تأكد إنك نسخته كامل.';
    return `ما قدرنا نتحقق من المفتاح (${res.status}). حاول بعد شوية.`;
  } catch (_) {
    return 'تعذّر الاتصال. تأكد من الإنترنت.';
  }
}
