/* Puente APK <-> app web. Se inyecta al servir index.html; el fichero
   index.html original no se toca. Solo actua si existe MiParteNative.

   La app llama ademas a estos metodos nativos DIRECTAMENTE, sin pasar por
   este fichero (estan en MainActivity.java, clase Puente):
     carpetaOneDrive()            nombre de la carpeta elegida, o ""
     eligeCarpetaOneDrive()       abre el selector de carpetas de Android
     guardaOneDrive(b64, nombre)  escribe el PDF en esa carpeta
     guardaCopia(b64, nombre)     copia de seguridad -> subcarpeta "Copias"
     escalaSistema()              tamaño de letra del telefono (1.0, 1.3...)
     ponZoom(pct)                 escala el texto de la app
   Si alguno falta, la app se apaña sola: comparte el PDF en vez de guardarlo
   y deja la letra como esta. */
(function () {
  var N = window.MiParteNative;
  if (!N) return;

  function b64(blob) {
    return new Promise(function (ok, ko) {
      var r = new FileReader();
      r.onload = function () { var s = String(r.result); ok(s.slice(s.indexOf(',') + 1)); };
      r.onerror = ko;
      r.readAsDataURL(blob);
    });
  }

  /* 1. Service Worker. En el APK no hace falta (los ficheros ya son locales) y
     ademas navigator.serviceWorker.ready no resuelve nunca en WebView, lo que
     colgaria el flujo de avisos. Lo ocultamos. */
  try {
    delete Navigator.prototype.serviceWorker;
  } catch (e) { }
  if ('serviceWorker' in navigator) {
    // Si no se ha podido borrar, dejamos un doble que RECHAZA. Nunca uno que
    // se quede colgado: la app hace await navigator.serviceWorker.ready y un
    // promise pendiente para siempre congelaria el flujo de avisos.
    try {
      var falla = function () { return Promise.reject(new Error('sin service worker en el APK')); };
      Object.defineProperty(navigator, 'serviceWorker', {
        configurable: true,
        get: function () { return { register: falla, getRegistration: falla, get ready() { return falla(); } }; }
      });
    } catch (e) { }
  }

  /* 2. Compartir el PDF -> hoja de compartir de Android (OneDrive, WhatsApp...) */
  navigator.canShare = function (d) { return !!(d && d.files && d.files.length); };
  navigator.share = function (d) {
    if (!d || !d.files || !d.files.length) {
      var err = new Error('nada que compartir'); err.name = 'TypeError';
      return Promise.reject(err);
    }
    var f = d.files[0];
    return b64(f).then(function (data) {
      if (!N.sharePdf(data, f.name || 'parte.pdf', f.type || 'application/pdf')) {
        var e = new Error('no se pudo compartir'); e.name = 'NotAllowedError'; throw e;
      }
    });
  };

  /* 3. Guardar el PDF: <a download> con blob: -> carpeta Descargas */
  document.addEventListener('click', function (ev) {
    var a = ev.target && ev.target.closest ? ev.target.closest('a[download]') : null;
    if (!a) return;
    var href = a.getAttribute('href') || '';
    if (href.indexOf('blob:') !== 0 && href.indexOf('data:') !== 0) return;
    ev.preventDefault();
    ev.stopImmediatePropagation();
    fetch(href).then(function (r) { return r.blob(); }).then(b64).then(function (data) {
      N.savePdf(data, a.getAttribute('download') || 'parte.pdf');
    }).catch(function (e) { N.toast('No se ha podido guardar el PDF'); });
  }, true);

  /* 3 bis. Enlaces externos: mapa de la obra y telefonos de contacto.
     El WebView no los abre solo; hay que pasarlos a Android. */
  var ESQUEMAS = /^(https?|tel|geo|mailto|sms):/i;
  document.addEventListener('click', function (ev) {
    var a = ev.target && ev.target.closest ? ev.target.closest('a[href]') : null;
    if (!a) return;
    var href = a.getAttribute('href') || '';
    if (!href || href === '#' || href.charAt(0) === '#') return;
    if (href.indexOf('blob:') === 0 || href.indexOf('data:') === 0) return;
    if (!ESQUEMAS.test(href)) return;
    if (/^https?:/i.test(href) && href.indexOf(location.origin) === 0) return;
    ev.preventDefault();
    ev.stopImmediatePropagation();
    N.abrirFuera(href);
  }, true);

  /* 4. Notificaciones. El WebView no trae la Notification API: la recreamos
     sobre el canal de notificaciones nativo. */
  /* Se sustituye SIEMPRE, no solo si falta: aunque algun WebView exponga
     Notification, no pinta nada en la barra de Android. */
  {
    var Notif = function (title, opts) {
      opts = opts || {};
      N.notify(String(title || 'Mi Parte'), String(opts.body || ''));
    };
    Object.defineProperty(Notif, 'permission', {
      get: function () { return N.notifPermission(); }
    });
    Notif.requestPermission = function (cb) {
      return new Promise(function (done) {
        var before = N.notifPermission();
        if (before !== 'default') { if (cb) cb(before); return done(before); }
        N.requestNotif();
        var n = 0, iv = setInterval(function () {
          var p = N.notifPermission();
          if (p !== 'default' || ++n > 60) {
            clearInterval(iv);
            if (cb) cb(p);
            done(p);
          }
        }, 250);
      });
    };
    window.Notification = Notif;
  }

  /* 5. Imprimir -> dialogo de impresion de Android */
  window.print = function () { try { N.print(); } catch (e) { } };

  /* 6. Alarma diaria nativa. Volcamos a Android la hora de aviso y si el parte
     de hoy esta cerrado, para que avise aunque la app este cerrada. */
  var ultimo = '';
  /* Los datos ya no viven en localStorage: desde 1.5 estan en IndexedDB
     (base "miparte", almacen "datos", clave "todo") y la app borra la clave
     vieja al mudarlos. Se leen los dos sitios, por si el movil todavia no ha
     mudado. */
  function leeDatos() {
    return new Promise(function (ok) {
      try {
        var raw = localStorage.getItem('miparte:v1');
        if (raw) return ok(JSON.parse(raw));
      } catch (e) { }
      try {
        var rq = indexedDB.open('miparte', 1);
        rq.onsuccess = function () {
          try {
            var d = rq.result;
            if (!d.objectStoreNames.contains('datos')) return ok(null);
            var q = d.transaction('datos', 'readonly').objectStore('datos').get('todo');
            q.onsuccess = function () { ok(q.result || null); };
            q.onerror = function () { ok(null); };
          } catch (e) { ok(null); }
        };
        rq.onerror = function () { ok(null); };
        rq.onblocked = function () { ok(null); };
      } catch (e) { ok(null); }
    });
  }
  function hoyKey() {
    var x = new Date();
    return x.getFullYear() + '-' + String(x.getMonth() + 1).padStart(2, '0') + '-' + String(x.getDate()).padStart(2, '0');
  }
  function sync() {
    leeDatos().then(function (d) { pasa(d); }).catch(function () { });
  }
  function pasa(d) {
    try {
      if (!d || !d.db) return;
      var aj = d.db.ajustes || {};
      var dia = hoyKey();
      var p = (d.partes || {})[dia];
      var cerrado = !!(p && p.entrada && p.salida && p.ops && p.ops.length &&
        (p.trabajos || []).some(function (t) { return t && t.texto; }));
      var dias = (aj.dias || '1111111');
      var horas = (aj.avisos && aj.avisos.length ? aj.avisos : [aj.aviso || '19:00']).join(',');
      var firma = (aj.notif ? '1' : '0') + '|' + horas + '|' + dias + '|' + (cerrado ? dia : '');
      if (firma === ultimo) return;
      ultimo = firma;
      N.syncAviso(!!aj.notif, horas + '|' + dias, cerrado ? dia : '');
    } catch (e) { }
  }
  setInterval(sync, 4000);
  document.addEventListener('visibilitychange', sync);
  window.addEventListener('pagehide', sync);
  setTimeout(sync, 1500);

  /* 7. Estado de los avisos y prueba real, para Ajustes. Todo lo que puede
     impedir que el aviso llegue se puede ver y arreglar desde la app.
     Si el APK es anterior a la 1.12 estos metodos no existen: cada uno se
     comprueba por separado y la app se apaña sin ellos. */
  window.MiParteSync = function () { ultimo = ''; sync(); };
  window.MiParteAvisos = {
    nativo: true,
    estado: function () {
      try {
        if (!N.estadoAvisos) return null;
        return JSON.parse(N.estadoAvisos());
      } catch (e) { return null; }
    },
    exacto: function () { try { if (N.pideExacto) N.pideExacto(); } catch (e) { } },
    bateria: function () { try { if (N.pideBateria) N.pideBateria(); } catch (e) { } },
    ajustesNotif: function () { try { if (N.ajustesNotif) N.ajustesNotif(); } catch (e) { } },
    prueba: function (segundos) {
      try {
        if (!N.pruebaAlarma) return false;
        window.MiParteSync();
        N.pruebaAlarma(segundos || 60);
        return true;
      } catch (e) { return false; }
    }
  };
})();
