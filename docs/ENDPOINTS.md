# API de autenticacion: guia de integracion

Describe el codigo actual, incluidos sus defectos pendientes. Laboratorio
intencionalmente vulnerable: no desplegar en Internet ni usar credenciales reales.

URL por defecto: `http://localhost:8080`. Host de desarrollo `127.0.0.1`;
el perfil prod utiliza `0.0.0.0`
([configuracion:1](../src/main/resources/application.properties#L1)).
Requisitos: JDK 21 y Maven 3.9+.

El README indica `mvn quarkus:dev`. Durante esta verificacion el plugin omitio
el arranque por no tener el goal build configurado; para ejecutar sin modificar
el POM se utilizo:

```powershell
mvn quarkus:dev "-Dquarkus.enforceBuildGoal=false"
```

Los ejemplos HTTP de login exitoso, JSON null, JSON malformado, tipo de contenido
incorrecto, Accept incompatible y salud UP se observaron el 2026-10-19 en modo
desarrollo, puerto temporal 18089 y SQLite aislado. No se probo el perfil prod.
Las demas respuestas se justifican con fuentes y pruebas existentes.
Las preguntas abiertas no constituyen contratos inventados.

## POST /auth/login

### 1. Que hace

Comprueba las credenciales de un usuario del laboratorio y devuelve su identidad,
rol y un token para la integracion con consumidores externos.

Ruta y respuesta:
[AuthResource.java:15](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L15),
[AuthResource.java:35](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L35).

### 2. Autenticacion y headers

No requiere autenticacion previa ni header `Authorization`. Las credenciales
se envian en el cuerpo, no mediante HTTP Basic.

```http
POST /auth/login
Content-Type: application/json
Accept: application/json
```

`Content-Type: application/json` identifica el cuerpo. `Accept` puede omitirse;
si se envia, debe admitir `application/json`. El recurso consume y produce JSON
([AuthResource.java:16](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L16)).
No se crea ni devuelve una cookie de sesion desde este recurso.

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| username | string | Si | Usuario; rechaza ausente, null, vacio o solo caracteres eliminados por Java String.trim(). Se utiliza el valor original sin recortar. |
| password | string | Si | Contrasena original; mismas comprobaciones de vacio. No enviar el hash ni el token en este campo. |
| client | string | No | Identificador del cliente; el recurso no lo utiliza. Puede omitirse o ser null. |

Ejemplo completo para el usuario inicial student:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

Fuentes: [LoginRequest.java:3](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3),
[AuthResource.java:26](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26).
Orden de validacion: cuerpo, username y password. Los campos desconocidos se
ignoran ([configuracion:8](../src/main/resources/application.properties#L8)).
No hay limites de longitud declarados por este DTO/recurso; no implica que el
servidor o la infraestructura acepten cuerpos ilimitados.

Credenciales iniciales reales:

| Usuario | Contrasena de laboratorio | Nombre visible | Rol |
|---|---|---|---|
| student | Workshop123! | Workshop Student | STUDENT |
| trainer | Trainer123! | Workshop Trainer | TRAINER |

La version anterior de este documento indicaba otra clave para trainer.
Esta tabla refleja [PasswordHashMigration.java:96](../src/main/java/com/bcp/workshop/auth/PasswordHashMigration.java#L96).
Las semillas no reemplazan usuarios existentes: una base preexistente puede
tener otras credenciales, nombres y roles.

### 4. Response exitosa

**HTTP 200**, `Content-Type: application/json`.

| campo | tipo | descripcion |
|---|---|---|
| token | string | JWT firmado con RS256 para la identidad encontrada; lo valida la API de requisitos con la clave pública correspondiente. |
| expiresIn | number (entero) | Duracion anunciada, siempre 3600 segundos. |
| user | object | Identidad publica; no incluye id, contrasena, hash ni salt. |
| user.username | string | Usuario recuperado de SQLite. |
| user.displayName | string | Nombre visible escapado con HTML4; por ejemplo, un `<` se devuelve como `&lt;`. |
| user.role | string | Rol guardado. Las semillas usan STUDENT o TRAINER; el codigo no impone un enum de roles. |
| debug | string | Siempre `authenticated-with-legacy-flow`. |

Ejemplo completo observado con student:

```json
{
  "expiresIn": 3600,
  "debug": "authenticated-with-legacy-flow",
  "user": {
    "displayName": "Workshop Student",
    "role": "STUDENT",
    "username": "student"
  },
  "token": "<JWT RS256 emitido por el backend>"
}
```

El valor de `token` es ilustrativo, no una credencial válida. Su contenido y
firma cambian con la identidad, el instante de generación y la clave privada.
El orden de propiedades JSON no está garantizado.
Fuente de todos los campos:
[AuthResource.java:39](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L39).
La prueba [AuthResourceTest.java:38](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L38)
comprueba respuesta completa, roles y estructura del token.

### 5. Respuestas de error

Errores producidos por el recurso, con cuerpo JSON:

| codigo | cuando ocurre | ejemplo completo de cuerpo |
|---|---|---|
| 400 | El recurso recibe null; observado enviando JSON `null`. | `{"message":"Request is null"}` |
| 400 | username ausente, null o vacio tras trim(); se comprueba antes de password. | `{"message":"Username is required"}` |
| 400 | username pasa la validacion y password falta, es null o queda vacio tras trim(). | `{"message":"Password is required"}` |
| 401 | No existe el usuario o no coincide su password. Ejemplo solicitado con username student. | `{"message":"Invalid user student or password"}` |
| 401 | Una SQLException en la consulta se registra y devuelve ausencia; mismo cuerpo que credenciales incorrectas. | `{"message":"Invalid user student or password"}` |
| 500 | Un hash almacenado invalido llega a la verificacion del servicio y la excepcion alcanza el catch del recurso. | `{"message":"Invalid stored password format","exception":"java.lang.IllegalStateException"}` |
| 500 | Otro error alcanza el catch del recurso; se muestran su mensaje y clase. Ejemplo controlado de la prueba del recurso. | `{"message":"controlled failure","exception":"java.lang.IllegalStateException"}` |
| 500 | La excepcion capturada no tiene mensaje; caso caracterizado por la prueba del recurso. | `{"message":"Unknown error","exception":"java.lang.IllegalStateException"}` |

Fuentes: [AuthResource.java:26](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26),
[AuthResource.java:54](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L54),
[AuthenticationService.java:30](../src/main/java/com/bcp/workshop/auth/AuthenticationService.java#L30),
[LegacyDatabase.java:61](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L61),
[PasswordHasher.java:40](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L40),
[AuthResourceTest.java:92](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L92).
El mensaje 401 contiene el username recibido. Los valores de message y
exception de un 500 no son una lista cerrada: dependen del fallo.
Los ejemplos de 500 describen ramas de codigo/pruebas, no fallos HTTP inducidos
durante esta verificacion.

Errores del framework antes de entrar al recurso, observados con el servidor
local actual:

| codigo | cuando ocurre | ejemplo de cuerpo observado |
|---|---|---|
| 400 | JSON malformado: cuerpo literal `{` con Content-Type application/json. | Cuerpo vacio, 0 bytes; no es `{}` ni `null`. |
| 415 | Tipo no soportado: cuerpo `text` con Content-Type text/plain. | Cuerpo vacio, 0 bytes. |
| 406 | Accept incompatible: Accept text/plain con solicitud JSON. | Cuerpo vacio, 0 bytes. |

**Preguntas abiertas:** el repositorio no define mappers propios para errores
del framework. No se verificaron todos los errores de conversion de tipos,
cuerpo ausente, limites de transporte, intermediarios ni diferencias por perfil;
no asumir que devuelven el JSON `message` del recurso. Tampoco se fija aqui la
envoltura exacta de excepciones CDI durante una inicializacion fallida.
El cliente debe manejar respuestas sin JSON y fallos de conectividad.

### 6. Notas de seguridad

- El token es un JWT firmado con RS256. Incluye `sub`, `role` y `exp`; la API de
  requisitos valida la firma con la clave pública configurada y rechaza tokens
  vencidos o inválidos.
- La expiración incluida en `exp` y anunciada mediante `expiresIn` es de 3600
  segundos. La API de requisitos valida la expiración junto con la firma.
  Los tokens legacy se invalidan; no hay fallback de compatibilidad.
  No se prescribe un header Bearer para consumidores no inspeccionados.
- El login ahora busca por usuario mediante PreparedStatement y verifica
  PBKDF2-HMAC-SHA256 con 600000 iteraciones y salt aleatorio. La migracion de
  passwords es transaccional; hashes corruptos no activan fallback a texto plano
  ([LegacyDatabase.java:56](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L56),
  [PasswordHasher.java:15](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L15),
  [PasswordHashMigration.java:34](../src/main/java/com/bcp/workshop/auth/PasswordHashMigration.java#L34)).
- CORS admite cualquier origen por configuracion; las respuestas 500 siguen
  exponiendo detalles internos. No registrar passwords ni tokens en clientes.
- No hay limitador de intentos ni autorizacion por rol implementados en el
  recurso/servicio revisados. No inferir controles de infraestructura externos.

## GET /q/health

### 1. Que hace

Permite a un operador o monitor consultar si la aplicacion reporta un estado
saludable, sin realizar un login de usuario.

Lo proporciona Quarkus SmallRye Health, declarado en
[pom.xml:40](../pom.xml#L40), no AuthResource. No se encontraron comprobaciones
de salud personalizadas en las fuentes del componente.

### 2. Autenticacion y headers

No requiere autenticacion en la configuracion actual. Se observo HTTP 200
sin header `Authorization`. No enviar password ni token.

```http
GET /q/health
Accept: application/json
```

Accept es recomendado para consumir JSON; no es necesario Content-Type porque
no se envia cuerpo. No hay reglas HTTP de autenticacion configuradas en
[application.properties](../src/main/resources/application.properties).

### 3. Request

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| Ninguno | No aplica | No | GET sin cuerpo, sin parametros de ruta ni query requeridos por la configuracion actual. |

**Ejemplo completo de solicitud:** `GET /q/health` con los headers del punto 2.
**Ejemplo JSON:** no aplica; el request real tiene 0 bytes de cuerpo.
No enviar `{}` ni `null`: inventaria una entrada JSON que este endpoint no pide.
No hay DTO de entrada de salud en el codigo del proyecto.

### 4. Response exitosa

**HTTP 200**, `Content-Type: application/json`. Cuerpo completo observado:

```json
{
  "status": "UP",
  "checks": []
}
```

| campo | tipo | descripcion |
|---|---|---|
| status | string | Estado global reportado, UP en la respuesta saludable observada. |
| checks | array | Resultados de comprobaciones de salud; vacio en esta aplicacion durante la verificacion. |

No se definen campos de elementos de checks porque no hay elementos ni
comprobaciones propias que permitan verificarlos en este componente.
La estructura procede de la extension, no de un DTO del proyecto.

### 5. Respuestas de error

| codigo | cuando ocurre | ejemplo de cuerpo y alcance de evidencia |
|---|---|---|
| 503 | La extension reporta estado no saludable (DOWN). Comportamiento de SmallRye Health descrito en el documento previo, no una rama implementada por el componente. | **Pregunta abierta:** no se pudo verificar un cuerpo real DOWN; no hay checks propios ni prueba que lo produzcan. No se inventa un JSON con checks o mensajes. |

No se observaron errores de salud durante la consulta local ni hay respuestas
de error personalizadas para esta ruta en las fuentes. El repositorio solo
declara la extension; no permite enumerar exhaustivamente sus errores internos.
**Preguntas abiertas:** cuerpo real de 503, errores internos del framework,
negociacion de contenido y comportamiento bajo fallo de infraestructura.
Su confirmacion requiere caracterizacion adicional de la extension en el
entorno objetivo, no una suposicion sobre AuthResource.
Una conexion rechazada o un timeout no tiene necesariamente codigo HTTP ni
cuerpo JSON: manejarlo como fallo de transporte.

### 6. Notas de seguridad

- `UP` y `checks: []` **no prueban que SQLite funcione ni que el login autentique**.
  El bean de persistencia se inicializa mediante PostConstruct y puede no
  haberse utilizado al consultar salud
  ([LegacyDatabase.java:36](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L36)).
- No devuelve token ni identidad. No usarlo para autorizar usuarios.
- No agregar ni asumir passwords, secretos o detalles de conexion en checks.
  La exposicion de esta ruta debe limitarse al entorno local del laboratorio.

## Documentos y fuentes consultados

Documentos:

- [README.md](../README.md): ejecucion, credenciales, migracion y limitaciones.
- Version anterior de este documento: contrato legacy contrastado con las
  fuentes actuales; se actualizaron los detalles internos obsoletos.

Codigo y configuracion:

- [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java).
- [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java).
- [AuthenticationService.java](../src/main/java/com/bcp/workshop/auth/AuthenticationService.java).
- [LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java).
- [PasswordHashMigration.java](../src/main/java/com/bcp/workshop/auth/PasswordHashMigration.java).
- [PasswordHasher.java](../src/main/java/com/bcp/workshop/auth/PasswordHasher.java).
- [SignedTokenService.java](../src/main/java/com/bcp/workshop/auth/SignedTokenService.java).
- [application.properties](../src/main/resources/application.properties).
- [pom.xml](../pom.xml): extension de salud y version de Quarkus.
- [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java):
  ejemplos y caracterizacion de login.

Solo se actualiza documentacion; no se cambia codigo, configuracion ni dependencias.
