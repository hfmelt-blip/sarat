// جدول كل الوجبات السابقة مع مكونات كل وجبة.

import { data } from './db.js';
import { h, icon, fullDate, timeLabel, header } from './ui.js';
import { route, back, rerender, session } from './nav.js';

const g = (v) => Math.round(v || 0);

function tableRow(cells, cls) {
  return h('tr', { class: cls || '' }, cells.map((c, i) => h(i === 0 ? 'th' : 'td', { scope: i === 0 ? 'row' : null }, c)));
}

route('meals', () => {
  if (!session.mealsOpen) session.mealsOpen = {};
  const open = session.mealsOpen;
  const meals = [...data.meals].sort((a, b) => b.day - a.day || b.minute - a.minute || b.id - a.id);
  const days = [];
  for (const m of meals) {
    let d = days[days.length - 1];
    if (!d || d.day !== m.day) { d = { day: m.day, meals: [] }; days.push(d); }
    d.meals.push(m);
  }
  const sum = (list, k) => list.reduce((a, m) => a + (m[k] || 0), 0);

  return h('main', { class: 'screen no-tabs stack' },
    header('كل وجباتي', 'اضغط على أي وجبة حتى تشوف مكوناتها', () => back('history')),
    days.length ? null : h('div', { class: 'small muted' }, 'ما كو وجبات مسجلة بعد.'),
    days.map((d) => h('div', { class: 'stack-s' },
      h('div', { style: { marginTop: '8px' } },
        h('h3', null, fullDate(d.day)),
        h('div', { class: 'xs muted' }, `${sum(d.meals, 'calories')} سعرة · بروتين ${g(sum(d.meals, 'proteinG'))}غ · كارب ${g(sum(d.meals, 'carbsG'))}غ · دهون ${g(sum(d.meals, 'fatG'))}غ`)),
      d.meals.map((m) => {
        const isOpen = !!open[m.id];
        return h('div', { class: 'card tight stack-s' },
          h('button', { class: 'row', style: { background: 'none', border: 0, padding: 0, textAlign: 'start', width: '100%' }, 'aria-expanded': String(isOpen), onclick: () => { open[m.id] = !isOpen; rerender(); } },
            h('div', { class: 'grow' },
              h('div', { class: 'semi ellipsis' }, m.title),
              h('div', { class: 'xs muted' }, `${timeLabel(m.minute)} · ${m.calories} سعرة · ب ${g(m.proteinG)} · ك ${g(m.carbsG)} · د ${g(m.fatG)}`)),
            h('span', { style: { transform: isOpen ? 'rotate(-90deg)' : 'rotate(90deg)', color: 'var(--muted)', display: 'inline-flex' } }, icon('back'))),
          isOpen ? h('table', { class: 'mtable' },
            h('thead', null, tableRow(['المكوّن', 'سعرات', 'بروتين', 'كارب', 'دهون'], 'head')),
            h('tbody', null,
              (m.items || []).map((i) => tableRow([i.name + (i.portion ? ` (${i.portion})` : ''), i.calories, g(i.proteinG), g(i.carbsG), g(i.fatG)])),
              tableRow(['المجموع', m.calories, g(m.proteinG), g(m.carbsG), g(m.fatG)], 'total'))) : null);
      }))),
  );
});
