// شاشات: الإعداد، مفتاح Gemini، السجل، التقدم، الملف.

import { data, getKv, setKv, exportAll, importAll } from './db.js';
import { h, icon, epochDay, dayToDate, fullDate, shortDate, MONTHS, toast, confirmBox, chips, parseNum, fmt1, header, hhmm } from './ui.js';
import { route, go, back, rerender, session, tabScreen } from './nav.js';
import * as S from './store.js';
import * as N from './nutrition.js';
import { verifyKey } from './gemini.js';
import { mealRow, installGuide, applyTheme } from './app.js';

// ───── الإعداد (أول مرة أو تعديل) ─────

function draftFromProfile(p) {
  if (p) return {
    name: p.name || '', sex: p.sex, age: String(p.age), height: String(p.heightCm), weight: String(S.currentWeight()),
    activity: p.activity, goals: [...p.goals], target: String(p.targetWeightKg), rate: p.weeklyRateKg,
    med: p.thyroidMedMinute != null ? hhmm(p.thyroidMedMinute) : '',
  };
  return { name: '', sex: 'MALE', age: '', height: '', weight: '', activity: 'SEDENTARY', goals: [], target: '', rate: 0.5, med: '' };
}

route('onboarding', (r) => {
  const edit = r.params.edit === '1' && S.profile();
  const key = edit ? 'editDraft' : 'draft';
  if (!session[key]) session[key] = draftFromProfile(edit ? S.profile() : null);
  const d = session[key];

  // الأجزاء اللي تتغير ويا الكتابة نحدّثها بروحها حتى ما يضيع المؤشر من الحقل
  const idealBox = h('div');
  const targetWarn = h('div');
  const planBox = h('div');
  const saveBtn = h('button', { class: 'btn' }, edit ? 'احفظ' : 'يلا نبدي');

  const vals = () => {
    const age = parseNum(d.age), cm = parseNum(d.height), kg = parseNum(d.weight), target = parseNum(d.target);
    const basicsOk = age >= 14 && age <= 100 && cm >= 120 && cm <= 230 && kg >= 35 && kg <= 300;
    return { age, cm, kg, target, basicsOk, targetOk: basicsOk && target >= 35 && target <= kg + 0.01 };
  };

  const refresh = () => {
    const v = vals();
    idealBox.replaceChildren();
    if (v.basicsOk) {
      const ideal = N.idealWeight(v.cm, v.age);
      const [lo, hi] = N.healthyRange(v.cm);
      const staged = Math.round(v.kg * 0.9 * 2) / 2;
      idealBox.append(h('div', { class: 'soft stack-s' },
        h('div', { class: 'small semi' }, 'وزنك المثالي حسب طولك وعمرك'),
        h('div', { style: { fontSize: '26px', fontWeight: 800 } }, `${fmt1(ideal)} كغم`),
        h('div', { class: 'xs' }, `المدى الطبيعي: ${fmt1(lo)} – ${fmt1(hi)} كغم`),
        v.kg < lo ? h('div', { class: 'xs', style: { color: 'var(--red-text)' } }, 'وزنك الحالي أقل من الطبيعي، فالأفضل تحافظ عليه أو تزيده بمراجعة الطبيب.') : null,
        v.kg > ideal ? h('div', { class: 'row wrap', style: { gap: '6px' } },
          h('button', { class: 'chip', onclick: () => { d.target = String(ideal); targetInput.value = d.target; refresh(); } }, `الهدف: ${fmt1(ideal)}`),
          staged > ideal ? h('button', { class: 'chip', onclick: () => { d.target = String(staged); targetInput.value = d.target; refresh(); } }, `هدف مرحلي: ${fmt1(staged)} (نزول ١٠٪)`) : null) : null));
      targetInput.placeholder = fmt1(ideal);
    }
    targetWarn.replaceChildren(v.basicsOk && v.target > v.kg ? h('div', { class: 'warn small' }, 'الهدف لازم يكون أقل من وزنك الحالي أو يساويه.') : '');
    planBox.replaceChildren();
    if (v.targetOk) {
      const plan = N.plan(buildProfile(d, edit ? S.profile() : null), v.kg, epochDay());
      planBox.append(h('div', { class: 'hero stack-s' },
        h('div', { class: 'small muted' }, 'خطتك اليومية'),
        h('div', null, h('span', { style: { fontSize: '40px', fontWeight: 800 }, class: 'num' }, plan.dailyTarget), h('span', { class: 'muted small' }, ' سعرة باليوم')),
        h('div', { class: 'small' }, `احتياجك ${plan.tdee} سعرة · عجز ${plan.dailyDeficit} باليوم · نزول ~${fmt1(plan.weeklyLossKg)} كغم/أسبوع`),
        plan.etaDay ? h('div', { class: 'small' }, `توصل لـ ${fmt1(v.target)} كغم حوالي ${shortDate(plan.etaDay)}`) : h('div', { class: 'small' }, 'هدفك تحافظ على وزنك الحالي، فسعراتك اليومية تساوي احتياجك.'),
        plan.limitedByMinimum ? h('div', { class: 'xs', style: { color: '#FFD27A' } }, `ما ننزل تحت ${plan.minimumCalories} سعرة لأنه الحد الأدنى الآمن، فالنزول راح يكون أبطأ شوية.`) : null));
    }
    saveBtn.disabled = !v.targetOk;
  };

  const field = (id, label, value, set, attrs = {}) => {
    const input = h('input', { id, class: 'input', value, oninput: (e) => { set(e.target.value); refresh(); }, ...attrs });
    return [h('div', { class: 'field' }, h('label', { for: id }, label), input), input];
  };

  const parts = [];
  parts.push(edit ? header('تعديل بياناتك', null, () => { session.editDraft = null; back('profile'); })
    : h('div', { class: 'stack-s' }, h('h1', null, 'أهلاً بيك بـ «شد الهمة»'), h('div', { class: 'muted' }, 'خلّينا نعرفك حتى نحسب خطتك اليومية. كل البيانات تبقى على تلفونك.')));

  parts.push(field('ob-name', 'اسمك', d.name, (v) => { d.name = v.trim(); }, { placeholder: 'مثلاً: حيدر' })[0]);
  parts.push(h('div', { class: 'stack-s' }, h('div', { class: 'small semi' }, 'الجنس'),
    chips([['MALE', 'ذكر'], ['FEMALE', 'أنثى']], d.sex, (v) => { d.sex = v; rerender(); })));
  parts.push(h('div', { class: 'row top' },
    h('div', { class: 'grow' }, field('ob-age', 'العمر', d.age, (v) => { d.age = v; }, { inputmode: 'numeric', placeholder: 'سنة' })[0]),
    h('div', { class: 'grow' }, field('ob-h', 'الطول (سم)', d.height, (v) => { d.height = v; }, { inputmode: 'decimal', placeholder: '170' })[0]),
    h('div', { class: 'grow' }, field('ob-w', 'الوزن (كغم)', d.weight, (v) => { d.weight = v; }, { inputmode: 'decimal', placeholder: '85' })[0])));

  parts.push(h('div', { class: 'stack-s' }, h('div', { class: 'small semi' }, 'مستوى نشاطك اليومي'),
    Object.entries(N.ACTIVITY).map(([k, a]) => h('button', { class: 'choice' + (d.activity === k ? ' on' : ''), onclick: () => { d.activity = k; rerender(); } },
      h('span', { class: 'dot' }), h('span', { class: 'grow' }, h('div', { class: 'semi' }, a.label), h('div', { class: 'xs muted' }, a.hint))))));

  parts.push(h('div', { class: 'stack-s' }, h('div', { class: 'small semi' }, 'أهدافك الصحية (اختياري، تكدر تختار أكثر من وحدة)'),
    Object.entries(N.GOALS).map(([k, g]) => h('button', { class: 'choice check' + (d.goals.includes(k) ? ' on' : ''), onclick: () => { d.goals = d.goals.includes(k) ? d.goals.filter((x) => x !== k) : [...d.goals, k]; rerender(); } },
      h('span', { class: 'dot' }), h('span', { class: 'grow' }, h('div', { class: 'semi' }, g.label), h('div', { class: 'xs muted' }, g.description))))));

  if (d.goals.includes('HYPOTHYROID')) {
    parts.push(h('div', { class: 'field' }, h('label', { for: 'ob-med' }, 'وقت أخذ دواء الغدة'),
      h('input', { id: 'ob-med', class: 'input', type: 'time', value: d.med, onchange: (e) => { d.med = e.target.value; } }),
      h('div', { class: 'hint' }, 'حتى ننبهك إذا الوجبة قريبة من وقت الدواء.')));
  }

  parts.push(idealBox);
  const [targetField, targetInput] = field('ob-target', 'الوزن اللي تريد توصله (كغم)', d.target, (v) => { d.target = v; }, { inputmode: 'decimal' });
  parts.push(targetField, targetWarn);

  parts.push(h('div', { class: 'stack-s' }, h('div', { class: 'small semi' }, 'سرعة النزول بالأسبوع (كغم)'),
    chips([[0.25, '٠٫٢٥'], [0.5, '٠٫٥'], [0.75, '٠٫٧٥'], [1, '١']], d.rate, (v) => { d.rate = v; rerender(); }),
    h('div', { class: 'hint' }, 'نص كيلو بالأسبوع سرعة صحية ومريحة لأغلب الناس.')));
  parts.push(planBox);

  saveBtn.onclick = async () => {
    const v = vals();
    if (!v.targetOk) return;
    const p = buildProfile(d, edit ? S.profile() : null);
    await setKv('profile', p);
    if (!edit) {
      await S.addWeight(v.kg);
      session.draft = null;
      go(S.apiKey() ? 'home' : 'apikey?first=1', true);
    } else {
      if (Math.abs(v.kg - S.currentWeight()) > 0.01) await S.addWeight(v.kg);
      session.editDraft = null;
      toast('انحفظت بياناتك');
      back('profile');
    }
  };
  refresh();
  return h('div', null, h('main', { class: 'screen no-tabs stack' }, ...parts), h('div', { class: 'bottom-bar' }, saveBtn));
});

