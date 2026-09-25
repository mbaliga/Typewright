// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

/**
 * A project as a single .zip, in common code: the web's export and import where there is no
 * folder access, and a portable archive elsewhere. Compression is injected (the browser's
 * `CompressionStream("deflate-raw")`, java.util.zip on the JVM), so this object only handles
 * the container: PKWARE's zip format without zip64, entries at most 65,535.
 *
 * Output is deterministic: entries sorted by name, every timestamp 1980-01-01 00:00, fixed
 * permissions (0644 files, 0755 directories), UTF-8 names. The same project gives the same bytes
 * whenever the deflater is deterministic.
 */
object ProjectZip {
    /** The folders a new project is created with; written as directory entries even when empty, so a round trip keeps them. */
    val EMPTY_FOLDERS: List<String> = listOf("comparisons", "lessons", "scrapbook")

    /** Top-level folders archivers add that are never project content (macOS Finder's resource forks). */
    private val IGNORED_TOP_FOLDERS = setOf("__MACOSX")

    /**
     * Zips [files] under the folder [root] (the project's name; `""` puts entries at the top).
     * Each file is deflated with [deflate] (raw deflate, no zlib header) when that makes it
     * smaller, otherwise stored; a null [deflate] stores everything. Every parent directory and
     * each of [directories] gets a directory entry.
     */
    suspend fun write(
        files: ProjectFiles,
        root: String,
        deflate: (suspend (ByteArray) -> ByteArray)?,
        directories: Collection<String> = EMPTY_FOLDERS,
    ): ByteArray {
        if (root.isNotEmpty()) ProjectPath.validate(root)
        val prefix = if (root.isEmpty()) "" else "$root/"
        val entries = HashMap<String, ByteArray?>()

        fun addDirectoryWithParents(directory: String) {
            var slash = directory.indexOf('/')
            while (slash >= 0) {
                entries[prefix + directory.substring(0, slash + 1)] = null
                slash = directory.indexOf('/', slash + 1)
            }
            entries["$prefix$directory/"] = null
        }
        if (root.isNotEmpty()) entries[prefix] = null
        for (path in files.listFiles()) {
            ProjectPath.validate(path)
            entries[prefix + path] = files.readBytes(path)
            val parent = ProjectPath.parent(path)
            if (parent.isNotEmpty()) addDirectoryWithParents(parent)
        }
        for (directory in directories) addDirectoryWithParents(ProjectPath.validate(directory))
        require(entries.size <= MAX_ENTRIES) { "A project zip holds at most $MAX_ENTRIES entries; this one needs ${entries.size}" }

        val out = ByteSink()
        val central = ByteSink()
        for (name in entries.keys.sorted()) {
            val content = entries.getValue(name)
            val isDirectory = content == null
            val data = content ?: ByteArray(0)
            val crc = if (isDirectory) 0 else Crc32.of(data)
            val deflated = if (isDirectory || deflate == null || data.isEmpty()) null else deflate(data).takeIf { it.size < data.size }
            val method = if (deflated != null) METHOD_DEFLATED else METHOD_STORED
            val stored = deflated ?: data
            val nameBytes = name.encodeToByteArray()
            require(nameBytes.size <= 0xFFFF) { "Entry name too long: $name" }
            val offset = out.size

            out.u32(LOCAL_HEADER)
            out.u16(VERSION_NEEDED)
            out.u16(FLAG_UTF8)
            out.u16(method)
            out.u16(DOS_TIME_1980)
            out.u16(DOS_DATE_1980)
            out.u32(crc)
            out.u32(stored.size)
            out.u32(data.size)
            out.u16(nameBytes.size)
            out.u16(0)
            out.bytes(nameBytes)
            out.bytes(stored)

            central.u32(CENTRAL_HEADER)
            central.u16(VERSION_MADE_BY_UNIX)
            central.u16(VERSION_NEEDED)
            central.u16(FLAG_UTF8)
            central.u16(method)
            central.u16(DOS_TIME_1980)
            central.u16(DOS_DATE_1980)
            central.u32(crc)
            central.u32(stored.size)
            central.u32(data.size)
            central.u16(nameBytes.size)
            central.u16(0)
            central.u16(0)
            central.u16(0)
            central.u16(0)
            central.u32(if (isDirectory) DIRECTORY_ATTRIBUTES else FILE_ATTRIBUTES)
            central.u32(offset)
            central.bytes(nameBytes)
        }
        val centralOffset = out.size
        val centralBytes = central.toByteArray()
        out.bytes(centralBytes)
        out.u32(END_OF_CENTRAL_DIRECTORY)
        out.u16(0)
        out.u16(0)
        out.u16(entries.size)
        out.u16(entries.size)
        out.u32(centralBytes.size)
        out.u32(centralOffset)
        out.u16(0)
        return out.toByteArray()
    }

