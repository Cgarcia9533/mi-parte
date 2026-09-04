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
  /* 8. QUE EL TECLADO NO TAPE EL CAMPO.

     Desde Android 15, una app que apunta a SDK 35 o mas ya NO se redimensiona
     al salir el teclado: se pinta ENCIMA y el navegador ni se entera. Por eso
     su propio scrollIntoView no sirve: mide contra la ventana entera, ve el
     campo dentro, y decide que ya se ve. No es que falle, es que le mienten.

     Asi que se hace a mano. El envoltorio deja el alto del teclado en la
     variable --teclado; aqui se calcula cuanto sobresale el campo por debajo
     de la raya del teclado y se sube EXACTAMENTE eso, con animacion.

     Reglas:
       - Si el campo ya se ve entero, no se mueve nada. Un salto sin motivo
         marea mas que ayudar.
       - Solo al enfocar o al abrirse el teclado. Despues manda el dedo: si el
         usuario mueve la pantalla, no se le vuelve a tocar.
       - Si el campo esta dentro de una ventana con scroll propio (un dialogo),
         se mueve esa y no la pagina. */
  var CAMPOS = /^(INPUT|TEXTAREA|SELECT)$/;

  /* Las VENTANAS FLOTANTES son otra cosa y hay que tratarlas aparte.
     Un dialogo va pegado al borde de la PANTALLA: mover la pagina de detras no
     lo mueve a el ni un pixel, solo marea. Lo que hay que hacer es subirle el
     SUELO hasta la raya del teclado, y como su panel va pegado abajo, sube con
     el y queda justo encima.

     OJO CON COMO SE BUSCAN. El primer intento fue una regla de CSS que miraba
     el texto del atributo style («inset:0»), y no acerto NUNCA: el navegador
     reescribe ese atributo a su manera, con espacios, y sale «inset: 0px». Hay
     que mirar el estilo YA CALCULADO. Y la regla va por CLASE y con
     «!important», no poniendole el estilo a mano al elemento: React repinta el
     dialogo en cada tecla y le devolveria su «inset:0». */
  var CLASE = 'mp-flota';
  (function ponLaRegla() {
    try {
      var s = document.createElement('style');
      s.id = 'miparte-teclado-css';
      s.textContent = '.' + CLASE + '{bottom:var(--teclado,0px)!important}';
      (document.head || document.documentElement).appendChild(s);
    } catch (e) { }
  })();

  function marcaVentanas() {
    try {
      var d = document.body ? document.body.getElementsByTagName('div') : [];
      for (var i = 0; i < d.length; i++) {
        var e = d[i];
        if (e.classList.contains(CLASE)) continue;
        var c = getComputedStyle(e);
        // a pantalla completa: pegada arriba y abajo. Las que no llegan al
        // suelo no las tapa el teclado y no hay nada que subir.
        if (c.position === 'fixed' && parseFloat(c.top) === 0 && parseFloat(c.bottom) === 0) {
          e.classList.add(CLASE);
        }
      }
    } catch (e) { }
  }

  /* La ventana flotante que contiene el campo, si es que hay alguna. */
  function ventanaDe(el) {
    var p = el;
    while (p && p !== document.body) {
      if (getComputedStyle(p).position === 'fixed') return p;
      p = p.parentElement;
    }
    return null;
  }

  function altoTeclado() {
    var v = 0;
    try {
      v = parseFloat(getComputedStyle(document.documentElement)
            .getPropertyValue('--teclado')) || 0;
    } catch (e) { }
    if (v > 0) return v;
    // Respaldo por si el envoltorio no llega a dar la medida: hay WebViews que
    // si encogen el «visual viewport» aunque la ventana siga igual de alta.
    try {
      var vv = window.visualViewport;
      if (vv && window.innerHeight - vv.height > 120) {
        return Math.round(window.innerHeight - vv.height);
      }
    } catch (e) { }
    return 0;
  }

  /* Contenedor con scroll propio entre el campo y «tope» (sin pasarse de el). */
  function cajaConScroll(el, tope) {
    var p = el.parentElement;
    while (p && p !== document.body && p !== document.documentElement) {
      var s = getComputedStyle(p);
      if (/(auto|scroll)/.test(s.overflowY) && p.scrollHeight > p.clientHeight + 2) return p;
      if (p === tope) return null;
      p = p.parentElement;
    }
    return null;
  }

  /* QUE EL TECLADO NO TAPE EL CAMPO.

     Desde Android 15, una app que apunta a SDK 35 o mas ya NO se redimensiona
     al salir el teclado: se pinta ENCIMA y el navegador ni se entera. Por eso
     su scrollIntoView no sirve: mide contra la ventana entera, ve el campo
     dentro y decide que ya se ve. No falla, es que le mienten.

     Reglas:
       - Si el campo ya se ve entero, no se mueve NADA.
       - Solo al enfocar o al salir el teclado. Despues manda el dedo.
       - Dentro de una ventana flotante NO se mueve la pagina: la ventana ya ha
         subido sola con el CSS de arriba. Si la ventana tiene scroll propio, se
         mueve ese. */
  function subeElCampo() {
    var a = document.activeElement;
    if (!a || !CAMPOS.test(a.tagName || '')) return;
    var tec = altoTeclado();
    if (tec <= 0) return;
    marcaVentanas();                       // por si acaba de abrirse una
    var vent = ventanaDe(a);
    var AIRE = 14;                         // que no quede pegado a la raya
    var r = a.getBoundingClientRect();     // esto ya cuenta con la ventana subida
    var sobra = Math.round(r.bottom + AIRE - (window.innerHeight - tec));
    if (sobra <= 0) return;                // ya se ve: quieto
    var caja = cajaConScroll(a, vent);
    if (caja) {
      try { caja.scrollBy({ top: sobra, behavior: 'smooth' }); }
      catch (e) { caja.scrollTop += sobra; }
      return;
    }
    if (vent) return;                      // ventana flotante: no se toca la pagina
    try { window.scrollBy({ top: sobra, behavior: 'smooth' }); }
    catch (e) { window.scrollBy(0, sobra); }
  }

  var tSube = 0;
  function pideSubir() {
    marcaVentanas();
    clearTimeout(tSube);
    tSube = setTimeout(subeElCampo, 90);
  }

  // Al tocar un campo. Si el teclado aun no ha salido, --teclado vale 0 y no se
  // hace nada; ya lo hara el aviso de abajo cuando salga.
  document.addEventListener('focusin', function (ev) {
    var t = ev.target;
    if (t && CAMPOS.test(t.tagName || '')) pideSubir();
  }, true);
  // Al salir el teclado (lo avisa el envoltorio) y por si el WebView encoge solo.
  window.addEventListener('miparte-teclado', pideSubir);
  try { if (window.visualViewport) window.visualViewport.addEventListener('resize', pideSubir); } catch (e) { }

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
