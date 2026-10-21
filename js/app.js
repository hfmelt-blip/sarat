// نقطة البداية: الشاشة الرئيسية وتسجيل الوجبات.

import { init, data, getKv } from './db.js';
import { h, icon, epochDay, nowMinute, timeLabel, toast, modal, confirmBox, verdictBadge, chips, parseNum, fmt1 } from './ui.js';
import { route, go, back, render, rerender, session, tabScreen } from './nav.js';
import * as S from './store.js';
import * as N from './nutrition.js';
import { analyzeMeal, GeminiError } from './gemini.js';
import { QUICK_ITEMS } from './recipes.js';
import './screens.js';
import './kitchen.js';
import './meals.js';

// ───── الصور ─────

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => resolve({ img, url });
    img.onerror = () => reject(new Error('ما كدرنا نفتح الصورة'));
    img.src = url;
  });
}
function toJpeg(img, max, quality) {
  const scale = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight));
  const c = document.createElement('canvas');
  c.width = Math.round(img.naturalWidth * scale);
  c.height = Math.round(img.naturalHeight * scale);
  c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
  return c.toDataURL('image/jpeg', quality);
}
function squareThumb(img, size) {
  const c = document.createElement('canvas');
  c.width = c.height = size;
  const s = Math.min(img.naturalWidth, img.naturalHeight);
  c.getContext('2d').drawImage(img, (img.naturalWidth - s) / 2, (img.naturalHeight - s) / 2, s, s, 0, 0, size, size);
  return c.toDataURL('image/jpeg', 0.7);
}

export function pickPhoto(capture = true) {
  const input = h('input', { type: 'file', accept: 'image/*', style: { display: 'none' } });
  if (capture) input.setAttribute('capture', 'environment');
  input.onchange = async () => {
    const file = input.files && input.files[0];
    input.remove();
    if (!file) return;
    try {
      const { img, url } = await loadImage(file);
      const full = toJpeg(img, 960, 0.75);
      session.meal = { mode: 'photo', image: full, thumb: squareThumb(img, 160), state: 'loading' };
      URL.revokeObjectURL(url);
      go('meal');
      runAnalysis();
    } catch (e) {
      toast(e.message);
    }
  };
  document.body.appendChild(input);
  input.click();
}

// ───── تحليل الوجبة ─────

function mealContext() {
  const p = S.profile();
  const plan = S.currentPlan();
  const eaten = S.mealsOn(epochDay()).reduce((a, m) => a + m.calories, 0);
  return { goals: p.goals, dailyTarget: plan.dailyTarget, remaining: plan.dailyTarget - eaten, nowMinute: nowMinute(), medMinute: p.thyroidMedMinute ?? null };
}

async function runAnalysis() {
  const m = session.meal;
  m.state = 'loading'; m.error = null;
  rerender();
  try {
    const a = await analyzeMeal(S.apiKey(), mealContext(), m.mode === 'photo' ? { imageBase64: m.image.split(',')[1] } : { description: m.text });
    if (session.meal !== m) return;
    if (a.isFood === false || !a.items.length) {
      m.state = 'error';
      m.error = m.mode === 'photo' ? 'ما لكينا أكل بالصورة. جرّب صورة أوضح للصحن.' : 'ما فهمنا الوجبة. اكتبها بتفصيل أكثر.';
    } else {
      m.state = 'result';
      m.analysis = a;
      m.base = a.items.map((i) => ({ ...i }));
      m.portion = 1;
      m.originalName = a.mealName;
      m.edited = false;
      m.correction = S.getCorrection(a.mealName);
      m.correctionApplied = false;
    }
  } catch (e) {
    if (session.meal !== m) return;
    m.state = 'error';
    m.error = e instanceof GeminiError ? e.message : 'صار خطأ غير متوقع: ' + (e.message || e);
  }
  rerender();
}

const scale = S.scaleIng;
const scaleFood = (i, f) => ({ ...i, grams: Math.round(i.grams * f), calories: Math.round(i.calories * f), proteinG: i.proteinG * f, carbsG: i.carbsG * f, fatG: i.fatG * f });

function setPortion(m, p) {
  const f = p / m.portion;
  m.analysis.items = m.analysis.items.map((i) => scaleFood(i, f));
  m.portion = p;
  rerender();
}