    /**
     * Unzips [bytes] into project-relative paths and their contents, sorted by path. [inflate]
     * undoes raw deflate. Directory entries and a top-level `__MACOSX/` folder are skipped.
     *
     * When every file sits inside one shared top folder, that folder is stripped only if it is
     * a zipped project folder: it holds `typewright.json`, or a `.ufo` directly inside it (a
     * folder of font sources to import). Otherwise the paths are kept as they are, so a zipped
     * `.ufo` keeps its own folder and a zip written with an empty root reads back unchanged.
     *
     * Zip-slip safe: an entry whose path is absolute, climbs with `..`, or contains a backslash
     * or NUL throws [IllegalArgumentException] naming it, before anything is returned, so no
     * caller ever writes outside the project. So do encrypted, zip64 and damaged archives and
     * CRC or size mismatches.
     */
    suspend fun read(
        bytes: ByteArray,
        inflate: suspend (ByteArray) -> ByteArray,
    ): Map<String, ByteArray> {
        val source = ByteSource(bytes)
        val end = findEndOfCentralDirectory(source)
        val count = source.u16(end + 10)
        val centralOffset = source.u32(end + 16)
        require(count != 0xFFFF && centralOffset != 0xFFFFFFFFL) { "Zip64 archives are not supported" }

        val files = ArrayList<Pair<String, ByteArray>>()
        var at = centralOffset.toIntChecked()
        repeat(count) {
            require(source.u32(at) == CENTRAL_HEADER.toLong()) { "Damaged zip: bad central directory entry at $at" }
            val flags = source.u16(at + 8)
            val method = source.u16(at + 10)
            val crc = source.u32(at + 16)
            val compressedSize = source.u32(at + 20).toIntChecked()
            val size = source.u32(at + 24).toIntChecked()
            val nameLength = source.u16(at + 28)
            val extraLength = source.u16(at + 30)
            val commentLength = source.u16(at + 32)
            val localOffset = source.u32(at + 42).toIntChecked()
            val name = source.slice(at + 46, nameLength).decodeToString()
            at += 46 + nameLength + extraLength + commentLength

            if (name.endsWith("/") || name.substringBefore('/') in IGNORED_TOP_FOLDERS) return@repeat
            require((flags and FLAG_ENCRYPTED) == 0) { "\"$name\" is encrypted" }
            val path = safePath(name)
            require(source.u32(localOffset) == LOCAL_HEADER.toLong()) { "Damaged zip: no local header for \"$name\"" }
            val dataStart = localOffset + 30 + source.u16(localOffset + 26) + source.u16(localOffset + 28)
            val stored = source.slice(dataStart, compressedSize)
            val data =
                when (method) {
                    METHOD_STORED -> stored

                    METHOD_DEFLATED -> inflate(stored)

                    else -> throw IllegalArgumentException(
                        "\"$name\" uses compression method $method; only stored and deflated are supported",
                    )
                }
            require(data.size == size) { "\"$name\" inflated to ${data.size} bytes; the zip says $size" }
            require((Crc32.of(data).toLong() and 0xFFFFFFFFL) == crc) { "\"$name\" fails its CRC check" }
            files += path to data
        }

        val strip = isZippedProjectFolder(files.map { it.first })
        val result = LinkedHashMap<String, ByteArray>()
        for ((path, data) in files.sortedBy { it.first }) {
            val key = if (strip) path.substringAfter('/') else path
            require(result.put(key, data) == null) { "The zip holds \"$key\" twice" }
        }
        return result
    }

