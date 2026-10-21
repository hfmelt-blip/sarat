// المطبخ: شنو آكل؟، الوصفات، تعديل وصفة، المشتريات.

import { h, icon, epochDay, nowMinute, toast, confirmBox, chips, parseNum, header, verdictBadge, shareText } from './ui.js';
import { route, go, back, rerender, session } from './nav.js';
import * as S from './store.js';
import * as N from './nutrition.js';
import { suggestMeal, planText, analyzeMeal, GeminiError, macroLines, macrosOk, macroDistance, macroFixRequest } from './gemini.js';
import { BUILTIN, builtinById, builtinKcal, builtinToSuggestion, totals, judge, scaleIng, recipeText } from './recipes.js';

const SLOT_NAMES = Object.fromEntries(Object.entries(N.SLOTS).map(([k, v]) => [k, v.english]));

// ───── شنو آكل؟ ─────

function planState() {
  if (!session.plan) {
    const pr = S.prefs();
    session.plan = { wish: '', change: '', slot: N.slotForTime(nowMinute(), pr.mealsPerDay || 3), suggestion: null, loading: false, error: null, shown: [], offline: false, saved: false };
  }
  return session.plan;
}

function computeBudget(ps) {
  const p = S.profile();
  const plan = S.currentPlan();
  const meals = S.mealsOn(epochDay());
  const sum = (k) => meals.reduce((a, m) => a + m[k], 0);
  return N.budget(ps.slot, S.prefs().mealsPerDay || 3, plan.dailyTarget, sum('calories'), plan.macros, sum('proteinG'), sum('carbsG'), sum('fatG'));
}

async function requestSuggestion(ps, previous = null, change = null) {
  const pr = S.prefs();
  if (!pr.pantry.trim()) { toast('اكتب شنو موجود بالبيت أول'); return; }
  if (!S.apiKey()) { go('apikey'); return; }
  ps.loading = true; ps.error = null; rerender();
  try {
    const p = S.profile();
    const b = computeBudget(ps);
    const base = {
      budget: b, slotEnglish: N.SLOTS[ps.slot].english, slotNames: SLOT_NAMES, nowMinute: nowMinute(),
      goals: p.goals, medMinute: p.thyroidMedMinute ?? null, pantry: pr.pantry, wish: ps.wish.trim() || null,
      exclude: ps.shown, feedback: S.planFeedback(),
    };
    let r = await suggestMeal(S.apiKey(), planText({ ...base, previous, change }));
    // إذا الوجبة ما راعت البروتين والكارب والدهون، نطلب تصحيح مرة وحدة
    if (r.possible && r.ingredients.length && !macrosOk(r, b)) {
      try {
        const fixed = await suggestMeal(S.apiKey(), planText({ ...base, exclude: [], previous: r, change: macroFixRequest(r, b) }));
        if (fixed.possible && fixed.ingredients.length && macroDistance(fixed, b) < macroDistance(r, b)) r = fixed;
      } catch (_) { /* نبقى على الاقتراح الأول */ }
    }
    ps.suggestion = r; ps.offline = false; ps.saved = false;
    if (r.dishName) ps.shown.push(r.dishName);
    if (change) ps.change = '';
  } catch (e) {
    ps.error = e instanceof GeminiError ? e.message : 'صار خطأ غير متوقع: ' + (e.message || e);
  }
  ps.loading = false;
  rerender();
}

