# Propuesta de correccion de H-03 y H-04

## Alcance y referencias

Solo planificacion dentro de workshop-auth-backend. Este documento sustituye
la propuesta de seis items: no incluye H-05, H-06, H-07 ni H-08 y no modifica
codigo, pruebas, dependencias, contrato o estados de
[HALLAZGOS.md](HALLAZGOS.md), unico seguimiento.

Referencias: [ENDPOINTS.md](docs/ENDPOINTS.md),
[GOOD_PRACTICES.md](docs/GOOD_PRACTICES.md) y [REGLAS.md](REGLAS.md),
contrastados con el generador legacy, el recurso HTTP y el POM actuales.

### Aprobacion y decisiones pendientes

El 2026-10-19 el usuario aprobo la propuesta anterior de seis items y autorizo
JWT RS256 en lugar de Base64 legacy. Esa aprobacion es antecedente, no evidencia
de que los consumidores hayan coordinado el cambio ni aprobacion automatica
de esta nueva revision acotada. Esta solicitud autoriza solo documentacion.
Antes de implementar, confirmar esta revision y cerrar los acuerdos indicados.

Siguen pendientes los valores exactos de issuer/audience, la aceptacion del
formato/claims por consumidores, y responsables/procedimiento de distribucion
y rotacion de claves. No inventar esos valores ni inspeccionar otros componentes.
La autorizacion anterior de cambiar errores 500 no forma parte de este plan.

## Item: H-03 - Token Base64 sin firma de integridad

### Identificacion y problema

H-03, severidad alta, estado Pendiente en
[HALLAZGOS.md](HALLAZGOS.md).
[LegacyTokenUtil.java:13](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L13)
calcula una expiracion; las lineas siguientes concatenan y codifican los datos,
sin firma. Base64 no autentica usuario, rol ni vencimiento.
No se afirma que falte timestamp ni que un consumidor externo acepte tokens
manipulados: ese comportamiento no se ha inspeccionado.

### Alcance propio y cambio propuesto

1. Sustituir la emision estatica legacy por TokenService inyectable que genere
   JWT RS256 con biblioteca integrada, sin implementar JWS manualmente.
2. Claims propuestos: sub=username, groups=[role], iat en segundos Unix y
   exp=iat+3600. Configurar iss y aud con valores acordados antes de implementar.
   No agregar contrasenas, hashes, secretos ni claves a header o payload.
3. Usar Clock inyectable y una constante nombrada compartida para duracion
   firmada y expiresIn. Mantener 3600 segundos, sin configurabilidad innecesaria.
4. Proponer io.quarkus:quarkus-smallrye-jwt-build gestionada por el BOM actual.
   Justificacion: JWT interoperable con firma mantenida por biblioteca, no
   criptografia casera. Comprobar gestion de version, API, transitivas y
   advisories antes de agregarla. No actualizar plataforma incidentalmente.
5. El servicio recibe la clave de SigningKeyProvider de H-04. No introducir
   claves hardcodeadas, fallback legacy ni algoritmo none para facilitar cierre.
6. El recurso delega emision. No instalar autenticacion JWT en las rutas:
   el componente emite tokens, no implementa un endpoint verificador.
   Los tests verifican firma, algoritmo, claims y expiracion; la aplicacion
   efectiva de esos controles en consumidores debe coordinarse externamente.

**Fuera del item:** carga, validacion, almacenamiento y rotacion de claves
(H-04); CORS, errores, persistencia, passwords y refactor general de capas.

### Archivos a tocar durante implementacion

- [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java):
  retirar generador cuando no tenga llamadas.
- Nuevo src/main/java/com/bcp/workshop/auth/TokenService.java.
- [AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java):
  solo delegacion y duracion compartida.
- [application.properties](src/main/resources/application.properties):
  configuracion acordada de iss/aud, sin valores inventados.
- [pom.xml](pom.xml): dependencia de emision; dependencia de verificacion con
  alcance test solo si resulta necesaria y se justifica.
- Nuevo src/test/java/com/bcp/workshop/auth/TokenServiceTest.java.
- [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java).
- [ENDPOINTS.md](docs/ENDPOINTS.md) y [TESTING.md](docs/TESTING.md):
  exclusivamente formato, seguridad y pruebas de token.