    // Whether every path sits in one shared top folder that is a project root or holds UFOs.
    private fun isZippedProjectFolder(paths: List<String>): Boolean {
        val top = paths.map { it.substringBefore('/', missingDelimiterValue = "") }.toSet().singleOrNull()
        if (top.isNullOrEmpty() || top.endsWith(UFO_SUFFIX, ignoreCase = true)) return false
        return paths.any { path ->
            val inside = path.substringAfter('/')
            inside == PROJECT_RECORD || inside.substringBefore('/', missingDelimiterValue = "").endsWith(UFO_SUFFIX, ignoreCase = true)
        }
    }

    private fun safePath(name: String): String {
        require(ProjectPath.isValid(name)) { "Refusing unsafe zip entry \"${name.replace("\u0000", "\\0")}\"" }
        return name
    }

    private fun findEndOfCentralDirectory(source: ByteSource): Int {
        val lastPossible = source.size - 22
        require(lastPossible >= 0) { "Not a zip: too short" }
        val firstPossible = maxOf(0, lastPossible - 0xFFFF)
        for (at in lastPossible downTo firstPossible) {
            if (source.u32(at) == END_OF_CENTRAL_DIRECTORY.toLong() && at + 22 + source.u16(at + 20) <= source.size) return at
        }
        throw IllegalArgumentException("Not a zip: no end of central directory")
    }

    private fun Long.toIntChecked(): Int {
        require(this in 0..Int.MAX_VALUE) { "Zip offset or size out of range: $this" }
        return toInt()
    }

    private const val PROJECT_RECORD = "typewright.json"
    private const val UFO_SUFFIX = ".ufo"
    private const val LOCAL_HEADER = 0x04034b50
    private const val CENTRAL_HEADER = 0x02014b50
    private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
    private const val VERSION_NEEDED = 20
    private const val VERSION_MADE_BY_UNIX = (3 shl 8) or 20
    private const val FLAG_ENCRYPTED = 0x0001
    private const val FLAG_UTF8 = 0x0800
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATED = 8
    private const val DOS_TIME_1980 = 0
    private const val DOS_DATE_1980 = (0 shl 9) or (1 shl 5) or 1
    private const val FILE_ATTRIBUTES = 0x81A4 shl 16
    private const val DIRECTORY_ATTRIBUTES = (0x41ED shl 16) or 0x10
    private const val MAX_ENTRIES = 0xFFFF
}

/** A growable little-endian byte buffer. */
private class ByteSink {
    private var buffer = ByteArray(1024)
    var size: Int = 0
        private set

    fun u16(value: Int) {
        ensure(2)
        buffer[size++] = value.toByte()
        buffer[size++] = (value ushr 8).toByte()
    }

    fun u32(value: Int) {
        ensure(4)
        buffer[size++] = value.toByte()
        buffer[size++] = (value ushr 8).toByte()
        buffer[size++] = (value ushr 16).toByte()
        buffer[size++] = (value ushr 24).toByte()
    }

    fun bytes(value: ByteArray) {
        ensure(value.size)
        value.copyInto(buffer, size)
        size += value.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        require(size.toLong() + extra <= Int.MAX_VALUE - 8) { "A project zip must stay under 2 GiB" }
        if (size + extra <= buffer.size) return
        var capacity = buffer.size
        while (capacity < size + extra) capacity = if (capacity > Int.MAX_VALUE / 2) Int.MAX_VALUE - 8 else capacity * 2
        buffer = buffer.copyOf(capacity)
    }
}

/** Bounds-checked little-endian reads over a zip's bytes. */
private class ByteSource(
    private val bytes: ByteArray,
) {
    val size: Int get() = bytes.size

    fun u16(at: Int): Int {
        check(at, 2)
        return (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
    }

    fun u32(at: Int): Long {
        check(at, 4)
        return (u16(at).toLong()) or (u16(at + 2).toLong() shl 16)
    }

    fun slice(
        at: Int,
        length: Int,
    ): ByteArray {
        check(at, length)
        return bytes.copyOfRange(at, at + length)
    }

    private fun check(
        at: Int,
        length: Int,
    ) {
        require(at >= 0 && length >= 0 && at.toLong() + length <= bytes.size) { "Damaged zip: read past the end" }
    }
}
