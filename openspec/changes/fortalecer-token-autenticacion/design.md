# Diseño

## Contexto

Ver `proposal.md` para la motivación y `specs/auth-token/spec.md` para los requisitos observables de este cambio.

El estado técnico actual es:

- `AuthResource` coordina `POST /auth/login`, conserva la validación de credenciales y construye la respuesta HTTP.
- `LoginRequest` ya contiene `client`, pero actualmente el recurso no lo valida ni lo utiliza.
- `AuthenticationService` devuelve un `UserRecord` que incluye un ID estable, además de username, displayName y role.
- `LegacyTokenUtil` genera Base64 sin firma y concatena username, role, expiración y un secreto fijo en el contenido. No hay verificación de tokens en el backend.
- `application.properties` no configura material de firma y `pom.xml` no declara una biblioteca JWT.
- `AuthResourceTest` caracteriza la respuesta de login, el formato legacy, el orden de validación y la negociación HTTP. Parte de las aserciones del token deberá sustituirse cuando se implemente el nuevo contrato.

## Goals / Non-Goals

**Goals:**

- Emitir tokens firmados con los claims de la spec y validarlos desde el nuevo `POST /auth/validate`.
- Obtener la clave de firma de configuración externa, sin secretos embebidos ni fallback en código.
- Mantener la estructura actual de la respuesta exitosa de login y los comportamientos de credenciales, validación HTTP y negociación que la spec protege.
- Mantener acotado el cambio a autenticación y emisión/verificación de tokens.

**Non-Goals:**

- Verificación por consumidores externos, revocación de tokens, sesiones, autorización por rol o cambios al almacenamiento de usuarios.
- Cambiar el contrato de salud, CORS o el modelo de errores del framework.
- Cumplir el criterio adicional del HU-01 adjunto que exige verificación con clave pública. El alcance OpenSpec aprobado no lo exige; incorporarlo requiere actualizar y aprobar proposal/specs antes de implementar.

## Contratos protegidos

- `POST /auth/login` mantiene HTTP 200 y los campos `token`, `expiresIn`, `user` y `debug`; `user` mantiene `username`, `displayName` y `role`. No añade ID público ni cookie de sesión.
- Se preservan la validación del cuerpo, username y password en el orden actual, el HTTP 401 para credenciales inválidas y las respuestas documentadas de negociación/parsing. La validación de `client` y el nuevo comportamiento del token son las excepciones aprobadas en la spec.
- `expiresIn` continúa siendo un entero. Se conserva inicialmente el TTL vigente de 3600 segundos; el valor se calcula desde el mismo instante usado para `iat` y `exp`, con la tolerancia máxima de un segundo de la spec.
- `POST /auth/validate` acepta JSON con `token` y comunica únicamente el resultado normativo mediante HTTP 200 para un token válido y HTTP 401 para uno inválido o expirado. No se fija un cuerpo de respuesta adicional porque la spec no lo define.
- Un token legacy sin firma no se considera válido para el nuevo verificador. No se agrega aceptación transitoria del formato Base64 inseguro.

## Componentes afectados

- `AuthResource`: validar `client`, solicitar la emisión del token e incorporar `POST /auth/validate` sin alterar el resto del contrato de login.
- `LoginRequest`: conservar el campo `client` ya existente; no se requiere ampliar el DTO para este diseño.
- `LegacyTokenUtil`: retirar su responsabilidad de construir tokens legacy y sustituirla por un límite dedicado a emitir/verificar tokens firmados.
- Configuración Quarkus: mapear `AUTH_TOKEN_SECRET` a la configuración de autenticación, sin valor predeterminado embebido.
- `pom.xml`: añadir una biblioteca JWT/JWS; no implementar manualmente el parseo ni la verificación criptográfica.
- Pruebas de `AuthResource` y de token, más `docs/ENDPOINTS.md` para documentar la nueva ruta y el formato observable sin publicar claves ni tokens.

## Decisiones

### Usar JWT/JWS firmado con HS256

Se utilizará un JWT compacto firmado como JWS con HMAC-SHA-256 (HS256), mediante una biblioteca mantenida como `com.auth0:java-jwt`. La firma protege la integridad y la estructura estándar permite representar los claims `sub`, `client`, `iat` y `exp`. El verificador de `POST /auth/validate` aplicará la misma clave y validará firma y expiración; no consultará SQLite ni agregará revocación, que están fuera de alcance.

Alternativas consideradas:

- Un formato propio con HMAC y campos separados: evita una dependencia, pero obliga a definir y mantener un parser, serialización y validación de expiración propios.
- JWT/JWS asimétrico (por ejemplo, RS256): permite verificación con clave pública, pero ese requisito no forma parte de la spec aprobada y añade gestión y distribución de pares de claves. Si la verificación pública es necesaria, debe aprobarse primero como cambio de alcance.
- Mantener el Base64 actual: se descarta porque no proporciona integridad verificable y expone el secreto dentro del contenido.

### Cargar el secreto desde configuración y fallar de forma cerrada

