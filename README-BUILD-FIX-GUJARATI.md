# Naliyapara Parivar — Build Fix

આ આવૃત્તિમાં GitHub Actions build error fix કરવામાં આવ્યો છે.

મુખ્ય fix:
- `androidx.core:core:1.17.0` ને `1.16.0` કરવામાં આવ્યું છે.
- `compileSdk 35` અને Android Gradle Plugin 8.8.2 સાથે compatible રાખ્યું છે.
- App version `1.2` / versionCode `3`.
- Website WebView, external links અને Firebase notification code યથાવત્ રાખવામાં આવ્યા છે.

## GitHub Actions
`.github/workflows/build-apk.yml` માં `actions/checkout@v5`, `actions/setup-java@v5` અને `gradle/actions/setup-gradle@v5` છે.

Workflow `main` branch પર push અથવા manual `workflow_dispatch` થી build થશે.