function buildProfile(d, old) {
  const [hh, mm] = (d.med || '').split(':').map(Number);
  return {
    name: d.name, sex: d.sex, age: Math.round(parseNum(d.age)), heightCm: parseNum(d.height),
    startWeightKg: old ? old.startWeightKg : parseNum(d.weight), activity: d.activity, goals: d.goals,
    targetWeightKg: parseNum(d.target), weeklyRateKg: d.rate,
    startEpochDay: old ? old.startEpochDay : epochDay(), tdeeAdjustment: old ? (old.tdeeAdjustment || 0) : 0,
    thyroidMedMinute: d.goals.includes('HYPOTHYROID') && !isNaN(hh) ? hh * 60 + (mm || 0) : null,
  };
}

// ───── مفتاح Gemini ─────

route('apikey', (r) => {
  const first = r.params.first === '1';
  if (session.keyInput == null) session.keyInput = S.apiKey();
  const st = session.keyState || {};
  const save = async () => {
    const k = (session.keyInput || '').trim();
    if (!k) return;
    session.keyState = { busy: true }; rerender();
    const err = await verifyKey(k);
    if (err) { session.keyState = { error: err }; rerender(); return; }
    await setKv('apiKey', k);
    session.keyState = null; session.keyInput = null;
    toast('انربط Gemini');
    if (first) go('home', true); else back('profile');
  };
  return h('div', null, h('main', { class: 'screen no-tabs stack' },
    header('اربط Gemini المجاني', 'حتى نحلل صور الوجبات ونقترح أكلات', first ? null : () => { session.keyInput = null; session.keyState = null; back('profile'); }),
    h('div', { class: 'card stack' },
      step(1, h('span', null, 'افتح ', h('a', { href: 'https://aistudio.google.com/apikey', target: '_blank', rel: 'noopener' }, 'Google AI Studio'), ' وسجّل دخول بحساب Gmail.')),
      step(2, 'اضغط «Create API key» وانسخ المفتاح.'),
      step(3, 'الصقه هنا واضغط «تحقق واحفظ».')),
    h('div', { class: 'field' },
      h('label', { for: 'apikey' }, 'المفتاح'),
      h('input', { id: 'apikey', class: 'input', value: session.keyInput, placeholder: 'AIza…', autocomplete: 'off', autocapitalize: 'off', spellcheck: 'false', dir: 'ltr', oninput: (e) => { session.keyInput = e.target.value; } }),
      h('div', { class: 'hint' }, 'مجاني، وينحفظ على تلفونك بس.')),
    st.error ? h('div', { class: 'error small' }, st.error) : null,
    h('button', { class: 'btn', disabled: !!st.busy, onclick: save }, st.busy ? h('div', { class: 'spinner', style: { borderTopColor: '#fff' } }) : null, st.busy ? 'نتحقق…' : 'تحقق واحفظ'),
    first ? h('button', { class: 'btn ghost', style: { alignSelf: 'center' }, onclick: () => go('home', true) }, 'بعدين') : null,
  ));
});

