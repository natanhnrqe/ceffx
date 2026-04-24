@echo off
:: Copyright (c) 2013 The Chromium Embedded Framework Authors. All rights
:: reserved. Use of this source code is governed by a BSD-style license
:: that can be found in the LICENSE file.
set RETURNCODE=
setlocal
cd ..
if "%1" == "" (
echo ERROR: Please specify a target platform: win32 or win64
set ERRORLEVEL=1
goto end
)
set DISTRIB_PLATFORM=%1
set DISTRIB_PATH=.\dist\%1
set DISTRIB_BIN_PATH=%DISTRIB_PATH%\bin
set DISTRIB_DOCS_PATH=%DISTRIB_PATH%\docs
set DISTRIB_LIB_PATH=%DISTRIB_PATH%\bin\lib\%1
set OUT_PATH=.\out\%1
set OUT_BINARY_PATH=.\build\native\Release
if not exist "%OUT_BINARY_PATH%" (
echo ERROR: Native Release build output path does not exist
goto end
)
set OUT_DOCS_PATH=.\out\docs
set SOURCE_PATH=.\java
set JOGAMP_PATH=.\third_party\jogamp
set JOGAMP_JAR_PATH=%JOGAMP_PATH%\jar
set TOOLS_DISTRIB_PATH=.\tools\distrib\%1
if "%1" == "win32" (
set JOGAMP_JAR_SUFFIX=i586
) else (
set JOGAMP_JAR_SUFFIX=amd64
)
:: Copy JAR files to the bin directory.
if not exist "%DISTRIB_BIN_PATH%" mkdir "%DISTRIB_BIN_PATH%"
copy "%JOGAMP_JAR_PATH%\gluegen-rt.jar" "%DISTRIB_BIN_PATH%"
copy "%JOGAMP_JAR_PATH%\gluegen-rt-natives-windows-%JOGAMP_JAR_SUFFIX%.jar" "%DISTRIB_BIN_PATH%"
copy "%JOGAMP_JAR_PATH%\jogl-all.jar" "%DISTRIB_BIN_PATH%"
copy "%JOGAMP_JAR_PATH%\jogl-all-natives-windows-%JOGAMP_JAR_SUFFIX%.jar" "%DISTRIB_BIN_PATH%"
copy "%SOURCE_PATH%\webviewpro-core\target\webviewpro-core*.jar" "%DISTRIB_BIN_PATH%\webviewpro-core.jar"
:: Copy test programs and its sources to the bin directory.
copy "%OUT_PATH%\webviewpro-tests\target\webviewpro-tests*.jar" "%DISTRIB_BIN_PATH%\webviewpro-tests.jar"
xcopy /sfy "%SOURCE_PATH%\webviewpro-tests\src\main\java\tests\*" "%DISTRIB_BIN_PATH%\tests\"
:: Copy CEF Release files to the lib directory.
if not exist "%DISTRIB_LIB_PATH%" mkdir "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\chrome_elf.dll" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\d3dcompiler_43.dll" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\d3dcompiler_47.dll" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\icudt.dll" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\ceffx.dll" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\ceffx_helper.exe" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\icudtl.dat" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\libcef.dll" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\libEGL.dll" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\libGLESv2.dll" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\natives_blob.bin" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\snapshot_blob.bin" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\v8_context_snapshot.bin" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\vk_swiftshader.dll" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\vk_swiftshader_icd.json" "%DISTRIB_LIB_PATH%"
copy "%OUT_BINARY_PATH%\vulkan-1.dll" "%DISTRIB_LIB_PATH%"
xcopy /sfy "%OUT_BINARY_PATH%\*.pak" "%DISTRIB_LIB_PATH%"
:: Copy documentation to the docs directory.
xcopy /sfy "%OUT_DOCS_PATH%\*" "%DISTRIB_DOCS_PATH%\"
:: Copy miscellaneous files to the root directory.
copy ".\LICENSE.txt" "%DISTRIB_PATH%"
xcopy /sfy "%JOGAMP_PATH%\*.LICENSE.txt" "%DISTRIB_PATH%"
:: Cannot use a variable substitution for /exclude because otherwise xcopy will fail.
:: NOTE: /exclude does not support paths with spaces - this is a limitation of xcopy.
xcopy /sfy "%TOOLS_DISTRIB_PATH%\*" "%DISTRIB_PATH%" /exclude:.\tools\distrib\EXCLUDE_FILES.txt
:end
endlocal & set RETURNCODE=%ERRORLEVEL%
goto omega
:returncode
exit /B %RETURNCODE%
:omega
call :returncode %RETURNCODE%
