# Protector de llamadas 400 — V2

## Cambios realizados

- Se ha añadido una lista de números bloqueados.
- Cada número tiene un botón **DESBLOQUEAR**.
- Al desbloquear un número, pasa a la **lista blanca**.
- La lista blanca tiene prioridad sobre el bloqueo de 400.
- Se normalizan números escritos como `+34 600123456`, `0034 600123456`,
  `34600123456` y `600123456`.
- La misma normalización se aplica al número recibido por Android; un número
  extranjero no se convierte recortando sus últimos nueve dígitos.
- La aplicación muestra claramente si Android ha concedido el rol de
  filtrado de llamadas.
- La interfaz es desplazable.
- Se pueden permitir llamadas por prefijo internacional; al configurar al
  menos uno, se bloquean los números de otros prefijos y los números cuyo país
  no pueda identificarse.
- El selector de países usa los metadatos de numeración de libphonenumber e
  incluye los prefijos de países, territorios y redes internacionales
  disponibles en esa base.
- El menú de tres puntos contiene la pantalla de protección, la importación de
  contactos, las reglas de bloqueo y el registro de llamadas.
- Se pueden importar todos los teléfonos de la agenda a la lista blanca. Android
  solicita permiso de lectura de contactos antes de acceder a ellos.
- Las reglas de bloqueo permiten rechazar números privados, bloquear todos los
  números que no estén en la agenda, bloquear un contacto concreto y bloquear
  números por prefijo.
- Se muestra el logotipo del teléfono en la aplicación y como icono de la app.
  Se puede activar una notificación emergente de llamada con una acción para
  bloquear ese número en llamadas futuras. Android no permite que la app inserte
  controles en la pantalla de llamada del sistema.
- Android 13 o posterior requiere permiso de notificaciones para mostrar el
  aviso de llamada.
- Las decisiones de filtrado se guardan localmente y se pueden exportar como
  CSV compatible con Excel.
- El horario semanal puede activarse por días y franja horaria. Durante la
  franja configurada se aplican las reglas existentes; fuera de ella se
  permiten las llamadas. También admite intervalos que cruzan medianoche.

## Por qué antes podían seguir entrando las llamadas

Guardar un número en una lista no es suficiente. Android debe haber concedido
a la aplicación el rol `ROLE_CALL_SCREENING`. Si no aparece:

`✓ PROTECCIÓN ACTIVA`

la aplicación no está autorizada a rechazar llamadas.

Para probar un bloqueo manual, añade el número, concede el rol y verifica que
aparece `✓ PROTECCIÓN ACTIVA`. Los números ocultos o no disponibles no pueden
coincidir con una entrada manual.

## Lógica

LISTA BLANCA -> permitir

NÚMERO BLOQUEADO -> rechazar

PREFIJO INTERNACIONAL NO PERMITIDO -> rechazar

NÚMERO PRIVADO (si se activa la regla) -> rechazar

PREFIJO BLOQUEADO (si se configura) -> rechazar

400 + bloqueo 400 activado -> rechazar

resto -> permitir

El registro local guarda número, fecha/hora y resultado (permitida o bloqueada).
Se conserva un máximo de 10.000 llamadas y no se envía a ningún servidor.