- [HALLAZGOS.md](HALLAZGOS.md): evidencia de H-03 solo al verificar su cierre.

### Prueba a escribir antes de tocar produccion

1. Ejecutar y guardar caracterizacion del login con ambos usuarios, errores,
   expiracion anunciada, identidad, debug y formato legacy.
2. Separar aserciones del sobre HTTP estable de las del formato reemplazado,
   conservando evidencia del cambio intencional en vez de ocultar el baseline.
3. Escribir regresion aplicable al emisor actual: exigir token JWT con firma
   verificable, no solo decodificable. Debe fallar con Base64 legacy.
4. Para el emisor nuevo, usar par RSA efimero y reloj fijo; verificar con
   biblioteca, independientemente de la llamada de construccion: firma,
   algoritmo RS256, claims acordados y exp-iat=3600.
5. Verificar rechazo de payload/header/firma alterados, clave equivocada,
   algoritmo no permitido, iss/aud incorrectos y exp<=ahora; aceptar justo
   antes de exp con tolerancia temporal explicitada. Estas son pruebas del
   token emitido/verificador de test, no controles productivos agregados.
6. Verificar JWT del login HTTP con publica de test y conservar todos los demas
   campos exactos. Repetir errores y salud UP/DOWN para detectar regresiones
   o activacion accidental de autenticacion en rutas publicas.

### Contrato, dependencias y cierre

H-03 solicita corregir integridad: cambia deliberadamente la representacion de
token, actualmente Base64 documentado. Mantener token string no garantiza
compatibilidad con consumidores que lo decodifican. Acordar RS256, claims,
iss/aud, clave publica y momento de cambio sin ampliar el ambito.

Mantener endpoints, entradas, validaciones, estados/cuerpos de error, identidad,
escape HTML4, debug y expiresIn. No cambiar el cuerpo 500 como parte de H-03.
No afirmar que un consumidor aplica exp sin evidencia de su responsable.
El cierre integrado depende del proveedor de H-04 y de pruebas criptograficas,
no de una decodificacion exitosa.

## Item: H-04 - Secreto embebido en Java e incluido en cada token

### Identificacion y problema

H-04, severidad alta, estado Pendiente en
[HALLAZGOS.md](HALLAZGOS.md).
[LegacyTokenUtil.java:8](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8)
define un secreto fijo y
[LegacyTokenUtil.java:14](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L14)
lo incorpora al contenido reversible.
Externalizar el mismo valor pero seguir incluyendolo en el token no corrige
la divulgacion. No reproducir su valor en nueva documentacion.

### Alcance propio y cambio propuesto

1. Retirar el secreto legacy de la emision y no reutilizarlo para firmar: ya
   esta expuesto. Sin secreto por defecto ni fallback.
2. Introducir SigningKeyProvider, responsable de cargar y validar clave privada
   externa PKCS#8 PEM RSA >=2048 bits, fuera del classpath/artefacto.
   Proponer auth.token.signing-key-path / AUTH_TOKEN_SIGNING_KEY_PATH:
   la variable contiene una ruta, nunca el material privado.
3. Validar al inicializar: configuracion obligatoria, archivo legible, formato,
   algoritmo y tamano. Fallar explicitamente sin emitir tokens ni generar
   claves silenciosamente. Diagnostico operacional sin material ni ruta sensible.
4. Documentar provisionamiento por el responsable y permisos restringidos
   para la cuenta del proceso. No crear archivos externos durante esta tarea
   ni leer/copiar claves del usuario. Tests con claves efimeras bajo target.
5. Coordinar publica y rotacion mediante reinicio controlado; no agregar gestor
   multiclave, recarga ni kid sin requisito. Acordar retencion de publica anterior
   durante la ventana de 3600 segundos y despliegue con consumidores.
6. Comprobar que tokens, logs, respuestas y artefactos nuevos no contienen
   secretos. No prometer borrar historial Git o tokens ya distribuidos.