/** اقتراح من الوصفات العراقية والمحفوظة بدون إنترنت. */
function suggestOffline(ps, onlyWish) {
  const b = computeBudget(ps);
  const pr = S.prefs();
  const goals = S.profile().goals;
  const wishKey = N.dishKey(ps.wish);
  const pantryKey = N.dishKey(pr.pantry);
  const disliked = new Set(S.planFeedback().disliked.map(N.dishKey));
  const excluded = new Set(ps.shown.map(N.dishKey));

  let cands = BUILTIN.map((r) => ({
    name: r.name, baseKcal: builtinKcal(r), keys: r.ing.map((i) => i.key).filter(Boolean), fits: r.slots.includes(ps.slot), saved: false,
    make: (f) => builtinToSuggestion(r, goals, pr.pantry.trim() || null, f),
  }));
  for (const sr of S.savedRecipes()) {
    const r = sr.recipe;
    cands = cands.filter((c) => N.dishKey(c.name) !== N.dishKey(r.dishName));
    cands.push({
      name: r.dishName, baseKcal: totals(r).kcal, keys: r.ingredients.map((i) => i.name.split(' ')[0]), fits: true, saved: true,
      make: (f) => ({
        ...r,
        ingredients: r.ingredients.map((i) => {
          const first = N.dishKey(i.name.split(' ')[0]);
          return { ...(f === 1 ? i : scaleIng(i, f)), available: !pantryKey || first.length < 2 || pantryKey.includes(first) };
        }),
        note: [r.note || '', f !== 1 ? 'الكمية معدّلة لتناسب سعرات وجبتك.' : ''].filter(Boolean).join(' '),
      }),
    });
  }
  const wishMatch = (name) => {
    if (wishKey.length < 2) return false;
    const k = N.dishKey(name);
    return k.includes(wishKey) || wishKey.includes(k) || wishKey.split(' ').some((w) => w.length >= 3 && k.includes(w));
  };
  const pool = cands.filter((c) => !excluded.has(N.dishKey(c.name)) && c.baseKcal > 0);
  let picked;
  if (onlyWish) {
    picked = pool.filter((c) => wishMatch(c.name)).sort((a, b) => b.saved - a.saved)[0];
  } else {
    const score = (c) => {
      let s = 0;
      if (wishMatch(c.name)) s += 100;
      if (c.fits) s += 30;
      if (c.saved) s += 5;
      if (disliked.has(N.dishKey(c.name))) s -= 60;
      if (pantryKey && c.keys.length) s += 20 * c.keys.filter((k) => pantryKey.includes(N.dishKey(k))).length / c.keys.length;
      const f = Math.min(1.5, Math.max(0.5, b.kcal / c.baseKcal));
      return s - macroDistance(c.make(f), b) * 25;
    };
    picked = pool.sort((a, b2) => score(b2) - score(a))[0];
  }
  if (!picked) return false;
  const factor = Math.min(1.5, Math.max(0.5, Math.round(b.kcal / picked.baseKcal * 4) / 4));
  const result = picked.make(factor);
  Object.assign(ps, { suggestion: result, offline: true, error: null, saved: picked.saved });
  ps.shown.push(result.dishName);
  return true;
}

function suggest(ps, another = false) {
  if (!another) ps.shown = [];
  if (another && ps.offline) { offlineButton(ps); return; }
  if (ps.wish.trim() && suggestOffline(ps, true)) { rerender(); return; }
  requestSuggestion(ps);
}
function offlineButton(ps) {
  if (!suggestOffline(ps, false)) {
    ps.shown = [];
    if (!suggestOffline(ps, false)) ps.error = 'ما لكيت وصفة محفوظة تناسب هالوجبة.';
  }
  rerender();
}

