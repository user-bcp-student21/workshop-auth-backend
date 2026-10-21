# Tasks

## 1. Caracterizar el contrato actual antes del cambio

- [x] 1.1 Completar la prueba de caracterización del login exitoso para verificar HTTP 200, `token`, `expiresIn` de 3600, `user`, `debug`, escaping de `displayName` y ausencia de cookie; ejecutar `mvn -Dtest=AuthResourceTest test` antes de modificar el comportamiento.
- [x] 1.2 Confirmar con pruebas el orden y mensajes actuales para body, username, password y credenciales inválidas, además de los casos observados 400/415/406; ejecutar `mvn -Dtest=AuthResourceTest test` y conservar el resultado como baseline.

Resultado de 1.1: desde la raiz de `workshop-auth-backend`, con JDK 21 y `AUTH_DB_URL=jdbc:sqlite:./target/test-data/characterization-auth.db`, se ejecuto `mvn -B '-Dtest=AuthResourceTest' test`. Codigo de salida 0; 8 pruebas, 0 fallos, 0 errores y 0 omitidas.

Resultado de 1.2: desde la raiz de `workshop-auth-backend`, con JDK 21 y `AUTH_DB_URL=jdbc:sqlite:./target/test-data/characterization-auth.db`, se ejecuto `mvn -B '-Dtest=AuthResourceTest' test`. Codigo de salida 0; 8 pruebas, 0 fallos, 0 errores y 0 omitidas.

## 2. Preparar emisión y verificación de tokens

- [ ] 2.1 Añadir la dependencia JWT/JWS elegida y mapear `AUTH_TOKEN_SECRET` desde configuración externa sin valor por defecto; agregar pruebas que comprueben que la configuración ausente o inválida impide emitir tokens, usando solo una clave sintética, y verificar con las pruebas de configuración.
- [ ] 2.2 Implementar el componente aislado de emisión/verificación HS256 con `sub` estable, `client`, `iat` y `exp`; agregar pruebas para firma válida, alteración de contenido, expiración y coherencia temporal de hasta un segundo, y ejecutar sus pruebas unitarias.
- [ ] 2.3 Documentar en `docs/ENDPOINTS.md` la configuración externa `AUTH_TOKEN_SECRET`, sus requisitos y la ausencia de fallback; verificar que la documentación no contenga claves, tokens ni secretos reales.

## 3. Integrar la emisión con POST /auth/login

- [ ] 3.1 Validar `client` después de las comprobaciones existentes de username y password e integrar el emisor; probar que client ausente, null, vacío o no string produce HTTP 500 y que un client válido aparece sin alteración en el claim, usando `mvn -Dtest=AuthResourceTest test`.
- [ ] 3.2 Actualizar las pruebas del login exitoso para comprobar `sub` igual al ID estable, `iat`, `exp`, `expiresIn` dentro de la tolerancia y la preservación de la forma de respuesta; verificar con `mvn -Dtest=AuthResourceTest test`.
- [ ] 3.3 Actualizar en `docs/ENDPOINTS.md` el contrato de `POST /auth/login`, incluyendo client obligatorio, claims y expiración sin exponer tokens ni secretos; contrastar cada campo documentado con las pruebas del endpoint.

## 4. Incorporar la validación de tokens

- [ ] 4.1 Implementar `POST /auth/validate` y pruebas HTTP para token válido (200), token alterado/inválido (401) y token expirado (401); verificar con `mvn -Dtest=AuthResourceTest test`.
- [ ] 4.2 Añadir pruebas de logs para login y validación que comprueben ausencia de passwords, tokens y secreto, y ejecutar las pruebas de autenticación con captura de logs.
- [ ] 4.3 Documentar en `docs/ENDPOINTS.md` la solicitud JSON y los estados 200/401 de `POST /auth/validate`; verificar coherencia con las pruebas HTTP y ausencia de credenciales o tokens de ejemplo.

## 5. Verificación integrada

- [ ] 5.1 Ejecutar `mvn test` y `openspec validate fortalecer-token-autenticacion --type change --strict`; ambos deben finalizar con código 0 y los logs de prueba no deben revelar valores sensibles.