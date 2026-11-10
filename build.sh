#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to an Android SDK containing platform 30 and build-tools 34.0.0}"
BT="$ANDROID_SDK_ROOT/build-tools/34.0.0"
AJ="$ANDROID_SDK_ROOT/platforms/android-30/android.jar"
mkdir -p build/gen build/classes build/dex build/test
java -m jdk.compiler/com.sun.tools.javac.Main -d build/test src/local/routervpndot/StateLogic.java src/local/routervpndot/DetectionState.java tests/StateLogicTest.java tests/DetectionStateTest.java
java -cp build/test StateLogicTest
java -cp build/test DetectionStateTest
java -m jdk.compiler/com.sun.tools.javac.Main -d build/test src/local/routervpndot/StateLogic.java src/local/routervpndot/StartupBanner.java tests/StartupBannerTest.java
java -cp build/test StartupBannerTest
java -m jdk.compiler/com.sun.tools.javac.Main -d build/test src/local/routervpndot/LogTimeline.java tests/LogTimelineTest.java
java -cp build/test LogTimelineTest
java -m jdk.compiler/com.sun.tools.javac.Main -d build/test src/local/routervpndot/IpLabel.java tests/IpLabelTest.java
java -cp build/test IpLabelTest
java -m jdk.compiler/com.sun.tools.javac.Main -d build/test src/local/routervpndot/StateLogic.java src/local/routervpndot/VpnDuration.java tests/VpnDurationTest.java
java -cp build/test VpnDurationTest
java -m jdk.compiler/com.sun.tools.javac.Main -d build/test src/local/routervpndot/RouterVpn.java tests/RouterVpnTest.java
java -cp build/test RouterVpnTest
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" link -I "$AJ" --manifest AndroidManifest.xml --java build/gen -o build/base.apk build/resources.zip
KEY="$({ tr -d '[:space:]' < proxycheck.key; } 2>/dev/null || true)"
[[ "$KEY" =~ ^[A-Za-z0-9-]*$ ]] || { echo "proxycheck.key must contain only letters, digits and dashes" >&2; exit 1; }
printf 'package local.routervpndot;\nfinal class Secrets{static final String PROXYCHECK_KEY="%s";}\n' "$KEY" > build/gen/local/routervpndot/Secrets.java
echo "proxycheck.io key: $([[ -n "$KEY" ]] && echo included || echo none)"
java -m jdk.compiler/com.sun.tools.javac.Main --release 8 -classpath "$AJ" -d build/classes src/local/routervpndot/*.java build/gen/local/routervpndot/*.java
"$BT/d8" --min-api 26 --lib "$AJ" --output build/dex build/classes/local/routervpndot/*.class
python3 - <<'PY'
import zipfile,shutil
shutil.copyfile('build/base.apk','build/unsigned.apk')
with zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    z.write('build/dex/classes.dex','classes.dex')
PY
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [[ ! -f local-test.jks ]]; then
 keytool -genkeypair -keystore local-test.jks -storepass android -keypass android -alias routerdot -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Router VPN Dot Local Test'
fi
"$BT/apksigner" sign --ks local-test.jks --ks-key-alias routerdot --ks-pass pass:android --key-pass pass:android --out RouterVPNDot-0.15.apk build/aligned.apk
"$BT/apksigner" verify --verbose RouterVPNDot-0.15.apk
"$BT/zipalign" -c 4 RouterVPNDot-0.15.apk