route('meal', () => {
  const m = session.meal;
  if (!m) { setTimeout(() => go('home', true)); return h('div'); }
  const title = m.mode === 'photo' ? 'تحليل الصورة' : 'اكتب وجبتك';
  const body = [];
  body.push(h('div', { class: 'header' },
    h('button', { class: 'icon-btn', 'aria-label': 'رجوع', onclick: () => { session.meal = null; back(); } }, icon('back')),
    h('div', { class: 'grow' }, h('h2', null, title))));

  if (m.mode === 'photo' && m.image) body.push(h('img', { class: 'preview-img', src: m.image, alt: 'صورة الوجبة' }));

  if (m.mode === 'text' && (m.state === 'input' || m.state === 'error')) {
    body.push(h('div', { class: 'field' },
      h('label', { for: 'mealText' }, 'شنو أكلت؟'),
      h('textarea', { id: 'mealText', class: 'input', placeholder: 'مثلاً: صحن تمن ومرقة فاصوليا ونص صمونة', oninput: (e) => { m.text = e.target.value; } }, m.text || ''),
      h('div', { class: 'hint' }, 'اكتب الكميات إذا تعرفها، يصير الحساب أدق.')));
    body.push(h('button', { class: 'btn', onclick: () => { if ((m.text || '').trim()) runAnalysis(); else toast('اكتب شنو أكلت أول'); } }, icon('sparkle'), 'احسب السعرات'));
  }

  if (m.state === 'loading') {
    body.push(h('div', { class: 'card stack' },
      h('div', { class: 'row' }, h('div', { class: 'spinner' }), h('div', { class: 'semi' }, m.mode === 'photo' ? 'نحلل الصورة…' : 'نحسب السعرات…')),
      h('div', { class: 'skeleton', style: { height: '22px', width: '60%' } }),
      h('div', { class: 'skeleton', style: { height: '16px' } }),
      h('div', { class: 'skeleton', style: { height: '16px', width: '80%' } }),
      h('div', { class: 'hint' }, 'ممكن ياخذ لحد دقيقة.')));
  }
  if (m.state === 'error') {
    body.push(h('div', { class: 'error' }, m.error));
    if (m.mode === 'photo') body.push(h('div', { class: 'row' },
      h('button', { class: 'btn outline grow', onclick: runAnalysis }, icon('refresh'), 'حاول مرة ثانية'),
      h('button', { class: 'btn outline grow', onclick: () => pickPhoto() }, icon('camera'), 'صورة ثانية')));
  }
  if (m.state === 'result') body.push(...resultContent(m));

  const main = h('main', { class: 'screen no-tabs stack' }, ...body);
  const bar = m.state === 'result' ? h('div', { class: 'bottom-bar' }, h('button', { class: 'btn', onclick: () => saveMeal(m) }, icon('check'), 'سجّل الوجبة')) : null;
  return h('div', null, main, bar);
});

