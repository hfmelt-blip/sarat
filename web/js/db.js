// تخزين محلي بـ IndexedDB. البيانات صغيرة، فنحملها كلها بالذاكرة ونكتب كل تغيير فوراً.

const DB_NAME = 'sarat';
const VERSION = 1;
const STORES = ['kv', 'meals', 'weights', 'recipes', 'shopping', 'corrections'];

let db;
export const data = {
  kv: {},          // profile, apiKey, prefs...
  meals: [],       // {id, day, minute, title, calories, proteinG, carbsG, fatG, verdict, verdictReason, suggestion, items, source, favorite, thumb, full, liked}
  weights: [],     // {id, day, kg}
  recipes: [],     // {id, title, source, recipe, createdAt}
  shopping: [],    // {id, name, amount, done, createdAt}
  corrections: [], // {key, title, items, updatedAt}
};

function open() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, VERSION);
    req.onupgradeneeded = () => {
      const d = req.result;
      for (const s of STORES) {
        if (!d.objectStoreNames.contains(s)) {
          d.createObjectStore(s, { keyPath: s === 'kv' ? 'k' : s === 'corrections' ? 'key' : 'id' });
        }
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function all(store) {
  return new Promise((resolve, reject) => {
    const req = db.transaction(store).objectStore(store).getAll();
    req.onsuccess = () => resolve(req.result || []);
    req.onerror = () => reject(req.error);
  });
}

function tx(store, fn) {
  return new Promise((resolve, reject) => {
    const t = db.transaction(store, 'readwrite');
    fn(t.objectStore(store));
    t.oncomplete = () => resolve();
    t.onerror = () => reject(t.error);
  });
}

export async function init() {
  db = await open();
  for (const s of STORES) {
    const rows = await all(s);
    if (s === 'kv') data.kv = Object.fromEntries(rows.map((r) => [r.k, r.v]));
    else data[s] = rows;
  }
  // نطلب من المتصفح ما يمسح البيانات تلقائياً
  try { if (navigator.storage && navigator.storage.persist) navigator.storage.persist(); } catch (_) {}
}

export function getKv(k, def = null) { return k in data.kv ? data.kv[k] : def; }
export async function setKv(k, v) {
  data.kv[k] = v;
  await tx('kv', (s) => s.put({ k, v }));
}

export async function put(store, row) {
  const list = data[store];
  const keyName = store === 'corrections' ? 'key' : 'id';
  const i = list.findIndex((r) => r[keyName] === row[keyName]);
  if (i >= 0) list[i] = row; else list.push(row);
  await tx(store, (s) => s.put(row));
}

export async function remove(store, key) {
  const keyName = store === 'corrections' ? 'key' : 'id';
  data[store] = data[store].filter((r) => r[keyName] !== key);
  await tx(store, (s) => s.delete(key));
}

/** نسخة احتياطية كاملة كـ JSON. */
export function exportAll() {
  const kv = { ...data.kv };
  delete kv.apiKey; // المفتاح ما يطلع ويا النسخة
  return JSON.stringify({ app: 'sarat', version: 1, exportedAt: new Date().toISOString(), ...data, kv }, null, 0);
}

export async function importAll(text) {
  const obj = JSON.parse(text);
  if (obj.app !== 'sarat') throw new Error('هذا الملف مو نسخة من «شد الهمة»');
  for (const s of STORES) {
    await tx(s, (st) => st.clear());
    if (s === 'kv') {
      const key = data.kv.apiKey;
      data.kv = obj.kv || {};
      if (key) data.kv.apiKey = key;
      await tx('kv', (st) => Object.entries(data.kv).forEach(([k, v]) => st.put({ k, v })));
    } else {
      data[s] = Array.isArray(obj[s]) ? obj[s] : [];
      await tx(s, (st) => data[s].forEach((r) => st.put(r)));
    }
  }
}