route('plan', () => {
  const ps = planState();
  const pr = S.prefs();
  const n = pr.mealsPerDay || 3;
  if (!N.slotsFor(n).includes(ps.slot)) ps.slot = N.slotForTime(nowMinute(), n);
  const b = computeBudget(ps);
  const s = ps.suggestion;

  const parts = [
    header('شنو آكل؟', 'وجبة من الموجود بالبيت، على قد سعراتك', () => back(), [
      h('button', { class: 'icon-btn', 'aria-label': 'وصفاتي', onclick: () => go('recipes') }, icon('book')),
      h('button', { class: 'icon-btn', 'aria-label': 'المشتريات', onclick: () => go('shopping') }, icon('cart')),
    ]),
    h('div', { class: 'stack-s' }, h('div', { class: 'small semi' }, 'كم وجبة تاكل باليوم؟'),
      chips([[2, 'وجبتين'], [3, '٣ وجبات'], [4, '٤ وجبات']], n, async (v) => { await S.setPrefs({ mealsPerDay: v }); rerender(); })),
    h('div', { class: 'stack-s' }, h('div', { class: 'small semi' }, 'الوجبة'),
      chips(N.slotsFor(n).map((k) => [k, N.SLOTS[k].label]), ps.slot, (v) => { ps.slot = v; rerender(); })),
    h('div', { class: 'hero stack-s' },
      h('div', { class: 'small muted' }, `سعرات وجبة ال${N.SLOTS[b.slot].label}`),
      h('div', null, h('span', { style: { fontSize: '34px', fontWeight: 800 }, class: 'num' }, b.kcal), h('span', { class: 'small muted' }, ' سعرة تقريباً')),
      h('div', { class: 'small' }, `بروتين ${b.proteinG}غ · كارب ${b.carbsG}غ · دهون ${b.fatG}غ`),
      h('div', { class: 'small muted' }, `باقيلك اليوم ${Math.max(0, b.remainingBefore)} سعرة` + (b.later.length ? ' · بعدها: ' + b.later.map(([k, v]) => `${N.SLOTS[k].label} ~${v}`).join('، ') : '')),
      b.nearlyDone ? h('div', { class: 'small', style: { color: '#FFD27A' } }, 'سعرات اليوم قربت تخلص، فنقترح شي خفيف جداً.') : null),
    h('div', { class: 'field' },
      h('label', { for: 'pantry' }, 'شنو موجود بالبيت؟'),
      h('textarea', { id: 'pantry', class: 'input', placeholder: 'مثلاً: بيض، طماطة، خيار، تمن، دجاج، عدس، خبز', style: { minHeight: '80px' }, oninput: (e) => S.setPrefs({ pantry: e.target.value }) }, pr.pantry),
      h('div', { class: 'chips' }, ['بيض', 'طماطة', 'خيار', 'بصل', 'دجاج', 'تمن', 'عدس', 'خبز', 'لبن', 'جبن'].map((it) =>
        h('button', { class: 'chip', style: { minHeight: '32px', padding: '4px 10px', fontSize: '13px' }, onclick: async () => {
          const items = S.prefs().pantry.split(/[،,]/).map((x) => x.trim()).filter(Boolean);
          if (!items.includes(it)) { await S.setPrefs({ pantry: [...items, it].join('، ') }); rerender(); }
        } }, '+ ' + it)))),
    h('div', { class: 'field' },
      h('label', { for: 'wish' }, 'تريد أكلة معينة؟ (اختياري)'),
      h('input', { id: 'wish', class: 'input', placeholder: 'مثلاً: عجة، شوربة عدس، مقلوبة', value: ps.wish, oninput: (e) => { ps.wish = e.target.value; } })),
    h('button', { class: 'btn', disabled: ps.loading, onclick: () => suggest(ps) }, icon('utensils'), 'اقترح لي وجبة'),
    h('button', { class: 'btn outline', disabled: ps.loading, onclick: () => { ps.shown = []; offlineButton(ps); } }, icon('book'), 'اقترح من الوصفات المحفوظة (بدون إنترنت)'),
  ];

  if (ps.loading) parts.push(h('div', { class: 'card stack' }, h('div', { class: 'row' }, h('div', { class: 'spinner' }), h('span', { class: 'semi' }, 'نجهّز لك وجبة من الموجود…')),
    h('div', { class: 'skeleton', style: { height: '20px', width: '55%' } }), h('div', { class: 'skeleton', style: { height: '14px' } }), h('div', { class: 'skeleton', style: { height: '14px', width: '75%' } })));
  else if (ps.error) parts.push(h('div', { class: 'error small' }, ps.error));
  else if (s) parts.push(...suggestionContent(ps, s, b));

  const bar = s && s.possible && s.ingredients.length && !ps.loading
    ? h('div', { class: 'bottom-bar' }, h('button', { class: 'btn', onclick: async () => {
      await S.addMeal(S.suggestionToAnalysis(s), 'PLAN');
      session.plan = null;
      toast('انضافت للسجل');
      go('home', true);
    } }, icon('check'), 'أكلتها، أضفها للسجل')) : null;
  return h('div', null, h('main', { class: 'screen no-tabs stack' }, ...parts), bar);
});

