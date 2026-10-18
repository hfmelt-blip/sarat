// تنقّل بسيط بالـ hash، وإعادة رسم الشاشة الحالية.

import { h, icon } from './ui.js';

const routes = {};
export const session = {}; // حالة مؤقتة بين الشاشات (صورة الوجبة، الاقتراح...)

export function route(name, fn) { routes[name] = fn; }

export function parse() {
  const raw = location.hash.replace(/^#\/?/, '') || 'home';
  const [path, query] = raw.split('?');
  const parts = path.split('/');
  const params = Object.fromEntries(new URLSearchParams(query || ''));
  return { name: parts[0], arg: parts[1], params };
}

export function go(path, replace = false) {
  const url = '#/' + path;
  if (replace) history.replaceState(null, '', url); else history.pushState(null, '', url);
  render(true);
}
export function back(fallback = 'home') {
  if (history.length > 1 && session.navDepth > 0) history.back();
  else go(fallback, true);
}

let lastKey = '';
export function render(scrollTop = false) {
  const r = parse();
  const key = location.hash;
  const fn = routes[r.name] || routes.home;
  const app = document.getElementById('app');
  const y = window.scrollY;
  const focused = document.activeElement && document.activeElement.id;
  app.replaceChildren(fn(r));
  if (scrollTop || key !== lastKey) window.scrollTo(0, 0); else window.scrollTo(0, y);
  if (focused) { const el = document.getElementById(focused); if (el) el.focus({ preventScroll: true }); }
  lastKey = key;
}
export const rerender = () => render(false);

window.addEventListener('popstate', () => { session.navDepth = Math.max(0, (session.navDepth || 0) - 1); render(true); });
const push = history.pushState.bind(history);
history.pushState = (...a) => { session.navDepth = (session.navDepth || 0) + 1; push(...a); };

export const TABS = [
  ['home', 'اليوم', 'home'],
  ['history', 'السجل', 'calendar'],
  ['progress', 'التقدم', 'chart'],
  ['profile', 'الملف', 'user'],
];

export function tabs(active) {
  return h('nav', { class: 'tabs' }, TABS.map(([r, label, ic]) =>
    h('button', { class: 'tab' + (r === active ? ' on' : ''), onclick: () => { if (r !== active) go(r, true); } },
      h('span', { class: 'pill' }, icon(ic)), label)));
}

/** شاشة مع شريط التبويبات. */
export function tabScreen(active, ...children) {
  return h('div', null, h('main', { class: 'screen stack' }, ...children), tabs(active));
}
