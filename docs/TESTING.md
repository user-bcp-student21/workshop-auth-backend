# Pruebas del backend de autenticacion

## Alcance y criterio

Contrato de referencia: [ENDPOINTS.md](ENDPOINTS.md). La matriz y resultados
anteriores corresponden al baseline ejecutado el 2026-10-19. La seccion final
registra la implementacion RS256, sus pruebas actuales y la integracion local
con la API de requisitos.

Las expectativas que describen defectos legacy son caracterizacion, no pruebas
de seguridad aprobatorias. Los defectos se separan mas abajo. Ninguna expectativa
se relajo para ocultar un fallo. No se introdujeron endpoints ni campos nuevos.

## Matriz por endpoint

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | student y trainer con credenciales validas; client presente/omitido | Camino feliz | 200 JSON con token, expiresIn 3600, debug e identidad completa; firma RS256, claims y expiracion comprobados. |
| POST /auth/login | JSON con campo desconocido | Borde | Campo ignorado; login 200 sin campos extra en respuesta. |
| POST /auth/login | username ausente, null, vacio o solo espacios | Borde/error | 400, message Username is required; se valida antes de password. |
| POST /auth/login | password ausente, null, vacio o solo espacios | Borde/error | 400, message Password is required. |
| POST /auth/login | JSON null y argumento null directo | Error | 400, message Request is null. |
| POST /auth/login | Usuario inexistente o password incorrecto | Error | 401 con Invalid user seguido del username y or password. |
| POST /auth/login | Espacios adicionales en username o password | Borde | 401; no se normalizan credenciales validas agregando trim. |
| POST /auth/login | Nombre visible con caracteres HTML | Borde, recurso directo | 200 con displayName escapado HTML4 y solo tres campos en user. |
| POST /auth/login | Tabla de usuarios temporalmente no disponible | Error, caracterizacion de defecto | 401 documentado, diagnostico en servidor; esquema restaurado en finally. |
| POST /auth/login | Hash almacenado corrupto despues de inicializar el bean | Error | 500 con message Invalid stored password format y exception java.lang.IllegalStateException; hash original restaurado en finally. |
| POST /auth/login | Servicio lanza excepcion con mensaje controlado | Error, recurso directo | 500 con controlled failure y java.lang.IllegalStateException. |
| POST /auth/login | Servicio lanza excepcion sin mensaje | Error, recurso directo | 500 con Unknown error y java.lang.IllegalStateException. |
| POST /auth/login | JSON malformado: cuerpo literal { | Error de framework | 400 con cuerpo vacio. |
| POST /auth/login | Content-Type text/plain | Error de framework | 415 con cuerpo vacio. |
| POST /auth/login | Accept text/plain | Error de framework | 406 con cuerpo vacio. |
| GET /q/health | Sin autenticacion ni cuerpo, Accept application/json | Camino feliz | 200 JSON, exactamente status UP y checks vacio. |
| GET /q/health | Omitir Accept opcional y repetir solicitud sin cuerpo | Borde | Ambas solicitudes dan 200, UP y checks vacio. No se inventa un campo vacio para una ruta sin DTO. |
| GET /q/health | Comprobacion de readiness DOWN en perfil exclusivo de test | Error de extension | 503 JSON, status DOWN y un check de test llamado controlled-test-readiness con status DOWN. |

La matriz cubre cada fila de error definida en ENDPOINTS. No convierte sus
preguntas abiertas en expectativas inventadas. Las dos variantes genericas de
500 se ejercitan directamente sobre el recurso, no mediante HTTP: esto permite
inducirlas sin incorporar mocks como dependencias ni puertas de prueba en
produccion. El 500 por hash corrupto si atraviesa HTTP, servicio y repositorio.

## Codigo de los tests

Fuentes ejecutables completas:

- [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java):
  8 metodos, varios con multiples escenarios. Conserva la caracterizacion
  anterior y agrega errores del framework, fallo SQL via HTTP y hash corrupto
  via HTTP; completa username vacio.
- [HealthResourceTest.java](../src/test/java/com/bcp/workshop/auth/HealthResourceTest.java):
  2 metodos, camino feliz y borde sin Accept.
- [HealthDownTest.java](../src/test/java/com/bcp/workshop/auth/HealthDownTest.java):
  1 metodo y un HealthCheck controlado que solo existe en las fuentes de test.

### Login: nuevos errores HTTP

Extracto ejecutado de AuthResourceTest:

```java
@Test
void rejectsMalformedJsonAndUnsupportedNegotiation() {
    given().contentType("application/json").accept("application/json").body("{")
            .post("/auth/login").then().statusCode(400).body(is(""));
    given().contentType("text/plain").accept("application/json").body("text")
            .post("/auth/login").then().statusCode(415).body(is(""));
    given().contentType("application/json").accept("text/plain").body("{}")
            .post("/auth/login").then().statusCode(406).body(is(""));
}
```

El metodo reportsCorruptStoredHashOverHttp inicia primero el bean mediante un
login valido, guarda el hash original, escribe un hash invalido en la fixture,
verifica 500 y restaura el valor en finally. Si el hash fuera corrupto antes
de inicializar el bean se probaria otra rama (validacion de migracion/CDI),
cuyo cuerpo no esta fijado en el contrato.

El metodo characterizesDocumentedQueryFailureOverHttp inicializa mediante
login valido, renombra la tabla de la fixture, verifica el 401 documentado y
restaura la tabla en finally. Esta prueba no significa que 401 sea un manejo
correcto de errores de infraestructura; ver defectos conocidos.

### Salud: camino feliz y borde

Codigo de las dos pruebas de HealthResourceTest:

```java
@Test
void reportsHealthyWithoutAuthenticationOrRequestBody() {
    given().accept("application/json").get("/q/health")
            .then().statusCode(200).contentType("application/json")
            .body("$", aMapWithSize(2))
            .body("status", is("UP")).body("checks", empty());
}

@Test
void acceptsOmittedOptionalAcceptHeaderRepeatedly() {
    for (int request = 0; request < 2; request++) {
        given().get("/q/health").then().statusCode(200)
                .body("status", is("UP")).body("checks", empty());
    }
}
```

### Salud: 503 real del framework, sin cambiar produccion

HealthDownTest usa @TestProfile con esta configuracion:

```java
public static class DownProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("testing.health.failure", "true");
    }
}

@Test
void reportsServiceUnavailableWhenAHealthCheckIsDown() {
    given().accept("application/json").get("/q/health")
            .then().statusCode(503).contentType("application/json")
            .body("$", aMapWithSize(2))
            .body("status", is("DOWN")).body("checks", hasSize(1))
            .body("checks[0].name", is("controlled-test-readiness"))
            .body("checks[0].status", is("DOWN"));
}
```

La clase de soporte, en el mismo archivo de test:

```java
@Readiness
@ApplicationScoped
@IfBuildProperty(name = "testing.health.failure", stringValue = "true")
class ControlledDownHealthCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.down("controlled-test-readiness");
    }
}
```

El cuerpo comprobado bajo ese perfil es:

```json
{
  "status": "DOWN",
  "checks": [
    {
      "name": "controlled-test-readiness",
      "status": "DOWN"
    }
  ]
}
```

Es una prueba de la respuesta 503 documentada, no una afirmacion de que
produccion ya tenga ese check. El perfil normal sigue verificando checks vacio,
lo que comprueba que el check DOWN no se filtra a las otras pruebas.

## Ejecuciones reales

Directorio de todos los comandos: raiz del repositorio independiente
`workshop-auth-backend`, no la raiz del workspace. PowerShell en Windows,
Maven existente y JDK 21. No instalar dependencias.

Preparacion:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
New-Item -ItemType Directory -Force .\target\test-data | Out-Null
```

La ruta JAVA_HOME es la usada en esta maquina; en otra, elegir su JDK 21.
AUTH_DB_URL debe configurarse antes de iniciar Maven/JVM. Las pruebas rechazan
URLs fuera del prefijo de fixtures, para no tocar la base del desarrollador.

### Ejecucion dirigida

```powershell
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/test-data/testing-endpoints.db'
mvn -B '-Dtest=AuthResourceTest,HealthResourceTest,HealthDownTest' test
$LASTEXITCODE
```

- Codigo de salida real: **0**.
- Resultado: **11 tests, 0 fallos, 0 errores, 0 omitidos**.
- [Log completo](../target/test-data/testing-endpoints.log).

El ejecutor de tests del IDE no encontro tests Java en los archivos indicados.
No se interpreta como una ejecucion correcta ni como cero pruebas necesarias:
se utilizo Maven, que si descubrio y ejecuto las clases.

### Suite completa y reporte limpio

```powershell
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/test-data/testing-full.db'
mvn -B '-Djacoco.destFile=target\test-data\testing-full.exec' `
    '-Djacoco.dataFile=target\test-data\testing-full.exec' `
    '-Djacoco.append=false' verify
$LASTEXITCODE
```

- Codigo de salida real: **0**, BUILD SUCCESS.
- Resultado: **27 tests, 0 fallos, 0 errores, 0 omitidos**.
- [Log completo](../target/test-data/testing-full.log).
- [Reportes Surefire](../target/surefire-reports).
- [Informe JaCoCo HTML](../target/site/jacoco/index.html).
- [Informe JaCoCo XML](../target/site/jacoco/jacoco.xml).

| Clase | Tests | Fallos | Errores | Omitidos |
|---|---:|---:|---:|---:|
| AuthResourceTest | 8 | 0 | 0 | 0 |
| HealthResourceTest | 2 | 0 | 0 | 0 |
| HealthDownTest | 1 | 0 | 0 | 0 |
| AuthenticationServiceTest | 3 | 0 | 0 | 0 |
| LegacyDatabaseTest | 5 | 0 | 0 | 0 |
| PasswordHashMigrationTest | 5 | 0 | 0 | 0 |
| PasswordHasherTest | 3 | 0 | 0 | 0 |

Los metodos con tablas de entradas cuentan como un test por metodo en Surefire,
no como un test por cada iteracion. Los logs ERROR de fallos SQL/hash inducidos
son esperados; las aserciones pasan. No se descartan excepciones inesperadas.

## Cobertura: reporte y metodo de medicion

Meta de [REGLAS.md](../REGLAS.md): al menos 85% del codigo modificado.
Los porcentajes de esta seccion describen el baseline del 2026-10-19 y no
la cobertura del cambio RS256, que se registra al final. JaCoCo mide clases de
produccion, no la cobertura del propio codigo de tests.

Ademas de ejecutar todos los nuevos metodos, se midio el codigo de produccion
del diff local acumulado contra main:

- **Lineas ejecutables nuevas/modificadas: 142/150 = 94,67%.**
- **Backend completo: 175/183 = 95,63%.**
- Archivo de ejecucion especifico, append=false, para no acumular resultados
  de ejecuciones anteriores.

Metodo: leer sourcefile/line del XML JaCoCo; una linea con ci > 0 esta cubierta.
Obtener lineas nuevas/modificadas comparando cada fuente actual con
`git show main:<ruta>` mediante SequenceMatcher; en clases nuevas se consideran
todas sus lineas. Intersectar ambos conjuntos, excluyendo lineas sin instrucciones
ejecutables. Dividir cubiertas entre cubiertas mas no cubiertas.
Esto mide lineas, no ramas ni un umbral impuesto automaticamente por Maven.

Comando de medicion utilizado desde la raiz, con Python ya disponible:

```powershell
@'
from pathlib import Path
import difflib, subprocess, xml.etree.ElementTree as ET
root = Path.cwd()
report = ET.parse(root / "target/site/jacoco/jacoco.xml").getroot()
sources = {s.attrib["name"]: s for s in report.findall("./package/sourcefile")}
covered = missed = 0
for file in (root / "src/main/java/com/bcp/workshop/auth").glob("*.java"):
    rel = file.relative_to(root).as_posix()
    baseline = subprocess.run(
        ["git", "show", "main:" + rel], capture_output=True, text=True, encoding="utf-8")
    before = baseline.stdout.splitlines() if baseline.returncode == 0 else []
    after = file.read_text(encoding="utf-8").splitlines()
    changed = set()
    for tag, a, b, start, end in difflib.SequenceMatcher(a=before, b=after).get_opcodes():
        if tag in ("replace", "insert"):
            changed.update(range(start + 1, end + 1))
    for line in sources[file.name].findall("line"):
        if int(line.attrib["nr"]) in changed:
            if int(line.attrib["ci"]):
                covered += 1
            else:
                missed += 1
print(f"{covered}/{covered + missed} = {100 * covered / (covered + missed):.2f}%")
assert covered / (covered + missed) >= .85
'@ | python -
```

Salida real: `142/150 = 94.67%`. Codigo de salida: **0**.
No se modificaron POM, CodeQL, Dependency Review ni JaCoCo.

## Fallos, defectos conocidos y pendientes

### Fallos de las ejecuciones nuevas

Ninguno en Maven: tanto seleccion dirigida como suite completa pasaron.
El descubrimiento Java del ejecutor IDE no estuvo disponible; se uso Maven.

### Defectos reportados aparte, no ocultados por tests

- **Fallo SQL convertido en 401:** [LegacyDatabase.java:61](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L61)
  devuelve null tras SQLException; el recurso lo interpreta como credenciales
  invalidas. La prueba explicita es de caracterizacion del contrato, no valida
  que ese manejo sea correcto. No se cambia ni se marca corregido aqui.
- **Errores 500 verbosos (H-06):** [AuthResource.java:54](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L54)
  expone mensaje y clase. La caracterizacion no es una recomendacion de seguridad.
- **CI sin entorno aislado:** el runner actual de
  [ci.yml:21](../.github/workflows/ci.yml#L21) no establece AUTH_DB_URL.
  [LegacyDatabaseTest.java:80](../src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java#L80)
  exige la variable. Los resultados anteriores corresponden a los comandos
  configurados, no a una afirmacion de CI remoto verde. Sigue siendo un
  bloqueante conocido de la revision anterior; no se arreglo fuera del alcance.

Estos apuntes no crean un segundo seguimiento de estados: la fuente de estados
sigue siendo [HALLAZGOS.md](../HALLAZGOS.md).

### Pendientes y pruebas propuestas, no ejecutadas

- Fallos de inicializacion envueltos por CDI, cuerpos ausentes o conversiones
  no especificadas: preguntas abiertas del contrato, no expectativas inventadas.
- Perfil prod, intermediarios, limites de transporte y conectividad:
  no probados por esta suite.
- El 503 de produccion sigue sin un escenario propio que lo provoque; la prueba
  valida SmallRye con un check exclusivo de test. No demuestra salud de SQLite.
- Los 500 genericos con/sin mensaje se prueban a nivel de recurso; una prueba HTTP
  con servicio reemplazado podria agregarse si se acuerda esa infraestructura.
- Workflows remotos de CodeQL y Dependency Review: no ejecutados.

Las bases SQLite de las ejecuciones de esta entrega se eliminaron al terminar;
logs, reportes y archivo de cobertura se conservaron bajo target para inspeccion.

## Documentos consultados

- [ENDPOINTS.md](ENDPOINTS.md): casos y respuestas exigidos.
- [REGLAS.md](../REGLAS.md): caracterizacion, no reducir gates y cobertura.
- [HALLAZGOS.md](../HALLAZGOS.md): seccion de revision previa y bloqueante CI.
- [ci.yml](../.github/workflows/ci.yml) y [pom.xml](../pom.xml):
  ejecucion automatica y herramientas existentes.

Tambien se consultaron las pruebas existentes de AuthResource y el codigo
referenciado para justificar la induccion de errores.

## Implementacion RS256 e integracion (2026-10-20)

### Backend de autenticacion

Comando desde el worktree aislado, con JDK 21 y base SQLite bajo `target`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/test-data/token-final3.db'
mvn -q clean verify
```

Resultado real: **29 tests, 0 fallos, 0 errores**. JaCoCo cubre 8/8 lineas de
`SignedTokenService` y 19/19 de `SigningKeyProvider`. Las pruebas verifican
firma RS256, identidad, rol, expiracion, rechazos por clave ausente/invalida y
preservacion del sobre HTTP de login.

### API de requisitos

Con el entorno aislado de Python 3.13.15 y dependencias de `requirements.txt`:

```powershell
python -m pytest -q --cov=app --cov-report=term-missing tests
python -m pip check
```

Resultado real: **8 tests aprobados**, cobertura global de `app.main.py` **87%**
y `pip check`: **No broken requirements found**. Se observaron advertencias de
deprecacion preexistentes de `FastAPI.on_event` y AnyIO.

### Integracion HTTP entre servicios

Con un par RSA efimero fuera de los repositorios: `POST /auth/login` devolvio
HTTP 200, `expiresIn=3600` y un JWT de tres segmentos; al enviarlo como Bearer,
`GET /api/tasks` respondio HTTP 200 con las dos tareas esperadas. La clave
efimera se elimino al terminar la comprobacion; no se imprimio ni versiono.