function ingredientRow(i, actions) {
  return h('div', { class: 'card tight item-row', style: !i.available ? { borderColor: 'var(--warn-border)' } : null },
    h('div', { class: 'grow' },
      h('div', { class: 'semi' }, i.name),
      h('div', { class: 'xs muted' }, [i.amount, i.grams ? `${i.grams} غم` : ''].filter(Boolean).join(' · ')),
      !i.available ? (actions && actions.onShop
        ? h('button', { class: 'xs', style: { color: 'var(--orange-text)', background: 'none', border: 0, padding: 0, textDecoration: 'underline' }, onclick: actions.onShop }, 'تحتاج تشتريه · أضف للمشتريات')
        : h('div', { class: 'xs', style: { color: 'var(--orange-text)' } }, 'تحتاج تشتريه')) : null),
    actions && actions.onScale ? h('div', { class: 'stepper' },
      h('button', { 'aria-label': 'أقل', onclick: () => actions.onScale(0.75) }, '−'),
      h('span', { class: 'num semi', style: { minWidth: '40px', textAlign: 'center' } }, i.calories),
      h('button', { 'aria-label': 'أكثر', onclick: () => actions.onScale(1.25) }, '+'))
      : h('span', { class: 'num semi' }, i.calories),
    actions && actions.onRemove ? h('button', { class: 'icon-btn plain', 'aria-label': 'احذف ' + i.name, onclick: actions.onRemove }, icon('trash')) : null);
}

function summaryCard(s, budgetKcal) {
  const t = totals(s);
  return h('div', { class: 'card stack-s' },
    h('div', { class: 'row top' },
      h('div', { class: 'grow stack-s' }, h('h2', null, s.dishName), h('div', null, verdictBadge(s.verdict))),
      h('div', { class: 'center' }, h('div', { style: { fontSize: '30px', fontWeight: 800, color: 'var(--green)' }, class: 'num' }, t.kcal), h('div', { class: 'xs muted' }, budgetKcal ? `من ${budgetKcal}` : 'سعرة'))),
    s.summary ? h('p', { class: 'small muted' }, s.summary) : null,
    h('div', { class: 'small' }, `بروتين ${Math.round(t.p)}غ · كارب ${Math.round(t.c)}غ · دهون ${Math.round(t.f)}غ`),
    s.prepMinutes > 0 ? h('div', { class: 'row small muted', style: { gap: '6px' } }, icon('timer', { size: 16 }), `التحضير حوالي ${s.prepMinutes} دقيقة`) : null,
    s.note ? h('div', { class: 'soft small' }, s.note) : null);
}

function stepsList(s) {
  if (!s.steps || !s.steps.length) return null;
  return h('div', { class: 'stack-s' }, h('h3', null, 'طريقة التحضير'),
    s.steps.map((st, n) => h('div', { class: 'row top' }, h('span', { class: 'step-num' }, n + 1), h('div', { class: 'grow small', style: { lineHeight: 1.7 } }, st))));
}

async function addMissing(s) {
  let added = 0;
  for (const i of s.ingredients.filter((x) => !x.available)) if (await S.addShopping(i.name, i.amount)) added++;
  toast(added ? `انضاف ${added} للمشتريات` : 'الناقص موجود أصلاً بالقائمة');
}

