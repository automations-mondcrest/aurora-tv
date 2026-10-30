#!/usr/bin/env bash
# Builds AuroraHome.apk without Gradle.
# Usage: ./build.sh <keystore> <alias>   (prompts for the keystore password)
set -euo pipefail
KS="${1:?keystore path}"; ALIAS="${2:?key alias}"
JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
cd "$(dirname "$0")/app"
rm -rf build && mkdir -p build/gen build/classes
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -I "$JAR"
javac -nowarn -source 8 -target 8 -bootclasspath "$JAR" -classpath "$JAR" -d build/classes \
  build/gen/com/mondcrest/aurora/R.java src/com/mondcrest/aurora/*.java
dalvik-exchange --dex --min-sdk-version=21 --output=build/classes.dex build/classes
aapt package -f -M AndroidManifest.xml -S res -A assets -I "$JAR" -F build/unsigned.apk
(cd build && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks "$KS" --ks-key-alias "$ALIAS" --out build/AuroraHome.apk build/aligned.apk
apksigner verify build/AuroraHome.apk
echo "Built app/build/AuroraHome.apk"
