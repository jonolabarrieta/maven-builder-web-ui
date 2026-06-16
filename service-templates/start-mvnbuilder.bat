@echo off
:: This script starts MvnBuilder in the background without showing a console window on Windows.
::
:: Instructions:
:: 1. Copy this script and the compiled JAR file (mvn-builder-1.1.0.jar) to a permanent directory (e.g. C:\MvnBuilder).
:: 2. Update the path below to point to the absolute path of your JAR file.
:: 3. Run 'shell:startup' (via Win + R) and create a shortcut to this .bat file inside that folder to launch on login.

start javaw -jar "target\mvn-builder-1.1.0.jar"