function suggestionContent(ps, s, b) {
  if (!s.possible) return [h('div', { class: 'warn small' }, s.note || 'ما نكدر نسوي وجبة مناسبة من هذي المكونات. ضيف شي ثاني وجرب.')];
  const out = [];
  if (ps.offline) out.push(h('div', null, h('span', { class: 'badge GOOD' }, 'من الوصفات المحفوظة، بدون إنترنت')));
  out.push(summaryCard(s, b.kcal));
  out.push(h('div', { class: 'card tight stack-s' }, macroLines(s, b).map((l) => h('div', { class: 'row small' },
    h('span', { class: 'grow muted' }, l.label),
    h('span', { class: 'semi', style: { color: l.ok ? 'var(--green-dark)' : 'var(--orange-text)' } }, `${l.value} من ${l.target}غ` + (l.ok ? '  ✓' : `  · ${l.hint}`))))));
  out.push(h('h3', null, 'المقادير'));
  s.ingredients.forEach((i, idx) => out.push(ingredientRow(i, {
    onScale: (f) => { const n = scaleIng(i, f); if (n.calories <= 0 && f < 1) return; s.ingredients[idx] = n; rerender(); },
    onRemove: () => { s.ingredients.splice(idx, 1); rerender(); },
    onShop: async () => toast(await S.addShopping(i.name, i.amount) ? `انضاف «${i.name}» للمشتريات` : `«${i.name}» موجود أصلاً بالقائمة`),
  })));
  out.push(stepsList(s));
  out.push(h('div', { class: 'row' },
    h('button', { class: 'btn outline small grow', disabled: ps.saved, onclick: async () => { await S.saveRecipe(s, 'AI'); ps.saved = true; toast('انحفظت بوصفاتي'); rerender(); } }, icon('bookmark', { fill: ps.saved }), ps.saved ? 'محفوظة' : 'احفظ الوصفة'),
    h('button', { class: 'btn outline small grow', onclick: () => shareText(recipeText(s)) }, icon('share'), 'شارك')));
  if (s.ingredients.some((i) => !i.available)) out.push(h('button', { class: 'btn outline small', onclick: () => addMissing(s) }, icon('cart'), 'أضف الناقص لقائمة المشتريات'));
  out.push(h('div', { class: 'field' },
    h('label', { for: 'change' }, 'تريد تغيّر شي؟'),
    h('input', { id: 'change', class: 'input', placeholder: 'مثلاً: بدون خبز، أو بدّل الدجاج ببيض', value: ps.change, oninput: (e) => { ps.change = e.target.value; } })));
  out.push(h('div', { class: 'row' },
    h('button', { class: 'btn outline small grow', onclick: () => { if (ps.change.trim()) requestSuggestion(ps, s, ps.change.trim()); else toast('اكتب شنو تريد تغيّر'); } }, icon('edit'), 'عدّل'),
    h('button', { class: 'btn outline small grow', onclick: () => suggest(ps, true) }, icon('refresh'), 'وجبة ثانية')));
  return out;
}

// ───── الوصفات ─────

function recipeCard(title, kcal, prep, tag, onclick) {
  return h('button', { class: 'card tight row', style: { width: '100%', textAlign: 'start' }, onclick },
    h('div', { class: 'thumb', style: { background: 'var(--green-soft)', color: 'var(--green)', width: '44px', height: '44px', borderRadius: '12px' } }, icon('book')),
    h('div', { class: 'grow' }, h('div', { class: 'semi' }, title), h('div', { class: 'xs muted' }, [tag, prep > 0 ? `${prep} دقيقة` : ''].filter(Boolean).join(' · ') || ' ')),
    h('div', { class: 'center' }, h('div', { class: 'bold num', style: { color: 'var(--green)' } }, kcal), h('div', { class: 'xs muted' }, 'سعرة')));
}

route('recipes', () => {
  const saved = S.savedRecipes();
  return h('main', { class: 'screen no-tabs stack' },
    header('وصفاتي', 'تنفتح بدون إنترنت وبدون Gemini', () => back(), [
      h('button', { class: 'icon-btn green', 'aria-label': 'أضف وصفة', onclick: () => go('edit') }, icon('plus')),
    ]),
    h('h3', null, 'المحفوظة'),
    saved.length ? saved.map((r) => recipeCard(r.recipe.dishName, totals(r.recipe).kcal, r.recipe.prepMinutes, r.source === 'USER' ? 'وصفتك' : null, () => go('recipe/' + r.id)))
      : h('div', { class: 'small muted' }, 'ما عندك وصفات محفوظة بعد. اضغط + حتى تكتب وصفتك، أو من «شنو آكل؟» اضغط «احفظ الوصفة» على أي اقتراح يعجبك.'),
    h('h3', { style: { marginTop: '6px' } }, 'وصفات عراقية'),
    h('div', { class: 'small muted' }, 'لحصة وحدة بكميات خفيفة. السعرات تقريبية.'),
    BUILTIN.map((r) => recipeCard(r.name, builtinKcal(r), r.prep, r.slots.map((k) => N.SLOTS[k].label).join('، '), () => go('builtin/' + r.id))),
  );
});

