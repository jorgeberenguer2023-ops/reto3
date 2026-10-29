# NoMolesten

Aplicación Android para identificar llamadas con la agenda del teléfono, aplicar reglas de bloqueo y consultar el registro de llamadas.

## Funciones

- La primera vez muestra la importación de contactos. Después de importar, las siguientes aperturas comienzan en el registro de llamadas.
- Guarda localmente el nombre y los números de los contactos importados para identificarlos en el aviso, la pantalla de llamada y el registro.
- No muestra la lista blanca en pantalla. La importación no convierte automáticamente toda la agenda en lista blanca; la regla «Bloquear todos excepto mis contactos» sí la utiliza.
- Permite configurar el horario de bloqueo, elegir contactos importados y añadir números concretos como excepciones del horario.
- Muestra los números bloqueados en una sección desplegable. «No mostrar» oculta la lista sin desbloquear los números; una importación posterior vuelve a mostrarla.
- Registra llamadas entrantes filtradas como recibidas o bloqueadas, y separa las llamadas realizadas en el registro.
- En el filtro «Bloqueadas» ofrece un acceso a la aplicación Teléfono para revisar el buzón del operador y un acceso para gestionar los números bloqueados.
- Incluye un asistente local por texto y voz para consultar el registro guardado en el teléfono. No genera respuestas con un modelo en la nube ni accede a audios o transcripciones del buzón.
- Al terminar una llamada realizada, muestra una notificación para bloquear el número o guardarlo en Contactos con un nombre.
- Permite mostrar un aviso emergente de llamada y ofrece accesos a los ajustes de notificación de Android.

## Límite de WhatsApp

Android y WhatsApp no proporcionan a esta aplicación acceso a los miembros ni a los nombres de los grupos de WhatsApp. No se pueden importar automáticamente números de grupos ni mostrar un grupo como identificador de llamada. NoMolesten utiliza los nombres y números guardados en la agenda del teléfono. Si un contacto de WhatsApp también está guardado en la agenda, se mostrará ese nombre.

## Buzón de voz del operador

NoMolesten rechaza las llamadas que coinciden con las reglas activas. Que una llamada rechazada termine en el buzón depende del dispositivo y del operador; comprueba la configuración del buzón con tu compañía. En «Registro de llamadas > Bloqueadas» puedes abrir la aplicación Teléfono y gestionar los números bloqueados. El audio, el saludo, la reproducción y el borrado de mensajes pertenecen al buzón del operador y no son accesibles desde NoMolesten. Para que quien llama oiga una pregunta o un saludo, configúralo en el buzón del operador si este lo permite.

## Asistente de llamadas

Abre **Asistente de llamadas** desde el menú superior o desde el registro. Permite escribir preguntas o dictarlas, consulta los datos locales de llamadas y puede leer sus respuestas con la voz de Android. Las respuestas se limitan al número/nombre, hora, dirección y estado guardados; no hay transcripción de la conversación telefónica ni del buzón. El dictado requiere permiso de micrófono y usa el servicio de reconocimiento de voz configurado en Android; cuando el dispositivo lo admite se prefiere su reconocedor local.

Esta integración no incluye la API key compartida en el chat ni llama directamente a Groq. Una clave guardada dentro de un APK se puede extraer. Para habilitar respuestas generativas se necesita un backend HTTPS propio que proteja la clave en el servidor; no se ha configurado ninguno en este proyecto.

## Requisitos y configuración en el móvil

- Android 10 o posterior.
- Para filtrar llamadas entrantes antes de que se contesten, concede el rol **Filtrado de llamadas**.
- Para mostrar la pantalla propia de llamada, bloquear y colgar una llamada en curso y registrar llamadas realizadas, concede también el rol **Aplicación de teléfono predeterminada**. Android reemplazará el marcador actual hasta que elijas otra aplicación en Ajustes.
- En Android 13 o posterior, permite las notificaciones. Para los avisos emergentes, entra en **Reglas de bloqueo** y abre los ajustes del canal y de pantalla completa; activa las notificaciones emergentes si el fabricante ofrece esa opción.
- La app solicita acceso a contactos para importarlos y mostrar sus nombres. Ese permiso no da acceso a grupos de WhatsApp.

## Compilar desde la terminal de Visual Studio Code (PowerShell)

Abre una terminal en la carpeta del proyecto y ejecuta:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

El APK de depuración queda en:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Se requiere Android Studio/Android SDK instalado, con la plataforma Android 35 y las herramientas de compilación. En Android Studio se puede crear un dispositivo virtual desde **Device Manager**.

## Instalar y ejecutar en un emulador Android

En la primera terminal, lista los emuladores disponibles y arranca el nombre mostrado por el primer comando:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
& "$env:ANDROID_HOME\emulator\emulator.exe" -list-avds
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd "Emulado"
```

Deja esa terminal abierta mientras se inicia el emulador. En otra terminal, desde la carpeta del proyecto, instala y abre NoMolesten:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb devices
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n es.berenguer.protectorllamadas400/.MainActivity
```

Si no has compilado antes, ejecuta primero los comandos de compilación. Sustituye `NOMBRE_DEL_EMULADOR` por uno de los nombres que devuelve `-list-avds`.

## Instalar y ejecutar en un móvil conectado por USB

1. Activa **Opciones para desarrolladores** y **Depuración USB** en el móvil.
2. Conéctalo al ordenador y acepta en el móvil el aviso de autorización de depuración.
3. Desde la terminal de Visual Studio Code, en la carpeta del proyecto, ejecuta:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb devices
& $adb install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n es.berenguer.protectorllamadas400/.MainActivity
```

En `adb devices`, el teléfono debe aparecer como `device`. Si hay más de un dispositivo conectado, indica el identificador que aparece en esa lista:

```powershell
& $adb -s "IDENTIFICADOR_DEL_DISPOSITIVO" install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb -s "IDENTIFICADOR_DEL_DISPOSITIVO" shell am start -n es.berenguer.protectorllamadas400/.MainActivity
```

Si aparece `unauthorized`, desbloquea el móvil y acepta el diálogo de depuración USB. Para actualizar una instalación existente, conserva `-r` en `adb install -r`.

## Probar llamadas

- En un dispositivo virtual, crea contactos de prueba desde la aplicación Contactos del emulador. El emulador no importa los grupos de WhatsApp.
- En un móvil, importa la agenda desde la pantalla inicial. La importación también está disponible en el menú de tres puntos.
- Para ver llamadas filtradas, activa el rol de filtrado de llamadas y configura las reglas y el horario.
- Para la pantalla y el registro de llamadas realizadas, activa además el rol de teléfono predeterminado. La confirmación de este rol la muestra Android.
- Las acciones tras una llamada realizada aparecen en una notificación: **Bloquear** guarda el número para futuras llamadas y **Guardar con nombre** abre el formulario de Contactos.

## Privacidad

La agenda importada y el registro se almacenan localmente en el dispositivo. La aplicación no lee conversaciones, mensajes ni integrantes de grupos de WhatsApp.