function resultContent(m) {
  const a = m.analysis;
  const total = a.items.reduce((s, i) => s + i.calories, 0);
  const t = (k) => Math.round(a.items.reduce((s, i) => s + i[k], 0));
  const out = [];
  out.push(h('div', { class: 'small semi muted' }, 'راجع قبل التسجيل'));
  out.push(h('div', { class: 'card stack' },
    h('div', { class: 'row top' },
      h('div', { class: 'grow stack-s' },
        h('input', { id: 'mealName', class: 'input', value: a.mealName, 'aria-label': 'اسم الأكلة', style: { fontWeight: 700, fontSize: '18px' }, oninput: (e) => { a.mealName = e.target.value; } }),
        h('div', null, verdictBadge(a.verdict))),
      h('div', { class: 'center' }, h('div', { style: { fontSize: '32px', fontWeight: 800, color: 'var(--green)' }, class: 'num' }, total), h('div', { class: 'xs muted' }, 'سعرة'))),
    h('div', { class: 'small' }, `بروتين ${t('proteinG')}غ · كارب ${t('carbsG')}غ · دهون ${t('fatG')}غ`),
    a.verdictReason ? h('p', { class: 'small muted' }, a.verdictReason) : null,
    a.suggestion ? h('div', { class: 'soft small' }, a.suggestion) : null,
    a.medicationNote ? h('div', { class: 'info small' }, a.medicationNote) : null));

  if (m.correction && !m.correctionApplied) {
    const kcal = Math.round(m.correction.items.reduce((s, i) => s + i.calories, 0) * m.portion);
    out.push(h('div', { class: 'info stack-s' },
      h('div', { class: 'small' }, `صلّحت هالأكلة قبل. تقديرك السابق: ${kcal} سعرة.`),
      h('button', { class: 'btn small', style: { width: 'auto', alignSelf: 'flex-start' }, onclick: () => {
        a.items = m.correction.items.map((i) => scaleFood(i, m.portion));
        m.correctionApplied = true; m.edited = true; rerender();
      } }, 'استخدم تقديري السابق')));
  }

  out.push(h('div', { class: 'stack-s' }, h('div', { class: 'small semi' }, 'الكمية'),
    chips([[0.5, 'نص'], [1, 'حصة'], [1.5, 'حصة ونص'], [2, 'حصتين'], [3, '٣ حصص']], m.portion, (p) => setPortion(m, p))));

  out.push(h('h3', null, 'المكونات'));
  a.items.forEach((it, idx) => {
    out.push(h('div', { class: 'card tight item-row' },
      h('div', { class: 'grow' },
        h('div', { class: 'semi' }, it.name),
        h('div', { class: 'xs muted' }, [it.portion, it.grams ? `${it.grams} غم` : ''].filter(Boolean).join(' · '))),
      h('div', { class: 'stepper' },
        h('button', { 'aria-label': 'أقل', onclick: () => { if (it.calories <= 5) return; a.items[idx] = scaleFood(it, 0.75); m.edited = true; rerender(); } }, '−'),
        h('span', { class: 'num semi', style: { minWidth: '44px', textAlign: 'center' } }, it.calories),
        h('button', { 'aria-label': 'أكثر', onclick: () => { a.items[idx] = scaleFood(it, 1.25); m.edited = true; rerender(); } }, '+')),
      h('button', { class: 'icon-btn plain', 'aria-label': 'احذف ' + it.name, onclick: () => { a.items.splice(idx, 1); m.edited = true; rerender(); } }, icon('trash'))));
  });
  out.push(h('button', { class: 'btn outline', onclick: () => addItemDialog(m) }, icon('plus'), 'أضف مكوّن ناقص (زيت، سكر، خبز…)'));
  return out;
}

function addItemDialog(m) {
  modal((box, close) => {
    const add = (item) => { m.analysis.items.push({ ...item }); m.edited = true; close(); rerender(); };
    let name = '', amount = '';
    const status = h('div');
    const btn = h('button', { class: 'btn' }, 'احسب وأضف');
    btn.onclick = async () => {
      if (!name.trim()) { toast('اكتب شنو تريد تضيف'); return; }
      const label = [name.trim(), amount.trim()].filter(Boolean).join(' ');
      btn.disabled = true;
      status.replaceChildren(h('div', { class: 'row small' }, h('div', { class: 'spinner' }), `نحسب سعرات «${label}»…`));
      try {
        const a = await analyzeMeal(S.apiKey(), mealContext(), { description: `مكوّن واحد فقط يضاف لوجبة: ${label}. احسبه كسطر واحد.` });
        if (!a.items.length) throw new GeminiError(`ما كدرنا نحسب «${label}». اكتبه بشكل أوضح.`);
        const sum = (k) => a.items.reduce((x, i) => x + (i[k] || 0), 0);
        add({ name: name.trim(), portion: amount.trim() || a.items[0].portion, grams: sum('grams'), calories: Math.round(sum('calories')), proteinG: sum('proteinG'), carbsG: sum('carbsG'), fatG: sum('fatG'), fit: a.items[0].fit });
      } catch (e) {
        btn.disabled = false;
        status.replaceChildren(h('div', { class: 'error small' }, e instanceof GeminiError ? e.message : `ما كدرنا نحسب «${label}». حاول مرة ثانية.`));
      }
    };
    box.append(h('div', { class: 'stack' },
      h('h3', null, 'أضف مكوّن'),
      h('div', { class: 'chips' }, QUICK_ITEMS.map((q) => h('button', { class: 'chip', onclick: () => add(q) }, `${q.name} · ${q.calories}`))),
      h('hr', { class: 'sep' }),
      h('div', { class: 'small semi' }, 'أو اكتبه بنفسك'),
      h('input', { class: 'input', placeholder: 'شنو تريد تضيف؟ (مثلاً: نص صمونة)', oninput: (e) => { name = e.target.value; } }),
      h('input', { class: 'input', placeholder: 'الكمية (اختياري)', oninput: (e) => { amount = e.target.value; } }),
      h('div', { class: 'hint' }, 'التطبيق يحسب سعراتها تلقائياً.'),
      status, btn));
  });
}