function recipeDetail(source, recipeObj, opts) {
  const key = 'portion-' + opts.key;
  const portion = session[key] || 1;
  const s = { ...recipeObj, ingredients: recipeObj.ingredients.map((i) => scaleIng(i, portion)) };
  const missing = s.ingredients.some((i) => !i.available);
  return h('div', null,
    h('main', { class: 'screen no-tabs stack' },
      header('الوصفة', source === 'BUILTIN' ? 'وصفة عراقية · التعديل يحفظ نسخة إلك' : null, () => back('recipes'), [
        h('button', { class: 'icon-btn plain', 'aria-label': 'عدّل الوصفة', onclick: opts.onEdit }, icon('edit')),
        opts.onDelete ? h('button', { class: 'icon-btn plain', 'aria-label': 'احذف الوصفة', onclick: () => confirmBox('حذف الوصفة؟', 'حذف', opts.onDelete) }, icon('trash')) : null,
      ]),
      h('div', { class: 'small semi' }, 'الكمية'),
      chips([[0.5, 'نص'], [1, 'حصة'], [1.5, 'حصة ونص'], [2, 'حصتين'], [4, '٤ حصص']], portion, (v) => { session[key] = v; rerender(); }),
      summaryCard(s),
      h('h3', null, 'المقادير'),
      s.ingredients.map((i) => ingredientRow(i, null)),
      stepsList(s),
      h('div', { class: 'row' },
        h('button', { class: 'btn outline small grow', onclick: () => shareText(recipeText(s)) }, icon('share'), 'شارك بواتساب'),
        h('button', { class: 'btn outline small grow', onclick: async () => {
          if (missing) { await addMissing(s); return; }
          let added = 0;
          for (const i of s.ingredients) if (await S.addShopping(i.name, i.amount)) added++;
          toast(added ? `انضاف ${added} للمشتريات` : 'كل المكونات موجودة بالقائمة');
        } }, icon('cart'), missing ? 'الناقص للمشتريات' : 'للمشتريات'))),
    h('div', { class: 'bottom-bar' }, h('button', { class: 'btn', onclick: async () => {
      await S.addMeal(S.suggestionToAnalysis(s), 'PLAN');
      toast('انضافت للسجل');
      go('home', true);
    } }, icon('check'), 'أكلتها، أضفها للسجل')));
}

route('recipe', (r) => {
  const id = Number(r.arg);
  const rec = S.getRecipe(id);
  if (!rec) { setTimeout(() => go('recipes', true)); return h('div'); }
  return recipeDetail(rec.source, rec.recipe, {
    key: 'r' + id,
    onEdit: () => go('edit?id=' + id),
    onDelete: async () => { await S.deleteRecipe(id); back('recipes'); },
  });
});

route('builtin', (r) => {
  const b = builtinById(r.arg);
  if (!b) { setTimeout(() => go('recipes', true)); return h('div'); }
  const pantry = S.prefs().pantry.trim() || null;
  return recipeDetail('BUILTIN', builtinToSuggestion(b, S.profile().goals, pantry), { key: 'b' + b.id, onEdit: () => go('edit?bid=' + b.id) });
});

// ───── إضافة وتعديل وصفة ─────

