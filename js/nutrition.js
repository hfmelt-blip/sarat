// الحسابات: نفس منطق نسخة الأندرويد (Mifflin-St Jeor، 7700 سعرة للكيلو).

export const ACTIVITY = {
  SEDENTARY: { factor: 1.2, label: 'قليل الحركة', hint: 'عمل مكتبي، بدون رياضة' },
  LIGHT: { factor: 1.375, label: 'نشاط خفيف', hint: 'رياضة ١–٣ أيام بالأسبوع' },
  MODERATE: { factor: 1.55, label: 'نشاط متوسط', hint: 'رياضة ٣–٥ أيام بالأسبوع' },
  HIGH: { factor: 1.725, label: 'نشاط عالي', hint: 'رياضة يومية أو عمل بدني' },
};

export const GOALS = {
  INSULIN_RESISTANCE: { label: 'مقاومة الإنسولين', description: 'نقلل السكريات والنشويات المكررة، ونعطي أولوية للألياف والبروتين.', carbShare: 0.30 },
  HYPOTHYROID: { label: 'خمول الغدة الدرقية', description: 'أكل متوازن غني باليود والسيلينيوم، وتنبيه إذا الوجبة قريبة من وقت الدواء.', carbShare: 0.45 },
  OBESITY_LIPIDS: { label: 'السمنة والدهون الثلاثية والحشوية', description: 'نقلل السكر والدهون المشبعة والمقليات، ونزيد الخضار والأوميغا ٣.', carbShare: 0.40 },
};

export const KCAL_PER_KG = 7700;
export const MAX_WEEKLY_RATE_KG = 1.0;
export const OVER_TOLERANCE = 0.05;
export const UNDER_TOLERANCE = 0.10;
export const WEIGH_IN_INTERVAL_DAYS = 14;
const MIN_CALIBRATION_DAYS = 7, MIN_LOGGED_SHARE = 0.7, CALIBRATION_DAMPING = 0.5, MAX_ADJUSTMENT = 500;

export function bmr(sex, kg, cm, age) {
  const base = 10 * kg + 6.25 * cm - 5 * age;
  return sex === 'MALE' ? base + 5 : base - 161;
}
export const minimumCalories = (sex) => (sex === 'MALE' ? 1500 : 1200);
export const tdee = (p, kg) => Math.round(bmr(p.sex, kg, p.heightCm, p.age) * ACTIVITY[p.activity].factor + (p.tdeeAdjustment || 0));

const roundHalf = (v) => Math.round(v * 2) / 2;
export function idealWeight(cm, age) { const m = cm / 100; return roundHalf((age >= 65 ? 24 : 22) * m * m); }
export function healthyRange(cm) { const m = cm / 100; return [roundHalf(18.5 * m * m), roundHalf(24.9 * m * m)]; }
export function bmi(kg, cm) { const m = cm / 100; return kg / (m * m); }

export function carbShare(goals) {
  return goals && goals.length ? Math.min(...goals.map((g) => GOALS[g].carbShare)) : 0.45;
}

export function macros(target, kg, goals) {
  const protein = Math.min(1.6 * kg, target * 0.30 / 4);
  const carbs = target * carbShare(goals) / 4;
  const fat = Math.max(0, (target - protein * 4 - carbs * 4) / 9);
  return { proteinG: Math.round(protein), carbsG: Math.round(carbs), fatG: Math.round(fat) };
}

export function plan(p, kg, today) {
  const t = tdee(p, kg);
  const minCal = minimumCalories(p.sex);
  const remaining = Math.max(0, kg - p.targetWeightKg);
  const rate = Math.min(Math.max(p.weeklyRateKg, 0), MAX_WEEKLY_RATE_KG);
  const requested = remaining > 0 ? Math.round(rate * KCAL_PER_KG / 7) : 0;
  const raw = t - requested;
  const target = Math.max(raw, minCal);
  const deficit = Math.max(0, t - target);
  const weeklyLoss = deficit * 7 / KCAL_PER_KG;
  const weeks = remaining > 0 && weeklyLoss > 0 ? remaining / weeklyLoss : null;
  return {
    tdee: t, dailyTarget: target, dailyDeficit: deficit, minimumCalories: minCal,
    weeklyLossKg: weeklyLoss, remainingKg: remaining, weeksToGoal: weeks,
    etaDay: weeks != null ? today + Math.ceil(weeks * 7) : null,
    limitedByMinimum: raw < minCal,
    macros: macros(target, kg, p.goals),
  };
}

// ───── الالتزام اليومي ─────