La clave HS256 se obtiene exclusivamente de `AUTH_TOKEN_SECRET`, suministrada al proceso por configuración externa. No se incluye un valor por defecto en código ni en los perfiles. Si falta o no cumple el mínimo criptográfico aceptado por la biblioteca, la aplicación no debe emitir tokens; se recomienda fallar durante la inicialización para detectar la configuración inválida antes de recibir tráfico. Las pruebas usan únicamente una clave sintética y aislada.

El secreto se usa para la firma/verificación y nunca se incorpora a los claims, respuestas ni logs. No se registra el token completo en ningún nivel de log.

### Construir claims desde los datos autenticados

- `sub` se deriva de `UserRecord.id`, no del username, porque el ID es estable y el contrato público de `user` no cambia.
- `client` se toma de la solicitud validada y se conserva sin transformación en el claim.
- `iat` y `exp` se calculan a partir de un único instante en segundos; `exp` se fija inicialmente a `iat + 3600` para conservar el TTL actual.
- `expiresIn` refleja ese mismo TTL y sigue siendo entero.
- No se incluyen password, hashes, secreto, ni claims de rol no solicitados por la spec.

### Mantener los límites HTTP actuales

El recurso conserva el orden actual de validación de body, username y password. Después de credenciales válidas, valida `client` según la spec y delega la emisión al componente de tokens. `POST /auth/validate` delega la comprobación criptográfica y responde con el estado estipulado; errores internos no deben convertir un token malformado en una respuesta 500.

Alternativa considerada: añadir verificación en `AuthenticationService`. Se descarta porque autenticar credenciales y emitir/verificar tokens son responsabilidades distintas; mantener el límite de token separado evita acoplar el acceso a SQLite a la validación de una credencial ya emitida.

## Estrategia incremental

1. Antes de cambiar el comportamiento, preservar con pruebas de caracterización la respuesta completa de login, el TTL vigente, los campos/escaping de `user`, el valor de `debug`, el orden y mensajes de validación, el 401 por credenciales inválidas y los casos observados 400/415/406.
2. Preparar pruebas de token con clave sintética: claims emitidos, verificación de firma, rechazo tras alterar el contenido, expiración y configuración ausente/inválida. Las pruebas no deben imprimir ni guardar tokens o claves en reportes.
3. Añadir la dependencia y el componente aislado de emisión/verificación; verificar sus casos unitarios antes de conectarlo al recurso.
4. Integrar la validación de `client` y emisión en login, y agregar `/auth/validate`; cubrir los contratos HTTP existentes y nuevos con pruebas de recurso.
5. Actualizar `docs/ENDPOINTS.md` y ejecutar la suite de autenticación. Revisar los logs de prueba para confirmar que no contienen credenciales, tokens ni secretos.

## Pruebas de caracterización previas

Antes de sustituir el token legacy, las pruebas actuales deben dejar registrado el comportamiento que se conserva: HTTP 200 y forma exacta de la respuesta, `expiresIn` entero de 3600, contenido público de `user`, username y role, displayName escapado, `debug`, ausencia de cookies, orden y mensajes de validación, errores de credenciales y negociación/parsing. La caracterización del formato legacy sirve solo como evidencia del estado previo y debe retirarse cuando el emisor nuevo esté integrado; no debe convertirse en requisito de compatibilidad.

## Riesgos / Trade-offs

- [Formato legacy consumido externamente] → El JWT no será compatible con consumidores que decodifiquen el Base64 anterior. Coordinar el cambio de formato antes del despliegue; no agregar fallback que vuelva a aceptar tokens sin firma.
- [Secreto HS256 distribuido a verificadores] → Toda instancia que valide tokens debe proteger el mismo secreto. No publicar verificadores externos ni claves en repositorio; si se requiere verificación pública, abrir y aprobar un cambio de alcance para firma asimétrica.
- [Configuración ausente o inválida] → El fallo de inicialización impide servir login; documentar `AUTH_TOKEN_SECRET` para cada entorno y suministrar una clave sintética separada en pruebas.
- [Relojes desincronizados] → `exp` puede evaluarse distinto en instancias con desfase; mantener sincronización de reloj y probar el límite de un segundo requerido por la spec.
- [Cambio de token invalida tokens emitidos antes] → No aceptar el formato legacy. Con TTL de 3600 segundos, coordinar la actualización y renovación de clientes dentro de esa ventana.
- [Desviación con HU-01 adjunto] → El criterio de verificación con clave pública no queda satisfecho por HS256. Se excluye por la decisión de mantener el OpenSpec aprobado; si ese criterio sigue siendo obligatorio, se debe actualizar la spec antes de implementar.

## Rollback

El rollback del servicio a la versión anterior restaura la emisión Base64 sin firma y el secreto embebido, por lo que reintroduce explícitamente H-03/H-04. No debe activarse como fallback automático ni coexistir como segunda ruta de emisión. Ante un incidente, preferir corregir hacia delante; si se requiere volver a la versión anterior, hacerlo como mitigación temporal aprobada, comunicar que los JWT emitidos dejarán de ser aceptados y exigir una nueva autenticación. Considerar que tokens nuevos pueden seguir vigentes hasta 3600 segundos.
