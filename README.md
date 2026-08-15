# Mi Parte

Parte diario de montaje. Se rellena en obra, funciona sin cobertura y los datos
no salen del teléfono.

Pensado para montaje de panel sándwich, pero sirve para cualquier trabajo que se
apunte por días: obra, horario, cuadrilla, trabajos, material, incidencias,
croquis y fotos. Cada día genera un PDF.

## Probarla

Abre la URL en el móvil con Chrome y dale a **Instalar aplicación**. Se queda
en el cajón de aplicaciones como una más y funciona sin conexión.

No hay registro, no hay cuenta, no hay servidor. Todo se guarda en el propio
teléfono.

## Qué hace

- **Parte en seis pasos**: obra, horario, cuadrilla, trabajos, material, cierre
- **Obra** con ubicación en Google Maps y personas de contacto
- **Cuadrilla** con clasificación del equipo y nota del día por operario
- **Material** con dos casillas por línea: recibido y colocado
- **Incidencias, EPIs, croquis y fotos** con hora y zona
- **PDF del día**, generado por la propia app, para compartir o guardar
- **Aviso** a la hora que elijas si el parte del día no está cerrado
- **Pantalla de inicio a tu gusto**: las tarjetas se ordenan y se ocultan
- **Copia de seguridad** a fichero, con fotos o solo el texto

## Las dos versiones

|  | Web (esta) | APK |
|---|---|---|
| Parte, PDF, fotos, copias | sí | sí |
| Sin conexión | sí | sí |
| Guardar en OneDrive de un toque | no | sí |
| Aviso con la app cerrada | no | sí |
| Letra del sistema del móvil | no | sí |

La web cubre lo esencial. El APK añade lo que necesita código nativo.

## Instalar el APK

En [`apk/`](apk/). Pásalo al móvil, ábrelo y acepta instalar de orígenes
desconocidos.

Play Protect avisará de que no conoce al desarrollador. Es lo normal en
cualquier app firmada fuera de Google Play: no dice que sea peligrosa, dice que
no la ha visto antes. Dale a **Instalar de todos modos**.

## Compilar el APK

En `apk/codigo-fuente/` está el código Java del envoltorio nativo. La app web
va en `assets/`.

Se compila sin Gradle ni Capacitor:

```
aapt2 compile --dir res -o flat/res.zip
aapt2 link -o base.apk -I android.jar --manifest AndroidManifest.xml \
      -R flat/res.zip --java gen --auto-add-overlay -A assets \
      --min-sdk-version 24 --target-sdk-version 34
javac -source 8 -target 8 -bootclasspath android.jar -d obj $(find src gen -name '*.java')
d8 --min-api 24 --output . $(find obj -name '*.class')
zip -j base.apk classes.dex && zipalign -f -p 4 base.apk aligned.apk
apksigner sign --ks TU-KEYSTORE --out "Mi Parte.apk" aligned.apk
```

Necesitas tu propio keystore: el de la app original no está aquí, y no debe
estarlo. Si compilas con otro certificado, el APK que salga no se instala
encima del oficial; hay que desinstalar primero.

## Qué hace el envoltorio nativo

La app web es la misma en los dos casos. El APK solo añade un puente
(`apk/puente-nativo.js` más `MainActivity.java`) que tapa lo que un WebView de
Android no da por su cuenta:

- Compartir y guardar el PDF
- Enviar el PDF a OneDrive
- Alarmas exactas para el aviso, con la app cerrada
- La letra que tengas puesta en el móvil, leída de la fuente instalada
- Selector de documentos para cargar una copia

## Estado

Funciona y está en uso. Lo que sé que falla o falta:

- **Las notificaciones programadas no siempre llegan.** Quien le dice a Android
  que el aviso está activo es el puente, releyendo la base de datos cada cuatro
  segundos. Si esa lectura falla, no se programa ninguna alarma.
- La búsqueda de la fuente del sistema cae en Roboto en algunos móviles.
- La fuente se incrusta en base64 y hace la página bastante más pesada al
  arrancar.

## Licencia

MIT. Cógela, cámbiala y úsala.