const praise = ['عاشت إيدك، ضمن هدفك', 'يوم مثالي، كمّل هيج', 'ضمن هدفك، استمر', 'أحسنت! خطوة أقرب للهدف', 'ممتاز، التزام يرفع الراس'];
const overMessages = ['باچر يوم جديد', 'ولا يهمك، نرجع للمسار', 'صارت، المهم الاستمرار'];

export function evaluateDay(day, consumed, target, minimum, today, streakAfter = 0) {
  const isToday = day === today;
  const limit = Math.round(target * (1 + OVER_TOLERANCE));
  const floor = Math.round(minimum * (1 - UNDER_TOLERANCE));
  const pick = Math.abs(day % 997);
  let status;
  if (consumed === 0 && !isToday) status = 'NO_DATA';
  else if (consumed > limit) status = 'OVER';
  else if (isToday) status = consumed >= floor ? 'ON_TARGET' : 'IN_PROGRESS';
  else if (consumed < floor) status = 'TOO_LOW';
  else status = 'ON_TARGET';
  let message;
  switch (status) {
    case 'ON_TARGET':
      message = consumed < minimum && !isToday ? `ضمن هدفك، بس ناقص ${minimum - consumed} سعرة عن الحد الأدنى`
        : streakAfter >= 3 ? `ممتاز! ${streakAfter} أيام ورا بعض` : praise[pick % praise.length];
      break;
    case 'OVER': message = `زدت ${consumed - target} سعرة، ${overMessages[pick % overMessages.length]}`; break;
    case 'TOO_LOW': message = 'أكلت أقل من الحد الآمن، جسمك يحتاج أكثر'; break;
    case 'IN_PROGRESS': message = `باقي ${Math.max(0, target - consumed)} سعرة لليوم`; break;
    default: message = 'ما مسجّل أكل لهذا اليوم';
  }
  return { day, consumed, target, status, message };
}

/** سجل الأيام من البداية لليوم، الأحدث أولاً. */
export function buildHistory(totals, from, today, target, minimum) {
  if (today < from) return [];
  const out = [];
  let streak = 0;
  for (let d = from; d <= today; d++) {
    const c = totals[d] || 0;
    const first = evaluateDay(d, c, target, minimum, today);
    streak = first.status === 'ON_TARGET' ? streak + 1 : first.status === 'IN_PROGRESS' ? streak : 0;
    out.push(first.status === 'ON_TARGET' ? evaluateDay(d, c, target, minimum, today, streak) : first);
  }
  return out.reverse();
}

export function currentStreak(history) {
  let s = 0;
  for (const r of history) {
    if (r.status === 'ON_TARGET') s++;
    else if (r.status === 'IN_PROGRESS') continue;
    else return s;
  }
  return s;
}

// ───── إعادة المعايرة من قياس الوزن ─────

export function calibrate(p, prevKg, prevDay, newKg, newDay, totals) {
  const days = newDay - prevDay;
  const logged = Object.entries(totals).filter(([d, k]) => +d >= prevDay && +d < newDay && k > 0).map(([, k]) => k);
  const avg = logged.length ? Math.round(logged.reduce((a, b) => a + b, 0) / logged.length) : 0;
  const lost = prevKg - newKg;
  const current = tdee(p, (prevKg + newKg) / 2);
  const expected = days > 0 && avg > 0 ? (current - avg) * days / KCAL_PER_KG : 0;
  const actualDeficit = days > 0 ? Math.round(lost * KCAL_PER_KG / days) : 0;
  const reliable = days >= MIN_CALIBRATION_DAYS && logged.length >= days * MIN_LOGGED_SHARE && avg >= minimumCalories(p.sex) * 0.6;
  const error = avg > 0 ? avg + actualDeficit - current : 0;
  const adj = reliable ? Math.max(-MAX_ADJUSTMENT, Math.min(MAX_ADJUSTMENT, Math.round((p.tdeeAdjustment || 0) + error * CALIBRATION_DAMPING))) : (p.tdeeAdjustment || 0);
  return { days, loggedDays: logged.length, lostKg: lost, expectedLostKg: expected, averageIntake: avg, actualDailyDeficit: actualDeficit, newAdjustment: adj, tdeeError: error, reliable };
}

// ───── توزيع الوجبات ─────

export const SLOTS = {
  BREAKFAST: { label: 'فطور', english: 'breakfast' },
  LUNCH: { label: 'غداء', english: 'lunch' },
  SNACK: { label: 'سناك', english: 'snack' },
  DINNER: { label: 'عشاء', english: 'dinner' },
};
export const LIGHT_MEAL_KCAL = 150;