**Fuera del item:** formato JWT, algoritmo de firma, claims, reloj y verificador
(H-03); passwords, SQL, CORS y correccion general de errores HTTP.
JDK basta para cargar/validar PKCS#8 RSA: no agregar dependencia para ese fin.

### Archivos a tocar durante implementacion

- Nuevo src/main/java/com/bcp/workshop/auth/SigningKeyProvider.java.
- [application.properties](src/main/resources/application.properties):
  ruta externa sin clave ni fallback.
- [LegacyTokenUtil.java](src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java):
  retirar secreto; H-03 retira el generador completo.
- TokenService.java de H-03: solo conectar proveedor, sin redefinir formato.
- Nuevo src/test/java/com/bcp/workshop/auth/SigningKeyProviderTest.java.
- TokenServiceTest.java de H-03: no divulgacion e integracion del proveedor.
- [AuthResourceTest.java](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java):
  configurar clave efimera antes del arranque HTTP.
- [README.md](README.md): provisionamiento y operacion.
- .gitignore solo si se acuerda ruta local especifica dentro del repositorio;
  nunca ignorar evidencia o reglas de calidad.
- [ENDPOINTS.md](docs/ENDPOINTS.md) y [TESTING.md](docs/TESTING.md):
  solo seguridad/configuracion y pruebas relacionadas.
- [HALLAZGOS.md](HALLAZGOS.md): evidencia de H-04 al verificar integracion.

Compartir archivos con H-03 no combina alcances: identificar por separado
el diff de emision y el de provisionamiento/no divulgacion.

### Prueba a escribir antes de tocar produccion

1. Caracterizar token actual sin imprimir el secreto. Escribir regresion que
   exija ausencia del secreto en contenido decodificado; debe fallar antes.
2. Probar proveedor con configuracion ausente, ruta inexistente, PEM corrupto,
   clave publica en vez de privada, algoritmo distinto y RSA menor al minimo.
   Todos fallan explicitamente sin filtrar material. Probar archivo ilegible
   cuando el SO permita inducirlo; si no, registrar pendiente sin simular exito.
3. Probar PKCS#8 valido, carga unica, emision con H-03 y verificacion con publica
   correspondiente. Otra clave no verifica con la publica anterior.
4. Capturar errores/logs y revisar header/payload para ausencia de secreto,
   material privado y credenciales. No imprimir claves en aserciones fallidas.
5. Integracion HTTP con archivo efimero bajo target/test-data configurado antes
   del arranque; restaurar configuracion y limpiar solo archivos conocidos.
6. Configuracion invalida no produce token exitoso. Si impide arranque, registrar
   fallo operacional; el contrato no define un cuerpo HTTP para fallo de CDI.
   No inventar status ni corregir H-06 dentro de este item.

### Dependencia y cierre

El proveedor puede probarse aisladamente. H-04 solo se cierra tras integrarse
con H-03, retirar secreto/fallback y verificar respuestas y artefactos.
No mantener un cuarto componente secreto del token legacy por compatibilidad.
La clave publica, nunca la privada, se entrega a consumidores mediante el
procedimiento pendiente de acuerdo.

## Secuencia y validacion comunes

1. Confirmar revision acotada y acuerdos externos pendientes antes de implementar.
2. Guardar baseline y regresiones fallidas antes de cambios productivos.
   H-04 proveedor y H-03 emision pueden prepararse por separado; ambos cierres
   requieren integracion, sin claves provisionales embebidas.
3. Ejecutar pruebas especificas antes/despues por item y suite de contrato.
   Registrar comandos, directorio, codigo de salida, resultados y pendientes
   reales en docs/TESTING.md, sin confundir pruebas propuestas con ejecutadas.
4. Mantener CodeQL, Dependency Review y JaCoCo. Medir lineas ejecutables
   agregadas/modificadas contra main con diff y reporte JaCoCo: cubiertas/total
   >=85%. Explicitar exclusiones no ejecutables y cobertura por archivo.
5. Respetar una responsabilidad por metodo, sin duplicacion, anidamiento
   profundo ni constantes magicas. No emprender refactor ajeno al plan.
6. Solo marcar cada item Resuelto en HALLAZGOS.md con evidencia y nota breve
   tras verificarlo. No actualizar estados en esta etapa documental.
