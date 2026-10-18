# SPDX-License-Identifier: FSL-1.1-ALv2
"""Builds the Brief's design prototype: the real engine, compiled to JavaScript, behind one page.

The prototype is the look-and-interaction source for the Brief screens (CLAUDE.md law 6). It runs
the same Kotlin the app runs (brief, the style detector, core-geometry, the sfnt reader), so every
question, number and piece of advice it shows is the engine's own. kotlinx.serialization has no
JavaScript build here, so two small shims stand in for it: the style atlas is decoded with the
browser's JSON parser (facade/AtlasJson.kt), and the Ethos record is copied out of project/Model.kt.

    python3 ui/v1-screens/brief/build.py            # writes ui/v1-screens/brief/build/brief-prototype.html

Needs a JDK and the Kotlin compiler (kotlinc-js) at the version in gradle/libs.versions.toml: set
KOTLINC_HOME, or the script downloads the compiler from JetBrains' GitHub releases into
~/.cache/typewright/. Nothing is fetched at run time: the page is one self-contained file.
"""

import base64
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
BUILD = HERE / "build"
SRC = BUILD / "src"


def kotlin_version() -> str:
    text = (ROOT / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
    return re.search(r'^kotlin = "([^"]+)"', text, re.M).group(1)


def kotlinc_home() -> Path:
    env = os.environ.get("KOTLINC_HOME")
    if env:
        return Path(env)
    version = kotlin_version()
    cache = Path.home() / ".cache" / "typewright" / f"kotlinc-{version}"
    home = cache / "kotlinc"
    if not (home / "bin" / "kotlinc-js").exists():
        cache.mkdir(parents=True, exist_ok=True)
        url = f"https://github.com/JetBrains/kotlin/releases/download/v{version}/kotlin-compiler-{version}.zip"
        archive = cache / "kotlin-compiler.zip"
        print(f"downloading {url}", file=sys.stderr)
        urllib.request.urlretrieve(url, archive)
        with zipfile.ZipFile(archive) as z:
            z.extractall(cache)
        for tool in (home / "bin").iterdir():
            tool.chmod(0o755)
    return home


def copy_sources() -> None:
    if SRC.exists():
        shutil.rmtree(SRC)
    SRC.mkdir(parents=True)
    trees = [
        (ROOT / "core-geometry/src/commonMain", lambda p: True),
        (ROOT / "core-font/src/commonMain/kotlin/com/asoc/typewright/core/font/sfnt", lambda p: p.name != "SfntFontUfoBridge.kt"),
        (ROOT / "qa/corpus/src/commonMain/kotlin/com/asoc/typewright/qa/corpus/style", lambda p: True),
        (ROOT / "brief/src/commonMain", lambda p: True),
        (HERE / "facade", lambda p: True),
    ]
    for base, keep in trees:
        for path in sorted(base.rglob("*.kt")):
            if keep(path):
                rel = path.relative_to(ROOT)
                target = SRC / "__".join(rel.parts)
                target.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")
    (SRC / "StyleAtlasShim.kt").write_text(style_atlas_shim(), encoding="utf-8")
    (SRC / "EthosShim.kt").write_text(ethos_shim(), encoding="utf-8")


def style_atlas_shim() -> str:
    """qa/corpus's StyleAtlas.kt without kotlinx.serialization: same classes, no decoder or loader."""
    text = (ROOT / "qa/corpus/src/commonMain/kotlin/com/asoc/typewright/qa/corpus/StyleAtlas.kt").read_text(encoding="utf-8")
    text = re.sub(r"^import kotlinx\.serialization.*\n", "", text, flags=re.M)
    text = re.sub(r"^@Serializable\n", "", text, flags=re.M)
    text = re.sub(r"^private val atlasJson = .*\n", "", text, flags=re.M)
    # Drop the decoder (facade/AtlasJson.kt replaces it), the classpath loader and its path.
    for name in ("decodeStyleAtlas", "loadStyleAtlas", "STYLE_ATLAS_RESOURCE_PATH"):
        text = re.sub(r"(/\*\*(?:(?!\*/).)*\*/\n)?[^\n]*\b(fun|val) " + name + r"\b[^\n]*\n", "", text, flags=re.S)
    models = (ROOT / "qa/corpus/src/commonMain/kotlin/com/asoc/typewright/qa/corpus/CorpusModels.kt").read_text(encoding="utf-8")
    quartiles = re.search(r"data class Quartiles\((?:.|\n)*?\n\)\n", models).group(0)
    return text + "\n" + quartiles


def ethos_shim() -> str:
    """project/Model.kt's declared-brief types without the rest of the project model: BriefSource,
    ContrastModel, Use, Brief and StyleClass (Ethos's own siblings, one contiguous block in
    Model.kt) alongside Ethos itself. brief/BriefEdits.kt's Brief.withEthos/withConfirmedStyleClass
    need all of them to compile here, even though the prototype itself only ever touches Ethos
    directly -- dead code elimination (-Xir-dce) drops the unused ones from the built page."""
    text = (ROOT / "project/src/commonMain/kotlin/com/asoc/typewright/project/Model.kt").read_text(encoding="utf-8")
    start = text.index("enum class BriefSource")
    start = text.rfind("/**", 0, start)
    end = text.index("\ndata class WorkbookRecord(")
    return "package com.asoc.typewright.project\n\n" + text[start:end].rstrip() + "\n"


def compile_engine(home: Path) -> str:
    kotlinc = str(home / "bin" / "kotlinc-js")
    stdlib = str(home / "lib" / "kotlin-stdlib-js.klib")
    klib = BUILD / "klib"
    out = BUILD / "js"
    for d in (klib, out):
        if d.exists():
            shutil.rmtree(d)
    sources = [str(p) for p in sorted(SRC.glob("*.kt"))]
    run([kotlinc, "-nowarn", "-Xir-produce-klib-dir", "-libraries", stdlib, "-ir-output-dir", str(klib),
         "-ir-output-name", "brief-prototype", *sources])
    run([kotlinc, "-nowarn", "-Xir-produce-js", f"-Xinclude={klib}", "-libraries", stdlib, "-ir-output-dir", str(out),
         "-ir-output-name", "briefPrototype", "-module-kind", "umd", "-target", "es2015", "-Xir-dce"])
    js = (out / "briefPrototype.js").read_text(encoding="utf-8")
    return minify(js)


def run(cmd: list[str]) -> None:
    result = subprocess.run(cmd, capture_output=True, text=True)
    noise = ("Picked up JAVA_TOOL_OPTIONS", "warning: the argument '-Xir-produce-klib-dir' is deprecated")
    for line in (result.stdout + result.stderr).splitlines():
        if not line.startswith(noise):
            print(line, file=sys.stderr)
    if result.returncode != 0:
        sys.exit(f"failed: {' '.join(cmd[:3])} …")


def minify(js: str) -> str:
    """terser when npx can reach it; the unminified file otherwise (it works the same, only larger)."""
    npx = shutil.which("npx")
    if not npx:
        return js
    src = BUILD / "engine.js"
    src.write_text(js, encoding="utf-8")
    result = subprocess.run([npx, "--yes", "terser@5", str(src), "--compress", "--mangle"], capture_output=True, text=True)
    return result.stdout if result.returncode == 0 and result.stdout else js


def cue_font_faces() -> str:
    manifest = json.loads((ROOT / "data/brief-cues/manifest.json").read_text(encoding="utf-8"))["cues"]
    rules = []
    for key, entry in manifest.items():
        data = base64.b64encode((ROOT / "data/brief-cues" / entry["file"]).read_bytes()).decode("ascii")
        rules.append(f"@font-face{{font-family:'cue-{key}';src:url(data:font/ttf;base64,{data}) format('truetype');font-display:block}}")
    return "\n".join(rules)


def safe_script(text: str) -> str:
    return text.replace("</", "<\\/")


def main() -> None:
    BUILD.mkdir(exist_ok=True)
    copy_sources()
    engine = compile_engine(kotlinc_home())
    page = (HERE / "prototype.html").read_text(encoding="utf-8")
    atlas = (ROOT / "data/style-atlas-latin.json").read_text(encoding="utf-8")
    example = base64.b64encode((ROOT / "fonts/HyleDeco-Regular.ttf").read_bytes()).decode("ascii")
    page = page.replace("/*@CUE_FONTS@*/", cue_font_faces())
    page = page.replace("<!--@ATLAS@-->", f'<script type="application/json" id="atlas">{safe_script(atlas)}</script>')
    page = page.replace("<!--@EXAMPLE_FONT@-->", f'<script type="text/plain" id="example-font">{example}</script>')
    page = page.replace("<!--@ENGINE@-->", f"<script>{safe_script(engine)}</script>")
    out = BUILD / "brief-prototype.html"
    out.write_text(page, encoding="utf-8")
    print(f"{out.relative_to(ROOT)}  {out.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
