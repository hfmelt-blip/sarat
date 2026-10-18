// يخلّي التطبيق ينفتح بدون إنترنت. غيّر الرقم مع كل تحديث.
const VERSION = 'sarat-v1';
const FILES = [
  './', 'index.html', 'manifest.webmanifest', 'css/app.css?v=1',
  'js/app.js?v=1', 'js/ui.js', 'js/db.js', 'js/nav.js', 'js/store.js', 'js/nutrition.js', 'js/gemini.js', 'js/recipes.js', 'js/screens.js', 'js/kitchen.js',
  'fonts/tajawal_regular.ttf', 'fonts/tajawal_medium.ttf', 'fonts/tajawal_bold.ttf', 'fonts/tajawal_extrabold.ttf',
  'icons/apple-touch-icon.png', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/favicon-32.png',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return; // طلبات Gemini تروح مباشرة
  // الشبكة أولاً حتى توصل التحديثات، وإذا ماكو نت نرجع للنسخة المحفوظة
  e.respondWith(
    fetch(e.request).then((res) => {
      const copy = res.clone();
      caches.open(VERSION).then((c) => c.put(e.request, copy));
      return res;
    }).catch(() => caches.match(e.request, { ignoreSearch: false }).then((r) => r || caches.match('index.html')))
  );
});