route('edit', (r) => {
  const id = Number(r.params.id || 0);
  const bid = r.params.bid || '';
  const k = `edit-${id}-${bid}`;
  if (!session[k]) {
    const src = bid ? builtinToSuggestion(builtinById(bid), []) : id ? (S.getRecipe(id) || {}).recipe : null;
    session[k] = {
      title: src ? src.dishName : '', prep: src && src.prepMinutes ? String(src.prepMinutes) : '',
      steps: src ? src.steps.join('\n') : '',
      rows: src ? src.ingredients.map((i) => ({ name: i.name, amount: i.amount, kcal: String(i.calories), grams: i.grams, p: i.proteinG, c: i.carbsG, f: i.fatG, available: i.available }))
        : [0, 1, 2].map(() => ({ name: '', amount: '', kcal: '', grams: 0, p: 0, c: 0, f: 0, available: true })),
      loading: false, message: null,
    };
  }
  const e = session[k];
  const totalEl = h('span', { class: 'small semi', style: { color: 'var(--green)' } });
  const updTotal = () => { totalEl.textContent = `المجموع ${e.rows.reduce((a, x) => a + (parseNum(x.kcal) || 0), 0)} سعرة`; };
  updTotal();
  const isNew = !id && !bid;

  const calc = async () => {
    const list = e.rows.filter((x) => x.name.trim());
    if (!list.length || e.loading) return false;
    if (!S.apiKey()) { go('apikey'); return; }
    e.loading = true; e.message = null; rerender();
    try {
      const p = S.profile();
      const desc = `وصفة «${e.title || 'وجبة'}» لحصة وحدة. احسب كل مكوّن كسطر مستقل بنفس الاسم والكمية:\n` + list.map((x) => `- ${x.name}${x.amount ? ` (${x.amount})` : ''}`).join('\n');
      const a = await analyzeMeal(S.apiKey(), { goals: p.goals, dailyTarget: 2000, remaining: 2000, nowMinute: nowMinute(), medMinute: null }, { description: desc });
      if (!a.items.length) e.message = 'ما كدرت أحسبها، جرّب تكتب الكميات أوضح.';
      else {
        e.rows = a.items.map((it, n) => ({ name: it.name, amount: it.portion || (list[n] ? list[n].amount : ''), kcal: String(it.calories), grams: it.grams, p: it.proteinG, c: it.carbsG, f: it.fatG, available: list[n] ? list[n].available : true }));
        e.message = `انحسبت السعرات: ${e.rows.reduce((s2, x) => s2 + (parseNum(x.kcal) || 0), 0)} سعرة للحصة`;
        e.dirty = false;
        e.loading = false; rerender();
        return true;
      }
    } catch (err) {
      e.message = err instanceof GeminiError ? err.message : 'صار خطأ: ' + (err.message || err);
    }
    e.loading = false; rerender();
    return false;
  };

  const save = async () => {
    if (!e.title.trim() || !e.rows.some((x) => x.name.trim())) { toast('اكتب اسم الأكلة ومكوّن واحد على الأقل'); return; }
    // السعرات تنحسب تلقائياً إذا ناقصة أو تغيّرت المكونات
    if (e.dirty || e.rows.some((x) => x.name.trim() && !((parseNum(x.kcal) || 0) > 0))) {
      if (!(await calc())) return;
    }
    const rows = e.rows.filter((x) => x.name.trim());
    const goals = S.profile().goals;
    const base = {
      possible: true, dishName: e.title.trim(),
      ingredients: rows.map((x) => ({ name: x.name.trim(), amount: x.amount.trim(), grams: x.grams || 0, calories: Math.round(parseNum(x.kcal) || 0), proteinG: x.p || 0, carbsG: x.c || 0, fatG: x.f || 0, available: x.available !== false })),
      steps: e.steps.split('\n').map((x) => x.trim().replace(/^[-•*]\s*/, '')).filter(Boolean),
      prepMinutes: Math.round(parseNum(e.prep) || 0), verdict: 'GOOD', summary: '', note: '',
    };
    const hasMacros = base.ingredients.some((i) => i.proteinG + i.carbsG + i.fatG > 0);
    if (hasMacros) [base.verdict, base.summary] = judge(base, goals);
    const newId = await S.saveRecipe(base, 'USER', bid ? 0 : id);
    session[k] = null;
    toast('انحفظت الوصفة');
    if (id) back('recipes'); else go('recipe/' + newId, true);
  };

  const rowsEls = e.rows.map((x, i) => h('div', { class: 'card tight stack-s' },
    h('div', { class: 'row' },
      h('input', { class: 'input grow', placeholder: 'المكوّن (مثلاً: عدس)', value: x.name, oninput: (ev) => { x.name = ev.target.value; x.kcal = ''; e.dirty = true; } }),
      h('button', { class: 'icon-btn plain', 'aria-label': 'احذف المكوّن', onclick: () => { e.rows.splice(i, 1); rerender(); } }, icon('trash'))),
    h('div', { class: 'row' },
      h('input', { class: 'input grow', placeholder: 'الكمية (نص كوب)', value: x.amount, oninput: (ev) => { x.amount = ev.target.value; x.kcal = ''; e.dirty = true; } }),
      h('span', { class: 'semi small', style: { color: 'var(--green)', minWidth: '80px', textAlign: 'center' } }, (parseNum(x.kcal) || 0) > 0 ? `${x.kcal} سعرة` : '—'))));

  return h('div', null,
    h('main', { class: 'screen no-tabs stack' },
      header(isNew ? 'وصفة جديدة' : 'تعديل الوصفة', 'المقادير لحصة وحدة', () => { session[k] = null; back('recipes'); }),
      h('div', { class: 'field' }, h('label', { for: 'r-title' }, 'اسم الأكلة'), h('input', { id: 'r-title', class: 'input', value: e.title, oninput: (ev) => { e.title = ev.target.value; } })),
      h('div', { class: 'field' }, h('label', { for: 'r-prep' }, 'وقت التحضير بالدقايق (اختياري)'), h('input', { id: 'r-prep', class: 'input', inputmode: 'numeric', value: e.prep, oninput: (ev) => { e.prep = ev.target.value; } })),
      h('div', { class: 'row' }, h('h3', { class: 'grow' }, 'المقادير'), totalEl),
      rowsEls,
      h('button', { class: 'btn ghost', style: { color: 'var(--green)', alignSelf: 'flex-start' }, onclick: () => { e.rows.push({ name: '', amount: '', kcal: '', grams: 0, p: 0, c: 0, f: 0, available: true }); rerender(); } }, icon('plus'), 'أضف مكوّن'),
      h('button', { class: 'btn outline', disabled: e.loading, onclick: calc }, e.loading ? h('div', { class: 'spinner' }) : icon('sparkle'), e.loading ? 'نحسب…' : 'احسب السعرات بـ Gemini'),
      h('div', { class: 'hint' }, 'السعرات تنحسب تلقائياً من تحفظ، أو اضغط الزر حتى تشوفها قبل.'),
      e.message ? h('div', { class: 'soft small' }, e.message) : null,
      h('h3', null, 'طريقة التحضير'),
      h('textarea', { class: 'input', placeholder: 'كل خطوة بسطر', oninput: (ev) => { e.steps = ev.target.value; } }, e.steps)),
    h('div', { class: 'bottom-bar' }, h('button', { class: 'btn', onclick: save }, 'احفظ الوصفة')));
});

