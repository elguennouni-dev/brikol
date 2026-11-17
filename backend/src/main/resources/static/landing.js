(function () {
  var html = document.documentElement, btn = document.getElementById('lang');
  html.classList.add('js');
  setTimeout(function () { document.querySelectorAll('.reveal').forEach(function (el) { el.classList.add('in'); }); }, 2500);
  function set(l) {
    html.lang = l; html.dir = l === 'ar' ? 'rtl' : 'ltr'; btn.textContent = l === 'ar' ? 'Français' : 'العربية';
    try { localStorage.setItem('landing-lang', l); localStorage.setItem('s3_lang', l); } catch (e) {}
  }
  var saved = null; try { saved = localStorage.getItem('landing-lang') || localStorage.getItem('s3_lang'); } catch (e) {}
  set(saved || ((navigator.language || '').indexOf('ar') === 0 ? 'ar' : 'fr'));
  btn.addEventListener('click', function () { set(html.lang === 'ar' ? 'fr' : 'ar'); });
  var top = document.getElementById('top');
  addEventListener('scroll', function () { top.classList.toggle('scrolled', scrollY > 8); }, { passive: true });
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (es) { es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }); }, { rootMargin: '0px 0px -10% 0px' });
    document.querySelectorAll('.reveal').forEach(function (el) { io.observe(el); });
  } else { document.querySelectorAll('.reveal').forEach(function (el) { el.classList.add('in'); }); }
  // Android button: active only when the APK is on the server
  var apk = document.getElementById('apk');
  fetch('/downloads/sanai3i.apk', { method: 'HEAD' }).then(function (r) {
    if (r.ok) {
      apk.classList.remove('off');
      apk.querySelector('[data-l=ar]').textContent = 'حمل APK';
      apk.querySelector('[data-l=fr]').textContent = 'Télécharger l’APK';
    }
  }).catch(function () {});
})();
