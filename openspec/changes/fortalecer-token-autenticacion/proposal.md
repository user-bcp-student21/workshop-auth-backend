# Propuesta

## Por qué

El Issue #12 de GitHub señala que el token de autenticación necesita claims de identidad verificables, semántica de expiración y protección contra la filtración de valores sensibles. El comportamiento documentado y observado actualmente devuelve un valor heredado codificado en Base64 y sin firma, por lo que los consumidores no pueden confiar en su integridad. Es necesario aclarar y fortalecer este contrato antes de implementarlo.

El contexto del Issue indica que el token actual usa HMAC, pero `docs/ENDPOINTS.md` y el comportamiento vigente describen un valor Base64 sin firma que contiene el nombre de usuario, el rol, la expiración y un secreto fijo. Esta propuesta toma como línea base el comportamiento documentado y observado; la decisión entre conservar HMAC o adoptar una biblioteca JWT queda pendiente para un diseño técnico posterior.

## Cambios propuestos

- Definir que los tokens de inicios de sesión exitosos incluyan los claims `sub`, `client`, `iat` y `exp`; `client` se obtendrá y validará a partir de la solicitud de inicio de sesión.
- Exigir que `expiresIn` sea coherente con `exp - now` dentro de una tolerancia documentada.
- Rechazar con HTTP 500 las solicitudes de inicio de sesión cuyo `client` falte, sea null o esté vacío, según lo acordado expresamente para esta propuesta.
- Añadir `POST /auth/validate`, que recibirá un `token` en formato JSON y devolverá HTTP 200 si es válido o HTTP 401 si es inválido o ha expirado.
- Evitar que se registren tokens, secretos u otros valores sensibles. Si se mantiene un secreto compartido, eliminar su valor predeterminado fuera del sandbox o documentarlo como exclusivo del laboratorio.
- **INCOMPATIBLE**: `client`, que antes era opcional y se ignoraba en `POST /auth/login`, será obligatorio para iniciar sesión correctamente.
- Preservar la estructura actual de la respuesta de inicio de sesión, así como el comportamiento vigente de validación de nombre de usuario y contraseña y de credenciales inválidas.

## Capacidades

### Capacidades nuevas
- `auth-token`: Emitir y validar tokens de autenticación, incluidos sus claims de identidad, la asociación con el cliente, la semántica de expiración y el rechazo observable de tokens inválidos o expirados.

### Capacidades modificadas

Ninguna. Este repositorio no tiene capacidades OpenSpec existentes.

## Impacto

- Contrato de API: `POST /auth/login`, ya existente, y el nuevo `POST /auth/validate`.
- Configuración de tokens y tratamiento de valores sensibles en los registros.
- Pruebas de autenticación y documentación de la API.
- La respuesta de inicio de sesión seguirá incluyendo `token`, `expiresIn`, `user` y `debug`. Se mantienen la estructura actual del objeto `user`, el estado de éxito, el orden de validación de nombre de usuario y contraseña y la respuesta 401 para credenciales inválidas. La semántica de `client` y la representación de la expiración cambiarán según lo descrito anteriormente.

## Fuera de alcance

- Elegir o diseñar el formato o la biblioteca del token (HMAC frente a JWT); esto queda como decisión técnica para un diseño posterior.
- Autorización por rol, sesiones, cookies, políticas o migración de contraseñas, comportamiento de salud y validación por consumidores externos.
- Cambios en el análisis de solicitudes y la negociación de contenido del framework que no estén relacionados con este cambio y que se documenten en `docs/ENDPOINTS.md`.