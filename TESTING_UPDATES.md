# Pruebas Locales del Sistema de Auto-Update

Este documento explica cómo probar el sistema de auto-actualización (auto-update) en tu máquina local sin necesidad de subir versiones o releases a GitHub.

## 1. Configuración de Propiedades de Desarrollo

En el archivo `src/main/resources/application.properties`, puedes sobrescribir la URL de actualización para que apunte a un servidor local:

```properties
# URL de producción (GitHub)
# app.update.check-url=https://api.github.com/repos/jonolabarrieta/maven-builder-web-ui/releases/latest

# URL para pruebas locales (Mock)
app.update.check-url=http://localhost:8080/update-mock.json
app.version=1.1.0
```

## 2. Preparar el Servidor Mock Local

1. Crea un directorio de pruebas temporales fuera del código fuente, por ejemplo `update-test-server/`.
2. Dentro de este directorio, crea un archivo llamado `update-mock.json` con la siguiente estructura (que simula la API de GitHub):

```json
{
  "tag_name": "v1.2.0",
  "html_url": "http://localhost:8080/release-notes",
  "body": "### Novedades en v1.2.0\n\n- Añadida nueva funcionalidad de prueba local.\n- Correcciones de diseño en la barra lateral.",
  "assets": [
    {
      "name": "mvn-builder.jar",
      "browser_download_url": "http://localhost:8080/mvn-builder-mock.jar"
    },
    {
      "name": "mvn-builder-linux",
      "browser_download_url": "http://localhost:8080/mvn-builder-mock-linux"
    },
    {
      "name": "mvn-builder-windows.exe",
      "browser_download_url": "http://localhost:8080/mvn-builder-mock-windows.exe"
    }
  ]
}
```

3. Coloca una copia compilada del JAR (o un JAR modificado de prueba) renombrado como `mvn-builder-mock.jar` (o el binario correspondiente) en ese mismo directorio.
4. Levanta un servidor web simple en ese directorio en el puerto `8080`. Por ejemplo:
   * Con Python:
     ```bash
     python3 -m http.server 8080
     ```
   * Con Node.js/npx:
     ```bash
     npx http-server -p 8080
     ```

## 3. Realizar la Prueba de Comprobación

1. Inicia la aplicación principal de `MvnBuilder` (con `app.version=1.1.0` y apuntando al puerto `8080` de tu servidor local).
2. Ve a la página de **Configuración** (`http://localhost:3333/settings`).
3. Verás la sección **Centro de Actualizaciones** indicando la versión actual (`v1.1.0`).
4. Haz clic en **Buscar Actualización**.
5. La UI enviará una solicitud al controlador, que consultará tu JSON local y devolverá la información de la versión `v1.2.0` y las notas de la versión.

## 4. Realizar la Prueba de Instalación

1. Haz clic en **Descargar e Instalar**.
2. La aplicación descargará el archivo `mvn-builder-mock.jar` (o binario), creará el script `update.bat` o `update.sh` en el directorio temporal de tu sistema y llamará al script detached antes de cerrarse.
3. El script esperará a que el proceso principal muera, sobrescribirá el archivo `.jar` (o binario) original con el nuevo descargado y volverá a iniciar la aplicación.
4. Tras el reinicio, accede de nuevo a `http://localhost:3333/settings` y verifica que ahora indica **Versión actual: v1.2.0**.