async function saveMeal(m) {
  const a = m.analysis;
  if (!a.items.length) { toast('ما بقى مكونات'); return; }
  a.mealName = (a.mealName || '').trim() || m.originalName || 'وجبة';
  if (m.edited) {
    const perServing = a.items.map((i) => scaleFood(i, 1 / m.portion));
    await S.saveCorrection(m.originalName, perServing);
    if (N.dishKey(a.mealName) !== N.dishKey(m.originalName)) await S.saveCorrection(a.mealName, perServing);
  }
  await S.addMeal(a, m.mode === 'photo' ? 'PHOTO' : 'TEXT', { thumb: m.thumb });
  session.meal = null;
  toast('انسجلت الوجبة');
  go('home', true);
}

// ───── الرئيسية ─────

function ring(consumed, target) {
  const r = 64, c = 2 * Math.PI * r;
  const frac = Math.min(1, consumed / Math.max(1, target));
  const over = consumed > target * 1.05;
  const color = over ? 'var(--orange)' : '#5BC79A';
  const wrap = h('div', { class: 'ring-wrap' });
  wrap.innerHTML = `<svg viewBox="0 0 150 150"><circle cx="75" cy="75" r="${r}" fill="none" stroke="rgba(255,255,255,.14)" stroke-width="12"/><circle cx="75" cy="75" r="${r}" fill="none" stroke="${color}" stroke-width="12" stroke-linecap="round" stroke-dasharray="${c}" stroke-dashoffset="${c * (1 - frac)}"/></svg>`;
  const remaining = target - consumed;
  wrap.append(h('div', { class: 'ring-label' },
    h('div', { style: { fontSize: '30px', fontWeight: 800 }, class: 'num' }, Math.abs(remaining)),
    h('div', { class: 'xs muted' }, remaining >= 0 ? 'سعرة باقية' : 'سعرة زيادة')));
  return wrap;
}

function macroBar(label, val, target) {
  return h('div', { class: 'stack-s', style: { gap: '4px' } },
    h('div', { class: 'row xs' }, h('span', { class: 'grow muted' }, label), h('span', { class: 'num' }, `${Math.round(val)}/${target}غ`)),
    h('div', { class: 'bar' }, h('i', { style: { width: Math.min(100, val / Math.max(1, target) * 100) + '%' } })));
}

function isIos() { return /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1); }
function isStandalone() { return window.navigator.standalone === true || matchMedia('(display-mode: standalone)').matches; }

export function installGuide() {
  modal((box, close) => box.append(h('div', { class: 'stack' },
    h('h3', null, 'ضيف التطبيق للشاشة الرئيسية'),
    h('div', { class: 'stack-s small' },
      h('div', { class: 'row' }, h('span', { class: 'step-num' }, '1'), h('span', null, 'افتح الرابط بـ Safari (مو من داخل التلكرام).')),
      h('div', { class: 'row' }, h('span', { class: 'step-num' }, '2'), h('span', null, 'اضغط زر المشاركة '), icon('iosShare', { size: 20 }), h('span', null, ' جوه الشاشة.')),
      h('div', { class: 'row' }, h('span', { class: 'step-num' }, '3'), h('span', null, 'اختار «إضافة إلى الشاشة الرئيسية» ثم «إضافة».')),
      h('div', { class: 'row' }, h('span', { class: 'step-num' }, '4'), h('span', null, 'افتح «شد الهمة» من الأيقونة الجديدة، وبياناتك تنحفظ بيها.'))),
    h('div', { class: 'hint' }, 'بالتلكرام: اضغط على الرابط مطولاً واختار «فتح في Safari».'),
    h('button', { class: 'btn', onclick: close }, 'تمام'))));
}

