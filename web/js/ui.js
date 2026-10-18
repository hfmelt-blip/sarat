// أدوات بناء الواجهة: عناصر، أيقونات، تواريخ، رسائل

export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  if (attrs) {
    for (const [k, v] of Object.entries(attrs)) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
      else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
      else if (k === 'value') el.value = v;
      else if (k === 'checked') el.checked = !!v;
      else if (k === 'html') el.innerHTML = v;
      else el.setAttribute(k, v === true ? '' : v);
    }
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const c of children) {
    if (c == null || c === false || c === true) continue;
    if (Array.isArray(c)) append(el, c);
    else if (c instanceof Node) el.appendChild(c);
    else el.appendChild(document.createTextNode(String(c)));
  }
}

const ICONS = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5"/>',
  calendar: '<rect x="3" y="4.5" width="18" height="16.5" rx="2.5"/><path d="M3 9.5h18M8 2.5v4M16 2.5v4"/><path d="M7.5 13.5h2M11 13.5h2M14.5 13.5h2M7.5 17h2M11 17h2"/>',
  chart: '<path d="M3 17l6-6 4 4 8-8"/><path d="M15 7h6v6"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7"/>',
  camera: '<path d="M4 7.5h3l1.8-2.5h6.4L17 7.5h3a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8.5a1 1 0 0 1 1-1z"/><circle cx="12" cy="13.5" r="3.8"/>',
  pencil: '<path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4z"/><path d="M13.5 6.5l4 4"/>',
  note: '<path d="M4 6h12M4 11h12M4 16h7"/><path d="M14 20l1-3.5 5-5 2.5 2.5-5 5L14 20z"/>',
  utensils: '<path d="M7 3v8M4 3v5a3 3 0 0 0 6 0V3M7 11v10"/><path d="M17 21V3c-2.2 1.2-3.5 3.6-3.5 7v3H17"/>',
  book: '<path d="M4 4.5A1.5 1.5 0 0 1 5.5 3H11v16H5.5A1.5 1.5 0 0 0 4 20.5v-16z"/><path d="M20 4.5A1.5 1.5 0 0 0 18.5 3H13v16h5.5a1.5 1.5 0 0 1 1.5 1.5v-16z"/>',
  cart: '<circle cx="9" cy="20" r="1.3"/><circle cx="18" cy="20" r="1.3"/><path d="M2.5 3.5h3l2.4 12h11l2-8.5H6.5"/>',
  share: '<circle cx="18" cy="5" r="2.6"/><circle cx="6" cy="12" r="2.6"/><circle cx="18" cy="19" r="2.6"/><path d="M8.3 10.8l7.4-4.4M8.3 13.2l7.4 4.4"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
  star: '<path d="M12 3.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.8l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8L12 3.5z"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
  back: '<path d="M9 5l7 7-7 7"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  sparkle: '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z"/><path d="M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15z"/>',
  timer: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2.5M9.5 2.5h5"/>',
  thumbUp: '<path d="M7 11v9H4v-9h3z"/><path d="M7 11l4-8a2.5 2.5 0 0 1 2.5 2.8L13 9h5.5a2 2 0 0 1 2 2.4l-1.4 7A2 2 0 0 1 17.1 20H7"/>',
  thumbDown: '<path d="M7 13V4H4v9h3z"/><path d="M7 13l4 8a2.5 2.5 0 0 0 2.5-2.8L13 15h5.5a2 2 0 0 0 2-2.4l-1.4-7A2 2 0 0 0 17.1 4H7"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
  download: '<path d="M12 4v11M7 10.5l5 5 5-5M4 20h16"/>',
  upload: '<path d="M12 16V5M7 9.5l5-5 5 5M4 20h16"/>',
  scale: '<rect x="3.5" y="3.5" width="17" height="17" rx="4"/><path d="M8.5 9a5 5 0 0 1 7 0l-2.3 2.6a1.6 1.6 0 0 0-2.4 0L8.5 9z"/>',
  key: '<circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M16 7l2.5 2.5M18.5 4.5L21 7"/>',
  flame: '<path d="M12 3c3 3.5 6 6 6 10.5A6 6 0 0 1 6 13.5c0-2.7 1.6-4.6 3.2-6.2.4 1.9 1.4 3.1 2.7 3.5-.6-2.9-.5-5.6.1-7.8z"/>',
  bookmark: '<path d="M6 3.5h12V21l-6-4-6 4V3.5z"/>',
  refresh: '<path d="M20 11a8 8 0 0 0-14.5-4.5L4 8"/><path d="M4 4v4h4"/><path d="M4 13a8 8 0 0 0 14.5 4.5L20 16"/><path d="M20 20v-4h-4"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.5"/>',
  edit: '<path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4z"/>',
  moon: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  iosShare: '<path d="M12 3v12M8 7l4-4 4 4"/><path d="M6 11H5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-1"/>',
};

