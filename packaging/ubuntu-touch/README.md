# packaging/ubuntu-touch

Click packaging for Ubuntu Touch. Read `docs/UBUNTU_TOUCH_PORT.md` first: it says which click is the port
(native, JVM-in-click) and which is a labelled companion (`preview/`, the Wasm build in `webapp-container`).

Nothing here has run on a device. CI builds the clicks (`.github/workflows/ubuntu-touch.yml`); locally:

```
bash packaging/ubuntu-touch/test-launcher.sh && bash packaging/ubuntu-touch/preview/test-preview.sh
python3 tools/check_ubuntu_touch.py
./gradlew :app-desktop:createDistributable        # on an arm64 Linux host only
packaging/ubuntu-touch/stage.sh
cd build/ubuntu-touch/stage && clickable build --arch arm64
```

Click names (`typewright.asoc`, `typewright-preview.asoc`) are placeholders until a `NAMES.md` row exists; they live
only in the two `manifest.json` files.
