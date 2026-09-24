# app-web

The Kotlin/Wasm browser application shell: `index.html` and an entry point that mounts `TypewrightApp()` into the page. Build it with `./gradlew :app-web:wasmJsBrowserDistribution` and serve `app-web/build/dist/wasmJs/productionExecutable/` over HTTP.
