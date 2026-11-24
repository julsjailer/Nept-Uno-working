#!/usr/bin/env bash
# Instala Neptuno-debug.apk en el emulador y registra el resultado exacto de PackageManager (en emu.log).
{
  echo "--- dispositivo"; adb shell getprop ro.build.version.release; adb shell getprop ro.product.cpu.abi
  PREV=$(gh release list --limit 10 --json tagName --jq '[.[]|select(.tagName|startswith("v1.0."))|.tagName] | .[1]' 2>/dev/null)
  if [ -n "$PREV" ] && [ "$PREV" != "null" ]; then
    echo "--- actualización REAL: instalar $PREV y luego la versión nueva encima"
    rm -rf prev && mkdir prev && gh release download "$PREV" --pattern '*.apk' --dir prev 2>&1 | tail -2
    ls -l prev
    adb install -r -d prev/*.apk 2>&1
    adb shell dumpsys package com.aistudio.neptuno.k7v9xm | grep -E "versionCode" | head -1
    adb install -r Neptuno-debug.apk 2>&1
    adb shell dumpsys package com.aistudio.neptuno.k7v9xm | grep -E "versionCode" | head -1
    adb uninstall com.aistudio.neptuno.k7v9xm 2>&1
  else
    echo "--- sin versión previa en releases para probar la actualización real"
  fi
  echo "--- instalación limpia"; adb install -r -d Neptuno-debug.apk 2>&1
  echo "--- paquete instalado"; adb shell pm list packages | grep neptuno || echo "NO instalado"
  adb shell dumpsys package com.aistudio.neptuno.k7v9xm | grep -E "versionCode|versionName|minSdk|targetSdk|signatures|flags=" | head
  echo "--- reinstalación encima (actualización)"; adb install -r Neptuno-debug.apk 2>&1
  echo "--- arranque"; adb shell monkey -p com.aistudio.neptuno.k7v9xm -c android.intent.category.LAUNCHER 1 2>&1 | tail -2
  sleep 8
  adb shell pidof com.aistudio.neptuno.k7v9xm || echo "proceso no está vivo"
  echo "--- logcat (errores de la app / PackageManager)"
  adb logcat -d -s AndroidRuntime:E PackageManager:W PackageInstaller:W 2>&1 | tail -40
} > emu.log 2>&1
cat emu.log