function mealRow(m, { onDelete, showRate = true } = {}) {
  const thumb = m.thumb ? h('img', { class: 'thumb', src: m.thumb, alt: '' }) : h('div', { class: 'thumb' }, icon(m.source === 'PLAN' ? 'utensils' : 'note'));
  const row = h('div', { class: 'card tight stack-s' },
    h('div', { class: 'meal' },
      thumb,
      h('div', { class: 'grow' },
        h('div', { class: 'semi ellipsis' }, m.title),
        h('div', { class: 'xs muted' }, timeLabel(m.minute) + (m.source === 'PHOTO' ? ' · بالكاميرا' : m.source === 'PLAN' ? ' · وجبة مقترحة' : ' · بالكتابة'))),
      h('div', { class: 'stack-s', style: { alignItems: 'flex-end', gap: '4px' } }, h('span', { class: 'kcal num' }, m.calories), verdictBadge(m.verdict)),
      h('div', { class: 'stack-s', style: { gap: 0 } },
        h('button', { class: 'icon-btn plain', 'aria-label': m.favorite ? 'إلغاء من المفضلة' : 'أضف للمفضلة', style: m.favorite ? { color: 'var(--star)' } : null, onclick: async () => { await S.toggleFavorite(m); rerender(); } }, icon('star', { fill: m.favorite })),
        h('button', { class: 'icon-btn plain', 'aria-label': 'حذف ' + m.title, onclick: () => confirmBox('تحذف هالوجبة؟', 'حذف', async () => { await S.deleteMeal(m.id); (onDelete || rerender)(); }) }, icon('trash')))));
  if (showRate && m.source === 'PLAN') {
    const pair = (label, value, set) => h('div', { class: 'rate' }, label,
      h('button', { class: value === true ? 'up' : '', 'aria-label': label + ' نعم', onclick: () => set(value === true ? null : true) }, icon('thumbUp', { fill: value === true })),
      h('button', { class: value === false ? 'down' : '', 'aria-label': label + ' لا', onclick: () => set(value === false ? null : false) }, icon('thumbDown', { fill: value === false })));
    row.append(h('div', { class: 'row', style: { gap: '14px' } },
      pair('شبعت؟', m.full, async (v) => { await S.rateMeal(m, v, m.liked); rerender(); }),
      pair('عجبتك؟', m.liked, async (v) => { await S.rateMeal(m, m.full, v); rerender(); })));
  }
  return row;
}
export { mealRow };