export function slotsFor(n) {
  if (n === 2) return ['BREAKFAST', 'DINNER'];
  if (n === 4) return ['BREAKFAST', 'LUNCH', 'SNACK', 'DINNER'];
  return ['BREAKFAST', 'LUNCH', 'DINNER'];
}
function weight(slot, n) {
  if (n === 2) return slot === 'BREAKFAST' ? 0.45 : 0.55;
  if (n === 4) return { BREAKFAST: 0.25, LUNCH: 0.35, SNACK: 0.10, DINNER: 0.30 }[slot];
  return { BREAKFAST: 0.25, LUNCH: 0.40 }[slot] ?? 0.35;
}
export function slotForTime(minute, n) {
  const hr = Math.floor(minute / 60);
  if (n === 2) return hr >= 4 && hr <= 14 ? 'BREAKFAST' : 'DINNER';
  if (hr >= 4 && hr <= 10) return 'BREAKFAST';
  if (hr >= 11 && hr <= 15) return 'LUNCH';
  if (n === 4 && hr >= 16 && hr <= 18) return 'SNACK';
  return 'DINNER';
}

export function budget(slot, n, target, consumed, mac, cp, cc, cf) {
  const all = slotsFor(n);
  const start = Math.max(0, all.indexOf(slot));
  const rest = all.slice(start);
  const total = rest.reduce((a, s) => a + weight(s, n), 0);
  const remaining = target - consumed;
  const share = weight(slot, n) / total;
  const nearlyDone = remaining < LIGHT_MEAL_KCAL;
  const kcal = nearlyDone ? LIGHT_MEAL_KCAL : Math.round(remaining * share);
  const ratio = nearlyDone ? LIGHT_MEAL_KCAL / Math.max(target, 1) : share;
  const left = (t, c) => Math.max(0, t - c);
  return {
    slot, kcal,
    proteinG: Math.max(10, Math.round(left(mac.proteinG, cp) * ratio)),
    carbsG: Math.round(left(mac.carbsG, cc) * ratio),
    fatG: Math.round(left(mac.fatG, cf) * ratio),
    remainingBefore: remaining,
    later: rest.slice(1).map((s) => [s, nearlyDone ? 0 : Math.round(remaining * weight(s, n) / total)]),
    nearlyDone,
  };
}

// ───── التحفيز ─────

export function greeting(name, minute) {
  const hr = Math.floor(minute / 60);
  const base = hr >= 4 && hr <= 11 ? 'صباح الخير' : hr >= 12 && hr <= 16 ? 'هلا والله' : 'مساء الخير';
  return name ? `${base} يا ${name}` : base;
}

export function motivation(c, seed) {
  const f = c.sex === 'FEMALE';
  const pool = [
    'يلا شد الهمة، وخلينا نحسب الأكل ونخلي صحتنا أحسن',
    'كل وجبة تحسبها خطوة لصحة أحسن، يلا نبدي',
    'اليوم فرصة جديدة، خلي همتك عالية',
    f ? 'إنتِ أقوى من أي أكلة، يلا نكمّل الطريق' : 'إنت أقوى من أي أكلة، يلا نكمّل الطريق',
    f ? 'صحتچ تستاهل، وإحنا وياچ خطوة بخطوة' : 'صحتك تستاهل، وإحنا وياك خطوة بخطوة',
    'شوية التزام اليوم، فرق كبير باچر',
    f ? 'هدفچ قريب، بس لا توگفين' : 'هدفك قريب، بس لا توگف',
    'الصحة تاج، وإحنا نبنيها وجبة وجبة',
  ];
  const remaining = c.target - c.consumed;
  const hr = Math.floor(c.minute / 60);
  if (c.consumed > c.target * 1.05) return 'اليوم زدنا شوية، ولا يهمك، باچر نرجع أقوى';
  if (c.streak >= 3 && c.mealsToday === 0) return f ? `${c.streak} أيام ملتزمة ورا بعض، عاشت إيدچ! خلّيها ${c.streak + 1} اليوم` : `${c.streak} أيام ملتزم ورا بعض، عاشت إيدك! خلّيها ${c.streak + 1} اليوم`;
  if (c.mealsToday > 0 && remaining >= 1 && remaining <= 350 && hr >= 17) return `باقيلك ${remaining} سعرة بس، خلّي العشاء خفيف وتختم يومك بطل${f ? 'ة' : ''}`;
  if (c.mealsToday > 0 && remaining > 0) return f ? `ماشية صح! باقيلچ ${remaining} سعرة لليوم` : `ماشي صح! باقيلك ${remaining} سعرة لليوم`;
  return pool[((seed % pool.length) + pool.length) % pool.length];
}

/** مفتاح موحّد لاسم الأكلة. */
export function dishKey(name) {
  return String(name || '').trim().toLowerCase()
    .replace(/[ً-ٟـ]/g, '')
    .replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه').replace(/ى/g, 'ي')
    .replace(/\s+/g, ' ');
}
