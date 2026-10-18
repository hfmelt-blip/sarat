// عمليات البيانات المشتركة بين الشاشات.

import { data, getKv, setKv, put, remove } from './db.js';
import * as N from './nutrition.js';
import { epochDay, uid } from './ui.js';
import { scaleIng } from './recipes.js';

export const profile = () => getKv('profile');
export const apiKey = () => getKv('apiKey', '');
export const prefs = () => getKv('prefs', { mealsPerDay: 3, pantry: '', theme: 'auto' });
export async function setPrefs(patch) { await setKv('prefs', { ...prefs(), ...patch }); }

export function sortedWeights() { return [...data.weights].sort((a, b) => a.day - b.day || a.id - b.id); }
export function currentWeight() {
  const w = sortedWeights();
  return w.length ? w[w.length - 1].kg : profile().startWeightKg;
}
export function currentPlan() { return N.plan(profile(), currentWeight(), epochDay()); }

export function mealsOn(day) { return data.meals.filter((m) => m.day === day).sort((a, b) => a.minute - b.minute || a.id - b.id); }
export function totalsByDay() {
  const t = {};
  for (const m of data.meals) t[m.day] = (t[m.day] || 0) + m.calories;
  return t;
}
export function history() {
  const p = profile(), plan = currentPlan();
  return N.buildHistory(totalsByDay(), p.startEpochDay, epochDay(), plan.dailyTarget, plan.minimumCalories);
}

const sum = (items, k) => items.reduce((a, i) => a + (i[k] || 0), 0);

/** يحفظ وجبة محللة بالسجل. */
export async function addMeal(analysis, source, extra = {}) {
  const items = analysis.items;
  const row = {
    id: uid(), day: extra.day ?? epochDay(), minute: extra.minute ?? (new Date().getHours() * 60 + new Date().getMinutes()),
    title: analysis.mealName || 'وجبة', calories: Math.round(sum(items, 'calories')),
    proteinG: sum(items, 'proteinG'), carbsG: sum(items, 'carbsG'), fatG: sum(items, 'fatG'),
    verdict: analysis.verdict || 'OK_WITH_CHANGES', verdictReason: analysis.verdictReason || '', suggestion: analysis.suggestion || '',
    items, source, favorite: false, thumb: extra.thumb || null, full: null, liked: null,
  };
  await put('meals', row);
  return row;
}
export const deleteMeal = (id) => remove('meals', id);
export async function toggleFavorite(meal) {
  const v = !meal.favorite;
  for (const m of data.meals.filter((x) => x.title === meal.title)) await put('meals', { ...m, favorite: v });
}
export async function rateMeal(meal, full, liked) { await put('meals', { ...meal, full, liked }); }

/** المفضلة: المثبّتة أو المكررة مرتين أو أكثر، آخر نسخة من كل وحدة. */
export function favorites() {
  const groups = new Map();
  for (const m of data.meals) {
    const g = groups.get(m.title) || { last: m, count: 0, fav: false };
    g.count++; g.fav = g.fav || m.favorite;
    if (m.id > g.last.id) g.last = m;
    groups.set(m.title, g);
  }
  return [...groups.values()].filter((g) => g.count >= 2 || g.fav)
    .sort((a, b) => (b.fav - a.fav) || (b.count - a.count) || (b.last.id - a.last.id)).slice(0, 10).map((g) => g.last);
}
export async function logAgain(meal) {
  const { id, day, minute, full, liked, ...rest } = meal;
  const now = new Date();
  await put('meals', { ...rest, id: uid(), day: epochDay(), minute: now.getHours() * 60 + now.getMinutes(), full: null, liked: null });
}

export function planFeedback() {
  const rated = data.meals.filter((m) => m.source === 'PLAN' && (m.full != null || m.liked != null)).sort((a, b) => b.id - a.id);
  const seen = new Set(), latest = [];
  for (const m of rated) { const k = N.dishKey(m.title); if (!seen.has(k)) { seen.add(k); latest.push(m); } }
  return {
    disliked: latest.filter((m) => m.liked === false).map((m) => m.title).slice(0, 10),
    liked: latest.filter((m) => m.liked === true).map((m) => m.title).slice(0, 10),
    hungry: latest.filter((m) => m.full === false).map((m) => m.title).slice(0, 10),
  };
}

// ───── تصحيحات المستخدم لكل أكلة ─────
export function getCorrection(name) { return data.corrections.find((c) => c.key === N.dishKey(name)) || null; }
export async function saveCorrection(name, itemsPerServing) {
  if (!name) return;
  await put('corrections', { key: N.dishKey(name), title: name, items: itemsPerServing, updatedAt: Date.now() });
}

// ───── الوزن ─────
export async function addWeight(kg) {
  const p = profile();
  const today = epochDay();
  const ws = sortedWeights();
  const prev = ws.length ? ws[ws.length - 1] : { day: p.startEpochDay, kg: p.startWeightKg };
  let cal = null;
  if (prev.day < today) {
    cal = N.calibrate(p, prev.kg, prev.day, kg, today, totalsByDay());
    if (cal.reliable && cal.newAdjustment !== (p.tdeeAdjustment || 0)) await setKv('profile', { ...p, tdeeAdjustment: cal.newAdjustment });
  }
  // وزن واحد لكل يوم
  for (const w of data.weights.filter((w) => w.day === today)) await remove('weights', w.id);
  await put('weights', { id: uid(), day: today, kg });
  return cal;
}
export const deleteWeight = (id) => remove('weights', id);

// ───── الوصفات ─────
export function savedRecipes() { return [...data.recipes].sort((a, b) => b.createdAt - a.createdAt); }
export function getRecipe(id) { return data.recipes.find((r) => r.id === id) || null; }
export async function saveRecipe(s, source = 'AI', id = 0) {
  const existing = id ? getRecipe(id) : data.recipes.find((r) => N.dishKey(r.title) === N.dishKey(s.dishName));
  if (existing) {
    await put('recipes', { ...existing, title: s.dishName, recipe: s });
    return existing.id;
  }
  const row = { id: uid(), title: s.dishName, source, recipe: s, createdAt: Date.now() };
  await put('recipes', row);
  return row.id;
}
export const deleteRecipe = (id) => remove('recipes', id);

// ───── المشتريات ─────
export function shopping() { return [...data.shopping].sort((a, b) => (a.done - b.done) || (b.createdAt - a.createdAt)); }
export async function addShopping(name, amount = '') {
  const n = name.trim();
  if (!n) return false;
  if (data.shopping.some((s) => !s.done && N.dishKey(s.name) === N.dishKey(n))) return false;
  await put('shopping', { id: uid(), name: n, amount, done: false, createdAt: Date.now() });
  return true;
}
export async function setShoppingDone(item, done) { await put('shopping', { ...item, done }); }
export const deleteShopping = (id) => remove('shopping', id);
export async function clearBought() { for (const s of data.shopping.filter((x) => x.done)) await remove('shopping', s.id); }

export function suggestionToAnalysis(s) {
  return {
    mealName: s.dishName,
    items: s.ingredients.map((i) => ({ name: i.name, portion: i.amount, grams: i.grams, calories: i.calories, proteinG: i.proteinG, carbsG: i.carbsG, fatG: i.fatG, fit: 'GOOD' })),
    verdict: s.verdict, verdictReason: s.summary, suggestion: '',
  };
}

export { scaleIng };
