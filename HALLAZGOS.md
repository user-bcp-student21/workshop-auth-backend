# Flujo de login y matriz de hallazgos

## Alcance

Contraste con [RETO.md](RETO.md), actualizado tras implementar H-01 y H-02
con aprobacion humana. Se ejecutaron pruebas locales aisladas; no se modifico
el [contrato HTTP](docs/ENDPOINTS.md) ni se revisaron componentes externos.
Este archivo es el unico seguimiento. Las evidencias de resolucion apuntan
al codigo actual; los diffs conservan el cambio respecto del codigo original.

## Mapa del flujo completo

| Paso | Comportamiento observado | Evidencia (archivo:linea) |
|---|---|---|
| Configuracion | Puerto 8080, host local y host 0.0.0.0 en prod. CORS admite cualquier origen; campos JSON desconocidos ignorados. AUTH_DB_URL conserva su valor por defecto SQLite. | [application.properties:1](src/main/resources/application.properties#L1); [application.properties:5](src/main/resources/application.properties#L5); [application.properties:8](src/main/resources/application.properties#L8); [LegacyDatabase.java:18](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L18) |
| Entrada | POST /auth/login consume y produce JSON. LoginRequest contiene username, password y client; client no interviene en la autenticacion. | [AuthResource.java:15](src/main/java/com/bcp/workshop/auth/AuthResource.java#L15); [LoginRequest.java:3](src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3) |
| Validacion | Solicitud null, username null/vacio y password null/vacio producen 400 en ese orden. trim() detecta vacios, no transforma credenciales. | [AuthResource.java:26](src/main/java/com/bcp/workshop/auth/AuthResource.java#L26); [AuthResource.java:29](src/main/java/com/bcp/workshop/auth/AuthResource.java#L29); [AuthResource.java:32](src/main/java/com/bcp/workshop/auth/AuthResource.java#L32) |
| Inicializacion | PostConstruct prepara la base y ejecuta migracion transaccional versionada. La inicializacion depende de la instanciacion CDI, no necesariamente del arranque. Un fallo bloquea consultas desde ese bean. | [LegacyDatabase.java:36](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L36); [PasswordHashMigration.java:34](src/main/java/com/bcp/workshop/auth/PasswordHashMigration.java#L34) |
| Persistencia | findByUsername busca con PreparedStatement y mapea la fila a UserRecord y hash interno. Las escrituras de migracion y semillas tambien usan parametros. | [LegacyDatabase.java:51](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L51); [LegacyDatabase.java:56](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L56); [PasswordHashMigration.java:77](src/main/java/com/bcp/workshop/auth/PasswordHashMigration.java#L77) |
| Verificacion | AuthenticationService verifica PBKDF2 antes de devolver el usuario. Usuario ausente o clave incorrecta producen 401. Un hash corrupto genera error explicito, nunca fallback a texto plano. | [AuthenticationService.java:19](src/main/java/com/bcp/workshop/auth/AuthenticationService.java#L19); [PasswordHasher.java:30](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L30); [AuthResource.java:36](src/main/java/com/bcp/workshop/auth/AuthResource.java#L36) |
| Token | Se conserva Base64 UTF-8 de usuario, rol, expiracion Unix y secreto fijo. Expiracion: ahora mas 3600 segundos. No tiene firma; consumidores externos no revisados. | [AuthResource.java:39](src/main/java/com/bcp/workshop/auth/AuthResource.java#L39); [LegacyTokenUtil.java:8](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8); [LegacyTokenUtil.java:13](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13) |
| Respuesta | HTTP 200 con token, expiresIn 3600, user y debug. displayName escapado HTML4. No devuelve id ni password/hash. | [AuthResource.java:40](src/main/java/com/bcp/workshop/auth/AuthResource.java#L40); [AuthResource.java:45](src/main/java/com/bcp/workshop/auth/AuthResource.java#L45); [AuthResource.java:48](src/main/java/com/bcp/workshop/auth/AuthResource.java#L48) |
| Errores | El recurso conserva 500 con mensaje o Unknown error y clase de excepcion. Un fallo SQL de consulta sigue registrandose y devolviendo ausencia por compatibilidad; errores de migracion se registran y relanzan. | [AuthResource.java:54](src/main/java/com/bcp/workshop/auth/AuthResource.java#L54); [LegacyDatabase.java:45](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L45); [LegacyDatabase.java:61](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L61) |

## Matriz de seguimiento

Se conserva la severidad original, en orden descendente. H-03 no demuestra
que un consumidor externo acepte tokens manipulados.

| id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|
| H-01 | Credenciales concatenadas en SQL | Vulnerabilidad | Antes permitia alterar la condicion de autenticacion. Resuelto con PreparedStatement en busqueda y escrituras; password se verifica fuera de SQL. Regresiones de comillas e inyeccion fallan antes y pasan despues. | [LegacyDatabase.java:56](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L56); [LegacyDatabase.java:57](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L57); [PasswordHashMigration.java:77](src/main/java/com/bcp/workshop/auth/PasswordHashMigration.java#L77) | alta | Resuelto |
| H-02 | Almacenamiento y comparacion de contrasenas en texto plano | Vulnerabilidad | Antes exponia las credenciales al leer la base. Resuelto con PBKDF2-HMAC-SHA256 de 600000 iteraciones, salt aleatorio y migracion transaccional versionada. Sin fallback; copias y restos fisicos requieren gestion local, no se promete borrado forense. | [PasswordHasher.java:23](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L23); [AuthenticationService.java:19](src/main/java/com/bcp/workshop/auth/AuthenticationService.java#L19); [PasswordHashMigration.java:34](src/main/java/com/bcp/workshop/auth/PasswordHashMigration.java#L34); [PasswordHashMigration.java:79](src/main/java/com/bcp/workshop/auth/PasswordHashMigration.java#L79) | alta | Resuelto |
| H-03 | Token Base64 sin firma de integridad | Vulnerabilidad | Base64 no autentica contenido: cualquiera puede reconstruir usuario, rol y expiracion. La suplantacion efectiva depende del consumidor, no revisado. El timestamp de expiracion si existe; no se afirma su ausencia. | [LegacyTokenUtil.java:13](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13); [LegacyTokenUtil.java:14](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L14); [LegacyTokenUtil.java:15](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L15) | alta | Pendiente |
| H-04 | Secreto embebido en Java e incluido en cada token | Vulnerabilidad | El valor fijo no procede de configuracion externa y viaja dentro del contenido reversible. Cualquier receptor puede recuperarlo; no ofrece confidencialidad ni prueba de autenticidad. No se reproduce el valor aqui. | [LegacyTokenUtil.java:8](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8); [LegacyTokenUtil.java:14](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L14); [LegacyTokenUtil.java:15](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L15) | alta | Pendiente |
| H-05 | CORS configurado para admitir cualquier origen | Vulnerabilidad | La expresion permite sitios no confiables, que pueden leer respuestas si navegador y conectividad lo permiten. No demuestra robo de cookies ni sesiones. CORS ya esta en application.properties: el problema es la politica permisiva fija. | [application.properties:4](src/main/resources/application.properties#L4); [application.properties:5](src/main/resources/application.properties#L5); [application.properties:6](src/main/resources/application.properties#L6); [application.properties:7](src/main/resources/application.properties#L7) | media | Pendiente |
| H-06 | Respuesta 500 expone mensaje y clase de excepcion | Vulnerabilidad | Las excepciones que alcanzan el catch del recurso revelan detalles internos al cliente. Se conserva ese contrato actual. El logging nuevo en persistencia no resuelve la exposicion HTTP. | [AuthResource.java:54](src/main/java/com/bcp/workshop/auth/AuthResource.java#L54); [AuthResource.java:56](src/main/java/com/bcp/workshop/auth/AuthResource.java#L56); [AuthResource.java:57](src/main/java/com/bcp/workshop/auth/AuthResource.java#L57) | media | Pendiente |
| H-07 | Recurso concentra validacion, coordinacion y respuesta con anidamiento | Refactor | login aun valida, genera token, transforma datos y arma respuestas y errores. H-02 separo autenticacion en servicio/repositorio, pero no realiza el refactor completo del recurso. 3600 sigue duplicado en recurso y token. | [AuthResource.java:24](src/main/java/com/bcp/workshop/auth/AuthResource.java#L24); [AuthResource.java:35](src/main/java/com/bcp/workshop/auth/AuthResource.java#L35); [AuthResource.java:39](src/main/java/com/bcp/workshop/auth/AuthResource.java#L39); [AuthResource.java:42](src/main/java/com/bcp/workshop/auth/AuthResource.java#L42); [LegacyTokenUtil.java:13](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13) | baja | Pendiente |
| H-08 | Versiones SCA indicadas por el reto presentes; advisories por verificar | Sugerencia | Quarkus 3.27.4 y Commons Text 1.9 figuran en el POM. No demuestran por si solos CVE ni explotabilidad: falta investigar advisory, rango y dependencias resueltas. El uso observado es escapeHtml4, no interpolacion. Severidad baja para investigacion, no para una CVE sin confirmar. | [pom.xml:13](pom.xml#L13); [pom.xml:49](pom.xml#L49); [pom.xml:50](pom.xml#L50); [AuthResource.java:45](src/main/java/com/bcp/workshop/auth/AuthResource.java#L45) | baja | Pendiente |

## Correspondencia con RETO.md

| Caso del reto | Seguimiento |
|---|---|
| SCA: Quarkus y Commons Text | H-08; versiones encontradas, CVE concreta no verificada. |
| Datos: contrasenas en texto plano | H-02 |
| SQL: concatenacion de credenciales | H-01 |
| Token: Base64 con secreto embebido | H-03 y H-04 |
| Configuracion: secreto y CORS hardcodeados | H-04 y H-05; CORS ya usa archivo de configuracion. |
| Clean code: responsabilidades concentradas | H-07 |
| Errores: exposicion de detalles internos | H-06 |

No se omitio ningun caso. No se certifica la seguridad de dependencias ni
de consumidores externos.

## Verificacion H-01 y H-02 (2026-10-19)

| Hallazgo | Caracterizacion antes | Correccion antes | Despues |
|---|---|---|---|
| H-01 | 7 pruebas pasan: 5 de AuthResource y 2 de persistencia. | 2 fallos esperados: comillas literales y manipulacion de condicion SQL. | 9/9 pasan tras parametrizar, antes de empezar H-02. |
| H-02 | Las 9 pruebas anteriores pasan sobre H-01. | La prueba de almacenamiento versionado falla con passwords legibles. | Suite final: 21/21 pasan, sin fallos, errores ni omisiones. |

Evidencias locales generadas bajo target, ignoradas por Git:

- [H-01 antes](target/test-data/h01-before-confirmed.log) y
  [despues](target/test-data/h01-after.log).
- [H-02 antes](target/test-data/h02-before.log) y
  [verificacion final](target/test-data/final-verify.log).
- [Diff H-01](target/test-data/h01.diff) y
  [diff H-02](target/test-data/h02.diff).
- [JaCoCo](target/site/jacoco/index.html): 170/178 lineas (95,51%) en fuentes
  nuevas/modificadas, con archivo de ejecucion limpio. Por fuente:
  AuthResource 100%, LegacyDatabase 100%, AuthenticationService 100%,
  StoredCredentials 100%, PasswordHashMigration 95,77%, PasswordHasher 85,29%.
  Intersectando las lineas ejecutables del informe con las agregadas/modificadas
  respecto de HEAD: 142/150 (94,67%), tambien superior al umbral del 85%.

Se verificaron rollback, reinicio idempotente, hashes corruptos, version no
soportada, clave legacy parecida al prefijo del hash, Unicode, ausencia de
truncamiento y logs de error sin credenciales. Una derivacion PBKDF2 de 600000
iteraciones medida en la ejecucion final costo aproximadamente 248 ms.

### Desviaciones y precisiones

- La tabla de ENDPOINTS indica Workshop123! para trainer; el codigo original
  y README usan Trainer123!. El usuario autorizo conservar el comportamiento
  real y registrar la discrepancia sin editar ENDPOINTS. Las otras respuestas
  caracterizadas permanecen iguales.
- Se registro aprobacion humana en PLAN antes de editar produccion.
- Los tests cargan SQLite explicitamente por la separacion de classloaders de
  Quarkus. No se cambio el driver ni se agregaron dependencias.
- No se ejecutaron workflows remotos CodeQL o Dependency Review; su
  configuracion y el POM permanecen intactos.

## Revision del diff local contra main (2026-10-19)

Rama verificada: day1/prompting. Se revisaron git diff main, git status --short
y archivos nuevos, sin presuponer PR ni merge.

- **H-01 se mantiene Resuelto:** busqueda parametrizada en
  [LegacyDatabase.java:56](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L56)
  y escrituras parametrizadas en
  [PasswordHashMigration.java:77](src/main/java/com/bcp/workshop/auth/PasswordHashMigration.java#L77).
  Las regresiones SQL pasan en la suite actual.
- **H-02 se mantiene Resuelto:** derivacion y comparacion en
  [PasswordHasher.java:24](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L24)
  y [PasswordHasher.java:31](src/main/java/com/bcp/workshop/auth/PasswordHasher.java#L31),
  migracion y rollback en
  [PasswordHashMigration.java:34](src/main/java/com/bcp/workshop/auth/PasswordHashMigration.java#L34).
  Pasan las pruebas de migracion, corrupcion e idempotencia.
- **Bloqueante para aceptar el diff completo:** los tests exigen AUTH_DB_URL
  externa en [LegacyDatabaseTest.java:80](src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java#L80),
  pero [ci.yml:21](.github/workflows/ci.yml#L21) ejecuta mvn -B verify sin
  configurarla ni preparar el directorio de fixtures. Reproduccion local sin
  variable: cinco errores en LegacyDatabaseTest. Debe integrarse la configuracion
  aislada en la ejecucion automatica, sin omitir pruebas ni reducir gates.
  Esto no reintroduce SQLi ni almacenamiento plano, por lo que no se reabren
  H-01/H-02; impide aprobar la entrega en su conjunto.
- **Importante, H-07 sigue Pendiente:** AuthResource.login aun combina validacion,
  token y respuesta con anidamiento y 3600 literal
  ([AuthResource.java:24](src/main/java/com/bcp/workshop/auth/AuthResource.java#L24),
  [AuthResource.java:42](src/main/java/com/bcp/workshop/auth/AuthResource.java#L42)).
  No satisface por completo las reglas 8, 11 y 12 de REGLAS. Es deuda previa
  explicitamente excluida del refactor por PLAN, no una correccion ya concluida.
- H-03/H-04/H-05/H-06/H-08 siguen Pendiente, sin declarar CVEs ni explotabilidad
  de consumidores no verificados. No se agregaron dependencias ni se alteraron
  los workflows o JaCoCo respecto de main.

Validacion repetida con JDK 21 y base SQLite nueva bajo target/test-data:
21 pruebas pasan; cobertura limpia de fuentes nuevas/modificadas 170/178
(95,51%) y de lineas agregadas/modificadas respecto de main 142/150 (94,67%).
La base temporal de esta revision se elimino tras cerrar las conexiones.
Evidencias: [CI sin variable](target/test-data/review-no-env.log),
[suite configurada](target/test-data/review-verify.log) e
[informe JaCoCo](target/site/jacoco/index.html).

No se modifico codigo en esta revision. Ningun hallazgo vuelve a Pendiente.