function step(n, content) {
  return h('div', { class: 'row top' }, h('span', { class: 'step-num' }, n), h('div', { class: 'grow small' }, content));
}

// ───── السجل ─────

const STATUS_COLOR = { ON_TARGET: 'var(--green)', OVER: 'var(--orange)', TOO_LOW: 'var(--red)' };

route('history', () => {
  const p = S.profile();
  const today = epochDay();
  const hist = S.history();
  const byDay = Object.fromEntries(hist.map((r) => [r.day, r]));
  const evaluated = hist.filter((r) => r.status !== 'IN_PROGRESS');
  const adherent = evaluated.filter((r) => r.status === 'ON_TARGET').length;
  const streak = N.currentStreak(hist);
  const week = Math.floor((today - p.startEpochDay) / 7) + 1;

  if (session.calMonth == null) { const d = new Date(); session.calMonth = [d.getFullYear(), d.getMonth()]; }
  if (session.calSel == null) session.calSel = today;
  const [y, mo] = session.calMonth;
  const first = new Date(y, mo, 1);
  const startDow = (first.getDay() + 1) % 7; // السبت أول الأسبوع
  const daysIn = new Date(y, mo + 1, 0).getDate();
  const cells = [];
  ['سبت', 'أحد', 'اثنين', 'ثلاثاء', 'أربعاء', 'خميس', 'جمعة'].forEach((d) => cells.push(h('div', { class: 'dow' }, d)));
  for (let i = 0; i < startDow; i++) cells.push(h('div'));
  for (let dnum = 1; dnum <= daysIn; dnum++) {
    const day = epochDay(new Date(y, mo, dnum));
    const rec = byDay[day];
    const inPlan = day >= p.startEpochDay && day <= today;
    const fill = rec && STATUS_COLOR[rec.status];
    const cls = 'day' + (!inPlan ? ' out' : '') + (day === session.calSel ? ' sel' : day === today ? ' today' : '');
    cells.push(h('button', {
      class: cls, disabled: !inPlan,
      style: fill ? { background: fill, color: '#fff' } : rec && rec.status === 'NO_DATA' ? { background: 'var(--line)' } : null,
      onclick: () => { session.calSel = day; rerender(); },
    }, dnum));
  }
  const sel = byDay[session.calSel];
  const meals = S.mealsOn(session.calSel);
  const monthStart = epochDay(new Date(y, mo, 1));
  const monthEnd = epochDay(new Date(y, mo, daysIn));

  return tabScreen('history',
    h('div', { class: 'header' }, h('div', { class: 'grow' }, h('h1', null, 'السجل')), h('div', { class: 'small muted' }, `الأسبوع ${week} من الخطة`)),
    h('div', { class: 'row' },
      h('div', { class: 'card grow', style: { background: 'var(--green-soft)' } }, h('div', { class: 'small muted' }, 'أيام الالتزام'), h('div', { style: { fontSize: '26px', fontWeight: 800 } }, `${adherent} من ${evaluated.length}`)),
      h('div', { class: 'card grow' }, h('div', { class: 'small muted' }, 'أيام متتالية'), h('div', { style: { fontSize: '26px', fontWeight: 800, color: 'var(--green)' } }, streak))),
    h('div', { class: 'card stack' },
      h('div', { class: 'row' },
        h('button', { class: 'icon-btn plain', 'aria-label': 'الشهر السابق', disabled: monthStart <= p.startEpochDay, onclick: () => { session.calMonth = mo === 0 ? [y - 1, 11] : [y, mo - 1]; rerender(); } }, icon('back')),
        h('div', { class: 'grow center bold' }, `${MONTHS[mo]} ${y}`),
        h('button', { class: 'icon-btn plain', 'aria-label': 'الشهر الجاي', disabled: monthEnd >= today, style: { transform: 'scaleX(-1)' }, onclick: () => { session.calMonth = mo === 11 ? [y + 1, 0] : [y, mo + 1]; rerender(); } }, icon('back'))),
      h('div', { class: 'cal' }, cells)),
    h('div', { class: 'legend' },
      h('span', null, h('i', { style: { background: 'var(--green)' } }), 'ملتزم'),
      h('span', null, h('i', { style: { background: 'var(--orange)' } }), 'زيادة'),
      h('span', null, h('i', { style: { background: 'var(--red)' } }), 'أقل من الآمن'),
      h('span', null, h('i', { style: { background: 'var(--line)' } }), 'بدون تسجيل')),
    h('div', { class: 'row' }, h('h3', { class: 'grow' }, fullDate(session.calSel)), sel ? h('div', { class: 'small muted num' }, `${sel.consumed} / ${sel.target}`) : null),
    sel ? h('div', { class: 'small', style: { color: STATUS_COLOR[sel.status] || 'var(--muted)' } }, sel.message) : null,
    meals.length ? meals.map((m) => mealRow(m, { showRate: true })) : h('div', { class: 'small muted' }, 'ما كو وجبات مسجلة بهذا اليوم.'),
  );
});