export function icon(name, extra = {}) {
  const fill = extra.fill ? 'currentColor' : 'none';
  const span = document.createElement('span');
  span.style.display = 'inline-flex';
  span.innerHTML = `<svg viewBox="0 0 24 24" fill="${fill}" stroke="currentColor" stroke-width="${extra.sw || 1.8}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name] || ''}</svg>`;
  const svg = span.firstChild;
  const size = extra.size || 20;
  svg.setAttribute('width', size); svg.setAttribute('height', size);
  svg.style.flex = 'none';
  if (extra.size) { svg.style.width = size + 'px'; svg.style.height = size + 'px'; }
  return svg;
}

// ───── تواريخ (يوم محلي كرقم) ─────

export function epochDay(d = new Date()) {
  return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000);
}
export function dayToDate(day) {
  const u = new Date(day * 86400000);
  return new Date(u.getUTCFullYear(), u.getUTCMonth(), u.getUTCDate());
}
export function nowMinute() { const d = new Date(); return d.getHours() * 60 + d.getMinutes(); }

export const MONTHS = ['كانون الثاني', 'شباط', 'آذار', 'نيسان', 'أيار', 'حزيران', 'تموز', 'آب', 'أيلول', 'تشرين الأول', 'تشرين الثاني', 'كانون الأول'];
export const DAYS = ['الأحد', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];

export function fullDate(day) {
  const d = dayToDate(day);
  return `${DAYS[d.getDay()]} ${d.getDate()} ${MONTHS[d.getMonth()]}`;
}
export function shortDate(day) {
  const d = dayToDate(day);
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
}
export function timeLabel(minute) {
  let hh = Math.floor(minute / 60), mm = minute % 60;
  const pm = hh >= 12;
  hh = hh % 12 || 12;
  return `${hh}:${String(mm).padStart(2, '0')} ${pm ? 'م' : 'ص'}`;
}
export function hhmm(m) { return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`; }

export function fmt1(v) {
  const r = Math.round(v * 10) / 10;
  return Number.isInteger(r) ? String(r) : r.toFixed(1);
}

/** يحوّل الأرقام العربية الهندية إلى أرقام عادية ويرجع رقم أو NaN. */
export function parseNum(s) {
  if (s == null) return NaN;
  const t = String(s).trim().replace(/[٠-٩]/g, (c) => c.charCodeAt(0) - 1632).replace(/[۰-۹]/g, (c) => c.charCodeAt(0) - 1776).replace('٫', '.').replace(',', '.');
  if (t === '') return NaN;
  return Number(t);
}

// ───── رسائل ونوافذ ─────

let toastTimer;
export function toast(text) {
  document.querySelectorAll('.toast').forEach((t) => t.remove());
  const el = h('div', { class: 'toast', role: 'status' }, text);
  document.body.appendChild(el);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.remove(), 2600);
}

export function modal(build) {
  const close = () => bg.remove();
  const box = h('div', { class: 'modal', role: 'dialog' });
  const bg = h('div', { class: 'modal-bg', onclick: (e) => { if (e.target === bg) close(); } }, box);
  build(box, close);
  document.body.appendChild(bg);
  return close;
}

export function confirmBox(title, okText, onOk, danger = true) {
  modal((box, close) => {
    box.append(h('div', { class: 'stack' },
      h('h3', null, title),
      h('div', { class: 'row' },
        h('button', { class: 'btn outline grow', onclick: close }, 'إلغاء'),
        h('button', { class: 'btn grow', style: danger ? { background: 'var(--red)' } : null, onclick: () => { close(); onOk(); } }, okText),
      ),
    ));
  });
}

export function verdictBadge(v) {
  const label = { GOOD: 'جيدة', OK_WITH_CHANGES: 'مقبولة مع تعديل', NOT_SUITABLE: 'غير مناسبة' }[v] || 'مقبولة مع تعديل';
  return h('span', { class: 'badge ' + (label === 'جيدة' ? 'GOOD' : v === 'NOT_SUITABLE' ? 'NOT_SUITABLE' : 'OK_WITH_CHANGES') }, label);
}

export function header(title, subtitle, onBack, actions = []) {
  return h('div', { class: 'header' },
    onBack ? h('button', { class: 'icon-btn', 'aria-label': 'رجوع', onclick: onBack }, icon('back')) : null,
    h('div', { class: 'grow' }, h(onBack ? 'h2' : 'h1', null, title), subtitle ? h('div', { class: 'small muted' }, subtitle) : null),
    ...actions,
  );
}

export function chips(options, selected, onPick) {
  return h('div', { class: 'chips', role: 'radiogroup' },
    options.map(([value, label]) => h('button', {
      class: 'chip' + (value === selected ? ' on' : ''), role: 'radio', 'aria-checked': String(value === selected),
      onclick: () => onPick(value),
    }, label)));
}

/** مشاركة نص: قائمة المشاركة بالآيفون، وإذا ما متوفرة يفتح واتساب. */
export async function shareText(text) {
  if (navigator.share) {
    try { await navigator.share({ text }); return; } catch (e) { if (e && e.name === 'AbortError') return; }
  }
  location.href = 'https://wa.me/?text=' + encodeURIComponent(text);
}

export function uid() { return Date.now() * 1000 + Math.floor(Math.random() * 1000); }