// ───── المشتريات ─────

route('shopping', () => {
  const items = S.shopping();
  const open = items.filter((i) => !i.done);
  let input = '';
  const add = async () => {
    const parts = input.split(/[،,]/).map((x) => x.trim()).filter(Boolean);
    for (const p of parts) await S.addShopping(p);
    rerender();
    setTimeout(() => { const el = document.getElementById('shopIn'); if (el) el.focus(); }, 0);
  };
  const shareList = () => shareText(['🛒 قائمة المشتريات', ...open.map((i) => `• ${i.name}${i.amount ? ` (${i.amount})` : ''}`), 'من تطبيق «شد الهمة»'].join('\n'));
  return h('main', { class: 'screen no-tabs stack' },
    header('قائمة المشتريات', open.length ? `${open.length} أشياء تحتاج تشتريها` : null, () => back(),
      open.length ? [h('button', { class: 'icon-btn', 'aria-label': 'شارك القائمة', style: { color: 'var(--green)' }, onclick: shareList }, icon('share'))] : []),
    h('div', { class: 'row' },
      h('input', { id: 'shopIn', class: 'input grow', placeholder: 'أضف شي (مثلاً: طماطة، خيار)', enterkeyhint: 'done', oninput: (e) => { input = e.target.value; }, onkeydown: (e) => { if (e.key === 'Enter') add(); } }),
      h('button', { class: 'icon-btn green', style: { borderRadius: '14px', width: '50px', height: '50px' }, 'aria-label': 'أضف', onclick: add }, icon('plus'))),
    items.length ? items.map((it) => h('div', { class: 'card tight row' },
      h('label', { class: 'check-row grow' },
        h('input', { type: 'checkbox', checked: it.done, onchange: async (e) => { await S.setShoppingDone(it, e.target.checked); rerender(); } }),
        h('div', { class: 'grow' }, h('div', { class: 'semi' + (it.done ? ' done' : '') }, it.name), it.amount ? h('div', { class: 'xs muted' }, it.amount) : null)),
      h('button', { class: 'icon-btn plain', 'aria-label': 'حذف ' + it.name, onclick: async () => { await S.deleteShopping(it.id); rerender(); } }, icon('trash'))))
      : h('div', { class: 'small muted' }, 'القائمة فاضية. المكونات الناقصة من «شنو آكل؟» تنضاف هنا بضغطة.'),
    items.some((i) => i.done) ? h('button', { class: 'btn ghost', style: { alignSelf: 'flex-start' }, onclick: async () => { await S.clearBought(); rerender(); } }, 'امسح اللي اشتريته') : null,
  );
});
