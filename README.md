# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.

## Correcciones H-01 y H-02

En este trabajo se parametrizó el acceso SQL y se reemplazó el almacenamiento
activo de contraseñas por PBKDF2-HMAC-SHA256: 600000 iteraciones, salt aleatorio
de 16 bytes y derivado de 32 bytes. No se agregaron dependencias.
El token legacy y los demás hallazgos del laboratorio permanecen pendientes;
estas correcciones no convierten el proyecto en apto para producción.

### Migración de SQLite

Antes de iniciar esta versión sobre una base existente:

1. Detén el backend y cualquier otro escritor de esa base. Respalda la base
   cerrada en una ubicación local protegida dentro del repositorio; si utilizas
   `AUTH_DB_URL`, identifica primero el archivo correspondiente.
2. Inicia normalmente. Al inicializarse el bean de persistencia, se migra la
   tabla `legacy_users` en una transacción y se registra la versión en
   `auth_schema_version`. Se conservan usuarios, ids, nombres y roles.
3. Verifica el login de los usuarios existentes. Los siguientes arranques
   validan el formato sin volver a derivar los hashes.
4. Si la migración falla, se revierte la transacción, se registra el error y
   se impide consultar credenciales desde ese bean. No edites la versión ni
   introduzcas passwords en texto plano en una base ya migrada.

Conserva el respaldo solo durante la validación y recuperación acordada; después
elimínalo de forma controlada. Backups, journals y páginas libres de SQLite
pueden conservar valores anteriores: esta migración no garantiza borrado
forense. No publiques ni agregues bases o respaldos a Git.

La credencial de `trainer` se conserva como `Trainer123!`, igual que en el
código original. La tabla de usuarios de `docs/ENDPOINTS.md` indica otra clave;
esa discrepancia documental se mantiene sin editar el contrato por indicación
humana. Las pruebas caracterizan el comportamiento real confirmado.

### Pruebas aisladas y cobertura

Usa JDK 21 también para Maven (`mvn --version` debe mostrarlo).
Desde PowerShell, en la raíz de este repositorio:

```powershell
New-Item -ItemType Directory -Force .\target\test-data | Out-Null
$env:AUTH_DB_URL = 'jdbc:sqlite:./target/test-data/local-tests.db'
mvn verify
```

Las pruebas HTTP y de repositorio rechazan una URL que no apunte a
`./target/test-data/`. Las pruebas de migración crean y eliminan sus propias
fixtures allí. Para repetir desde una base vacía, usa otro nombre de archivo
de prueba; nunca apuntes a la base de desarrollo.

JaCoCo genera `target/site/jacoco/index.html`. El umbral del código modificado
es 85% de líneas cubiertas: debe comprobarse en el informe, no deducirse del
éxito de Maven. CodeQL, Dependency Review y la configuración de JaCoCo no se
modificaron.