route('home', () => {
  const p = S.profile();
  const today = epochDay();
  const plan = S.currentPlan();
  const meals = S.mealsOn(today);
  const consumed = meals.reduce((a, m) => a + m.calories, 0);
  const sum = (k) => meals.reduce((a, m) => a + m[k], 0);
  const hist = S.history();
  const streak = N.currentStreak(hist);
  if (session.seed == null) session.seed = Math.floor(Math.random() * 1000);
  const line = N.motivation({ sex: p.sex, minute: nowMinute(), consumed, target: plan.dailyTarget, streak, mealsToday: meals.length }, session.seed);
  const ws = S.sortedWeights();
  const lastWeighDay = ws.length ? ws[ws.length - 1].day : p.startEpochDay;
  const favs = S.favorites();

  return tabScreen('home',
    h('div', { class: 'header' },
      h('div', { class: 'grow' }, h('h1', null, N.greeting(p.name, nowMinute())), h('div', { class: 'small muted' }, line))),
    !S.apiKey() ? h('button', { class: 'warn row', style: { border: 0, textAlign: 'start', width: '100%' }, onclick: () => go('apikey') },
      icon('key'), h('span', { class: 'grow small' }, 'اربط Gemini المجاني حتى تكدر تصوّر وجباتك'), icon('back')) : null,
    isIos() && !isStandalone() ? h('button', { class: 'info row', style: { border: 0, textAlign: 'start', width: '100%' }, onclick: installGuide },
      icon('iosShare'), h('span', { class: 'grow small' }, 'ضيف التطبيق للشاشة الرئيسية حتى ينفتح مثل أي تطبيق وتنحفظ بياناتك'), icon('back')) : null,
    h('div', { class: 'hero row', style: { gap: '16px' } },
      ring(consumed, plan.dailyTarget),
      h('div', { class: 'grow stack-s', style: { gap: '8px' } },
        h('div', { class: 'small muted' }, 'اليوم'),
        h('div', null, h('span', { style: { fontSize: '22px', fontWeight: 800 }, class: 'num' }, consumed), h('span', { class: 'small muted' }, ` من ${plan.dailyTarget}`)),
        macroBar('بروتين', sum('proteinG'), plan.macros.proteinG),
        macroBar('كارب', sum('carbsG'), plan.macros.carbsG),
        macroBar('دهون', sum('fatG'), plan.macros.fatG))),
    N.WEIGH_IN_INTERVAL_DAYS <= today - lastWeighDay ? h('button', { class: 'soft row', style: { border: 0, width: '100%', textAlign: 'start' }, onclick: () => go('progress', true) },
      icon('scale'), h('span', { class: 'grow small semi' }, 'صار وقت توزن نفسك، حتى نضبط خطتك'), icon('back')) : null,
    h('div', { class: 'row' },
      h('button', { class: 'btn grow', onclick: () => { if (!S.apiKey()) { go('apikey'); return; } pickPhoto(); } }, icon('camera'), 'صوّر وجبتك'),
      h('button', { class: 'btn outline', style: { width: 'auto', minHeight: '52px' }, 'aria-label': 'اختار صورة من الألبوم', onclick: () => { if (!S.apiKey()) { go('apikey'); return; } pickPhoto(false); } }, 'من الألبوم')),
    h('div', { class: 'row' },
      h('button', { class: 'btn outline grow', onclick: () => { if (!S.apiKey()) { go('apikey'); return; } session.meal = { mode: 'text', state: 'input', text: '' }; go('meal'); } }, icon('pencil'), 'اكتب وجبتك'),
      h('button', { class: 'btn outline grow', onclick: () => go('plan') }, icon('utensils'), 'شنو آكل؟')),
    h('div', { class: 'row' },
      h('button', { class: 'link-chip grow', onclick: () => go('recipes') }, icon('book'), 'وصفاتي'),
      h('button', { class: 'link-chip grow', onclick: () => go('shopping') }, icon('cart'), 'المشتريات'),
      h('button', { class: 'link-chip grow', onclick: () => go('meals') }, icon('calendar'), 'وجباتي')),
    favs.length ? h('div', { class: 'stack-s' },
      h('h3', null, 'وجباتك المعتادة'),
      h('div', { class: 'row', style: { overflowX: 'auto', paddingBottom: '4px' } }, favs.map((f) =>
        h('button', { class: 'card tight', style: { flex: 'none', textAlign: 'start', maxWidth: '170px' }, onclick: async () => { await S.logAgain(f); toast(`انضافت «${f.title}»`); rerender(); } },
          h('div', { class: 'semi small ellipsis' }, f.title), h('div', { class: 'xs muted' }, `${f.calories} سعرة · اضغط للتسجيل`))))) : null,
    h('h3', null, 'وجبات اليوم'),
    meals.length ? meals.map((m) => mealRow(m)) : h('div', { class: 'muted small' }, 'ما سجلت شي اليوم بعد. صوّر أول وجبة.'),
  );
});

// ───── البداية ─────

function applyTheme() {
  const t = S.prefs && getKv('prefs') ? (getKv('prefs').theme || 'auto') : 'auto';
  if (t === 'auto') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.setAttribute('data-theme', t);
}
export { applyTheme };

async function start() {
  try {
    await init();
  } catch (e) {
    document.getElementById('app').replaceChildren(h('div', { class: 'screen' }, h('div', { class: 'error' }, 'ما كدرنا نفتح التخزين بهذا المتصفح. جرّب Safari وطفّي التصفح الخاص.')));
    return;
  }
  applyTheme();
  const r = location.hash.replace(/^#\/?/, '');
  if (!S.profile()) {
    if (!r.startsWith('onboarding')) history.replaceState(null, '', '#/onboarding');
  } else if (!r) history.replaceState(null, '', '#/home');
  session.navDepth = 0;
  render(true);
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('./sw.js').catch(() => {});
}

start();
