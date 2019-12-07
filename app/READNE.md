gradlew clean build
apksigner sign --key <build>/device/ncr/rubicon/security/rubicon.pk8 --cert <build>/device/ncr/rubicon/security/rubicon.x509.pem --out ./app/build/outputs/apk/release/app-release.apk ./app/build/outputs/apk/release/app-release-unsigned.apk