// ───── التقدم ─────

function weightChart(ws, target) {
  if (ws.length < 2) return null;
  const W = 320, H = 170, pad = 24;
  const kgs = ws.map((w) => w.kg).concat([target]);
  const min = Math.min(...kgs) - 1, max = Math.max(...kgs) + 1;
  const d0 = ws[0].day, d1 = Math.max(ws[ws.length - 1].day, d0 + 1);
  const x = (d) => W - pad - (d - d0) / (d1 - d0) * (W - 2 * pad); // من اليمين لليسار
  const yv = (kg) => pad + (max - kg) / (max - min) * (H - 2 * pad);
  const pts = ws.map((w) => `${x(w.day).toFixed(1)},${yv(w.kg).toFixed(1)}`).join(' ');
  const div = h('div', { class: 'card chart' });
  div.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="منحنى الوزن">
    <line x1="${pad}" x2="${W - pad}" y1="${yv(target)}" y2="${yv(target)}" stroke="var(--green)" stroke-dasharray="4 4" stroke-width="1.5" opacity=".7"/>
    <text x="${pad}" y="${yv(target) - 6}" font-size="11" fill="var(--green)">الهدف ${fmt1(target)}</text>
    <polyline points="${pts}" fill="none" stroke="var(--ink)" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"/>
    ${ws.map((w) => `<circle cx="${x(w.day)}" cy="${yv(w.kg)}" r="4" fill="var(--card)" stroke="var(--ink)" stroke-width="2"/>`).join('')}
    <text x="${x(ws[ws.length - 1].day)}" y="${yv(ws[ws.length - 1].kg) - 10}" font-size="12" font-weight="700" fill="var(--ink)" text-anchor="middle">${fmt1(ws[ws.length - 1].kg)}</text>
  </svg>`;
  return div;
}

route('progress', () => {
  const p = S.profile();
  const ws = S.sortedWeights();
  const kg = S.currentWeight();
  const plan = S.currentPlan();
  const lost = p.startWeightKg - kg;
  const total = p.startWeightKg - p.targetWeightKg;
  const frac = total > 0 ? Math.max(0, Math.min(1, lost / total)) : 1;
  const b = N.bmi(kg, p.heightCm);
  const bmiLabel = b < 18.5 ? 'أقل من الطبيعي' : b < 25 ? 'طبيعي' : b < 30 ? 'زيادة وزن' : 'سمنة';
  const st = session.weightMsg;

  return tabScreen('progress',
    h('h1', null, 'التقدم'),
    h('div', { class: 'hero stack' },
      h('div', { class: 'row' },
        h('div', { class: 'grow' }, h('div', { class: 'small muted' }, 'وزنك الحالي'), h('div', { style: { fontSize: '34px', fontWeight: 800 } }, `${fmt1(kg)} كغم`)),
        h('div', { class: 'center' }, h('div', { class: 'small muted' }, lost >= 0 ? 'نزلت' : 'زدت'), h('div', { style: { fontSize: '22px', fontWeight: 800, color: '#5BC79A' } }, `${fmt1(Math.abs(lost))} كغم`))),
      h('div', { class: 'bar' }, h('i', { style: { width: frac * 100 + '%', background: '#5BC79A' } })),
      h('div', { class: 'row xs muted' }, h('span', { class: 'grow' }, `البداية ${fmt1(p.startWeightKg)}`), h('span', null, `الهدف ${fmt1(p.targetWeightKg)}`)),
      plan.etaDay ? h('div', { class: 'small' }, `باقي ${fmt1(plan.remainingKg)} كغم · توصل حوالي ${shortDate(plan.etaDay)}`) : h('div', { class: 'small' }, 'وصلت هدفك، حافظ عليه!')),
    h('div', { class: 'row' },
      h('div', { class: 'card grow' }, h('div', { class: 'xs muted' }, 'مؤشر كتلة الجسم'), h('div', { class: 'bold', style: { fontSize: '20px' } }, fmt1(b)), h('div', { class: 'xs muted' }, bmiLabel)),
      h('div', { class: 'card grow' }, h('div', { class: 'xs muted' }, 'سعراتك اليومية'), h('div', { class: 'bold', style: { fontSize: '20px' } }, plan.dailyTarget), h('div', { class: 'xs muted' }, `احتياجك ${plan.tdee}`))),
    h('div', { class: 'card stack' },
      h('h3', null, 'سجّل وزنك'),
      h('div', { class: 'row' },
        h('input', { id: 'newWeight', class: 'input grow', inputmode: 'decimal', placeholder: 'الوزن بالكيلو', value: session.weightInput || '', oninput: (e) => { session.weightInput = e.target.value; } }),
        h('button', { class: 'btn', style: { width: 'auto' }, onclick: async () => {
          const v = parseNum(session.weightInput);
          if (!(v >= 30 && v <= 300)) { toast('اكتب وزن صحيح'); return; }
          const cal = await S.addWeight(v);
          session.weightInput = '';
          session.weightMsg = calMessage(cal);
          toast('انسجل الوزن');
          rerender();
        } }, 'سجّل')),
      h('div', { class: 'hint' }, 'الأفضل توزن نفسك الصبح قبل الأكل، كل أسبوعين.'),
      st ? h('div', { class: 'soft small' }, st) : null),
    weightChart(ws, p.targetWeightKg),
    ws.length ? h('div', { class: 'stack-s' }, h('h3', null, 'القياسات'),
      [...ws].reverse().map((w) => h('div', { class: 'card tight row' },
        h('div', { class: 'grow' }, h('div', { class: 'semi' }, `${fmt1(w.kg)} كغم`), h('div', { class: 'xs muted' }, fullDate(w.day))),
        h('button', { class: 'icon-btn plain', 'aria-label': 'حذف القياس', onclick: () => confirmBox('تحذف هذا القياس؟', 'حذف', async () => { await S.deleteWeight(w.id); rerender(); }) }, icon('trash'))))) : null,
  );
});

function calMessage(c) {
  if (!c) return null;
  if (!c.reliable) return c.days < 7 ? 'انسجل الوزن. نعدّل خطتك بعد أسبوع من القياس السابق.' : 'انسجل الوزن. حتى نعدّل خطتك نحتاج تسجّل أكلك أغلب الأيام.';
  const err = c.tdeeError;
  if (Math.abs(err) < 75) return `ماشي مثل ما محسوب بالضبط: نزلت ${fmt1(c.lostKg)} كغم خلال ${c.days} يوم.`;
  return err > 0 ? `جسمك يحرق أكثر من المحسوب، فرفعنا سعراتك شوية. نزلت ${fmt1(c.lostKg)} كغم خلال ${c.days} يوم.`
    : `جسمك يحرق أقل من المحسوب، فقللنا سعراتك شوية حتى تكمّل بنفس السرعة.`;
}

// ───── الملف ─────

route('profile', () => {
  const p = S.profile();
  const plan = S.currentPlan();
  const pr = S.prefs();
  const fileInput = h('input', { type: 'file', accept: 'application/json,.json', style: { display: 'none' }, onchange: async (e) => {
    const f = e.target.files[0];
    if (!f) return;
    try { await importAll(await f.text()); applyTheme(); toast('رجعت بياناتك'); go('home', true); }
    catch (err) { toast(err.message || 'الملف غير صالح'); }
  } });
  const goalLabels = p.goals.map((g) => N.GOALS[g].label).join('، ') || 'نزول وزن عام';
  return tabScreen('profile',
    h('h1', null, 'الملف'),
    h('div', { class: 'card stack-s' },
      h('div', { class: 'bold', style: { fontSize: '18px' } }, p.name || 'بدون اسم'),
      h('div', { class: 'small muted' }, `${p.sex === 'MALE' ? 'ذكر' : 'أنثى'} · ${p.age} سنة · ${fmt1(p.heightCm)} سم · ${N.ACTIVITY[p.activity].label}`),
      h('div', { class: 'small' }, `الأهداف: ${goalLabels}`),
      h('div', { class: 'small' }, `السعرات اليومية: ${plan.dailyTarget} · بروتين ${plan.macros.proteinG}غ · كارب ${plan.macros.carbsG}غ · دهون ${plan.macros.fatG}غ`),
      p.tdeeAdjustment ? h('div', { class: 'xs muted' }, `الاحتياج معدّل ${p.tdeeAdjustment > 0 ? '+' : ''}${p.tdeeAdjustment} سعرة حسب قياسات وزنك`) : null,
      h('button', { class: 'btn outline small', style: { marginTop: '6px' }, onclick: () => { session.editDraft = null; go('onboarding?edit=1'); } }, icon('edit'), 'عدّل بياناتك')),
    h('button', { class: 'card row', style: { textAlign: 'start', width: '100%' }, onclick: () => go('apikey') },
      icon('key'), h('div', { class: 'grow' }, h('div', { class: 'semi' }, 'مفتاح Gemini'), h('div', { class: 'xs muted' }, S.apiKey() ? 'مربوط' : 'غير مربوط')), icon('back')),
    h('div', { class: 'card stack-s' },
      h('div', { class: 'semi' }, 'المظهر'),
      chips([['auto', 'حسب التلفون'], ['light', 'فاتح'], ['dark', 'داكن']], pr.theme || 'auto', async (v) => { await S.setPrefs({ theme: v }); applyTheme(); rerender(); })),
    h('div', { class: 'card stack' },
      h('div', null, h('div', { class: 'semi' }, 'نسخة احتياطية'), h('div', { class: 'xs muted' }, 'بياناتك محفوظة داخل Safari بهذا التلفون بس. احفظ نسخة كل فترة حتى ما تضيع إذا انمسحت بيانات المتصفح.')),
      h('div', { class: 'row' },
        h('button', { class: 'btn outline small grow', onclick: () => saveBackup() }, icon('download'), 'احفظ نسخة'),
        h('button', { class: 'btn outline small grow', onclick: () => fileInput.click() }, icon('upload'), 'رجّع نسخة')),
      fileInput),
    h('button', { class: 'card row', style: { textAlign: 'start', width: '100%' }, onclick: installGuide },
      icon('iosShare'), h('div', { class: 'grow semi' }, 'ضيف التطبيق للشاشة الرئيسية'), icon('back')),
    h('button', { class: 'card row', style: { textAlign: 'start', width: '100%' }, onclick: () => shareApp() },
      icon('share'), h('div', { class: 'grow semi' }, 'شارك التطبيق ويا غيرك'), icon('back')),
    h('div', { class: 'xs muted center' }, 'شد الهمة · نسخة الويب · مجاني بالكامل'),
    h('button', { class: 'btn ghost danger', style: { alignSelf: 'center' }, onclick: () => confirmBox('تمسح كل بياناتك من هذا التلفون؟', 'امسح الكل', async () => {
      indexedDB.deleteDatabase('sarat');
      location.hash = '';
      location.reload();
    }) }, 'امسح كل البيانات'),
  );
});

async function saveBackup() {
  const text = exportAll();
  const name = `sarat-backup-${new Date().toISOString().slice(0, 10)}.json`;
  const file = new File([text], name, { type: 'application/json' });
  if (navigator.canShare && navigator.canShare({ files: [file] })) {
    try { await navigator.share({ files: [file], title: 'نسخة شد الهمة' }); return; } catch (e) { if (e.name === 'AbortError') return; }
  }
  const a = h('a', { href: URL.createObjectURL(file), download: name });
  document.body.appendChild(a); a.click(); a.remove();
}

async function shareApp() {
  const url = location.origin + location.pathname;
  const text = `جرّب تطبيق «شد الهمة» لحساب السعرات، مجاني ويشتغل على الآيفون والأندرويد:\n${url}\nافتحه بـ Safari واختار «إضافة إلى الشاشة الرئيسية».`;
  if (navigator.share) { try { await navigator.share({ text }); return; } catch (e) { if (e.name === 'AbortError') return; } }
  try { await navigator.clipboard.writeText(text); toast('انسخ الرابط'); } catch (_) { toast(url); }
}
