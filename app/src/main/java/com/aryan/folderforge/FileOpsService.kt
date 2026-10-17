package com.aryan.folderforge

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

class FileOpsService : IFileOps.Stub() {

    private val allowedRoots = listOf(
        "/storage/emulated/0/Android/data/com.dts.freefireth/files/contentcache/optional/android",
        "/storage/emulated/0/Android/data/com.dts.freefiremax/files/contentcache/optional/android"
    )

    private fun checkedDestination(path: String): File? {
        return try {
            val file = File(path).canonicalFile
            if (allowedRoots.any { file.path == it }) file else null
        } catch (_: Exception) {
            null
        }
    }

    override fun applyZip(zipPath: String, destinationPath: String): String {
        val zip = try {
            File(zipPath).canonicalFile
        } catch (_: Exception) {
            return "Invalid ZIP path."
        }

        val destination = checkedDestination(destinationPath)
            ?: return "Destination path is not allowed."

        if (!zip.isFile || !zip.canRead()) return "ZIP is missing or unreadable."
        if (!destination.isDirectory || !destination.canWrite()) {
            return "Game destination is missing or not writable."
        }

        val stamp = System.currentTimeMillis()
        val stage = File(destination, ".aryanforge-stage-$stamp")
        val backupRoot = File(destination, ".aryanforge-backup-$stamp")
        val backups = linkedMapOf<File, File>()
        val created = mutableListOf<File>()
        val installedFiles = mutableListOf<File>()

        fun isInside(file: File, root: File): Boolean {
            val child = file.canonicalFile.toPath()
            val parent = root.canonicalFile.toPath()
            return child != parent && child.startsWith(parent)
        }

        fun ensureSafeTarget(file: File) {
            if (!isInside(file, destination)) {
                throw IOException("Unsafe destination path.")
            }
        }

        fun backupExisting(target: File) {
            if (!target.exists() || backups.containsKey(target)) return

            var relative = destination.toPath().relativize(target.toPath()).toString()
            val backup = File(backupRoot, relative)
            if (!backup.parentFile!!.exists() && !backup.parentFile!!.mkdirs()) {
                throw IOException("Cannot create backup directory.")
            }
            if (!backupRoot.exists() && !backupRoot.mkdir()) {
                throw IOException("Cannot create backup folder.")
            }
            if (!target.renameTo(backup)) {
                throw IOException("Cannot back up '${target.name}'.")
            }
            backups[target] = backup
        }

        try {
            if (!stage.mkdir()) throw IOException("Cannot create staging folder.")

            ZipInputStream(FileInputStream(zip).buffered()).use { input ->
                val buffer = ByteArray(64 * 1024)

                while (true) {
                    val entry = input.nextEntry ?: break
                    val raw = entry.name.replace('\\', '/')
                    val parts = raw.split('/').filter { it.isNotEmpty() }

                    if (raw.startsWith('/') || raw.contains('\u0000') ||
                        parts.any { it == "." || it == ".." || it.contains(':') }) {
                        throw IOException("Unsafe ZIP path rejected: $raw")
                    }

                    if (parts.isNotEmpty()) {
                        var target = stage

                        for (part in parts.dropLast(1)) {
                            target = File(target, part)
                            if (!isInside(target, stage)) {
                                throw IOException("Unsafe extraction path.")
                            }
                            if (target.exists() && !target.isDirectory) {
                                throw IOException("File/directory conflict in ZIP.")
                            }
                            if (!target.exists() && !target.mkdir()) {
                                throw IOException("Cannot create staging directory.")
                            }
                        }

                        val output = File(target, parts.last())
                        if (!isInside(output, stage)) {
                            throw IOException("Unsafe extraction path.")
                        }

                        if (entry.isDirectory) {
                            if (output.exists() && !output.isDirectory) {
                                throw IOException("File/directory conflict in ZIP.")
                            }
                            if (!output.exists() && !output.mkdir()) {
                                throw IOException("Cannot create staging directory.")
                            }
                        } else {
                            if (output.exists()) throw IOException("Duplicate ZIP entry.")
                            output.outputStream().buffered().use { out ->
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count > 0) out.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                    input.closeEntry()
                }
            }

            val items = stage.listFiles()?.toList() ?: emptyList()
            if (items.isEmpty()) throw IOException("ZIP contains no items.")

            if (!backupRoot.mkdir()) {
                throw IOException("Cannot create backup folder.")
            }

            fun mergeDirectory(source: File, target: File) {
                if (!target.exists()) {
                    if (!target.mkdirs()) {
                        throw IOException("Cannot create '${target.name}'.")
                    }
                    created.add(target)
                } else if (!target.isDirectory) {
                    backupExisting(target)
                    if (!target.mkdirs()) {
                        throw IOException("Cannot replace file with directory '${target.name}'.")
                    }
                    created.add(target)
                }

                val children = source.listFiles()
                    ?: throw IOException("Cannot read staged directory '${source.name}'.")

                for (child in children) {
                    val destChild = File(target, child.name)
                    ensureSafeTarget(destChild)

                    if (child.isDirectory) {
                        if (destChild.exists() && !destChild.isDirectory) {
                            backupExisting(destChild)
                            if (!destChild.mkdirs()) {
                                throw IOException("Cannot replace '${destChild.name}' with a directory.")
                            }
                            created.add(destChild)
                        } else {
                            mergeDirectory(child, destChild)
                        }
                    } else {
                        if (destChild.exists()) {
                            if (destChild.isDirectory) {
                                backupExisting(destChild)
                            } else {
                                backupExisting(destChild)
                            }
                        } else {
                            created.add(destChild)
                        }

                        if (!destChild.parentFile!!.exists() &&
                            !destChild.parentFile!!.mkdirs()) {
                            throw IOException("Cannot create parent directory.")
                        }

                        Files.copy(
                            child.toPath(),
                            destChild.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        installedFiles.add(destChild)
                    }
                }
            }

            for (item in items) {
                val target = File(destination, item.name)
                ensureSafeTarget(target)

                if (item.isDirectory) {
                    mergeDirectory(item, target)
                } else {
                    if (target.exists()) backupExisting(target)
                    Files.copy(
                        item.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    installedFiles.add(target)
                }
            }

            if (backupRoot.exists() && !backupRoot.deleteRecursively()) {
                return "Applied successfully; some backup files could not be deleted."
            }

            return "Applied successfully. Matching files overwritten; other files preserved."
        } catch (e: Exception) {
            // Remove newly installed files first, then restore original files.
            for (file in installedFiles.asReversed()) {
                try { if (file.isFile) file.delete() } catch (_: Exception) {}
            }

            for ((original, backup) in backups.entries.toList().asReversed()) {
                try {
                    if (backup.exists()) {
                        if (original.exists()) {
                            if (original.isDirectory) original.deleteRecursively()
                            else original.delete()
                        }
                        original.parentFile?.mkdirs()
                        backup.renameTo(original)
                    }
                } catch (_: Exception) {}
            }

            for (file in created.asReversed()) {
                try {
                    if (file.exists() && file.isDirectory &&
                        file.listFiles()?.isEmpty() == true) file.delete()
                } catch (_: Exception) {}
            }

            return "Apply failed: ${e.message ?: "unknown error"}"
        } finally {
            try { stage.deleteRecursively() } catch (_: Exception) {}
            try {
                if (backupRoot.exists() && backupRoot.listFiles()?.isEmpty() == true) {
                    backupRoot.delete()
                }
            } catch (_: Exception) {}
        }
    }

    override fun installRemovePack(zipPath: String, destinationPath: String): String {
        val zip = try { File(zipPath).canonicalFile } catch (_: Exception) {
            return "Invalid ZIP path."
        }
        val destination = checkedDestination(destinationPath)
            ?: return "Destination path is not allowed."
        if (!zip.isFile || !zip.canRead()) return "Remove pack ZIP is missing or unreadable."
        if (!destination.isDirectory || !destination.canWrite()) return "Game destination is missing or not writable."

        val stamp = System.currentTimeMillis()
        val stage = File(destination, ".aryanforge-rm-stage-$stamp")
        val backup = File(destination, ".aryanforge-rm-backup-$stamp")
        val names = listOf("gameassetbundles", "fileinfo")
        val moved = linkedMapOf<File, File>()
        val installed = mutableListOf<File>()
        fun inside(f: File, root: File): Boolean =
            f.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) &&
            f.canonicalFile != root.canonicalFile

        try {
            if (!stage.mkdir()) throw IOException("Cannot create staging folder.")
            ZipInputStream(FileInputStream(zip).buffered()).use { input ->
                val buf = ByteArray(65536)
                val seen = mutableSetOf<String>()
                while (true) {
                    val e = input.nextEntry ?: break
                    val raw = e.name.replace('\\', '/')
                    val parts = raw.split('/').filter { it.isNotEmpty() }
                    if (raw.startsWith('/') || raw.contains('\u0000') ||
                        parts.any { it == "." || it == ".." || it.contains(':') })
                        throw IOException("Unsafe ZIP path: $raw")
                    if (parts.isEmpty()) continue
                    if (parts[0] !in names) throw IOException("Unexpected ZIP item '${parts[0]}'.")
                    var parent = stage
                    for (part in parts.dropLast(1)) {
                        parent = File(parent, part)
                        if (!inside(parent, stage)) throw IOException("Unsafe extraction path.")
                        if (parent.exists() && !parent.isDirectory) throw IOException("ZIP file/directory conflict.")
                        if (!parent.exists() && !parent.mkdir()) throw IOException("Cannot create staging directory.")
                    }
                    val outFile = File(parent, parts.last())
                    if (!inside(outFile, stage)) throw IOException("Unsafe extraction path.")
                    val rel = stage.toPath().relativize(outFile.toPath()).toString()
                    if (!seen.add(rel)) throw IOException("Duplicate ZIP entry: $rel")
                    if (e.isDirectory) {
                        if (outFile.exists() && !outFile.isDirectory) throw IOException("ZIP file/directory conflict.")
                        if (!outFile.exists() && !outFile.mkdir()) throw IOException("Cannot create staging directory.")
                    } else {
                        if (outFile.exists()) throw IOException("ZIP file/directory conflict.")
                        outFile.outputStream().buffered().use { out ->
                            while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                        }
                    }
                    input.closeEntry()
                }
            }
            if (!File(stage, "gameassetbundles").isDirectory)
                throw IOException("ZIP must contain a gameassetbundles folder.")
            if (!File(stage, "fileinfo").exists()) throw IOException("ZIP must contain fileinfo.")
            if (!backup.mkdir()) throw IOException("Cannot create temporary replacement folder.")

            for (name in names) {
                val old = File(destination, name)
                if (old.canonicalFile.parentFile != destination.canonicalFile || old.canonicalFile.name != name)
                    throw IOException("Unsafe replacement target.")
                if (old.exists()) {
                    val save = File(backup, name)
                    if (!old.renameTo(save)) throw IOException("Cannot stage existing '$name'.")
                    moved[old] = save
                }
            }
            for (name in names) {
                val from = File(stage, name)
                val to = File(destination, name)
                if (!from.renameTo(to)) throw IOException("Cannot install '$name'.")
                installed.add(to)
            }
            stage.deleteRecursively()
            backup.deleteRecursively()
            return "Remove pack installed: gameassetbundles fully replaced and fileinfo overwritten."
        } catch (e: Exception) {
            for (f in installed.asReversed()) try { f.deleteRecursively() } catch (_: Exception) {}
            for ((old, saved) in moved.entries.toList().asReversed())
                try { if (saved.exists()) saved.renameTo(old) } catch (_: Exception) {}
            try { stage.deleteRecursively() } catch (_: Exception) {}
            try { backup.deleteRecursively() } catch (_: Exception) {}
            return "Remove pack failed: ${e.message ?: "unknown error"}"
        }
    }

    override fun removeGameAssetBundles(destinationPath: String): String {
        val destination = checkedDestination(destinationPath)
            ?: return "Destination path is not allowed."

        return try {
            val canonicalDestination = destination.canonicalFile
            val names = listOf("gameassetbundles", "fileinfo")
            val removed = mutableListOf<String>()
            val missing = mutableListOf<String>()
            val failed = mutableListOf<String>()

            for (name in names) {
                val target = File(destination, name)
                val canonicalTarget = target.canonicalFile

                if (canonicalTarget.parentFile != canonicalDestination ||
                    canonicalTarget.name != name) {
                    return "Unsafe delete target rejected."
                }

                if (!target.exists()) {
                    missing.add(name)
                    continue
                }

                if (target.deleteRecursively()) {
                    removed.add(name)
                } else {
                    failed.add(name)
                }
            }

            buildString {
                if (removed.isNotEmpty()) append("Deleted: ${removed.joinToString(", ")}. ")
                if (missing.isNotEmpty()) append("Not found: ${missing.joinToString(", ")}. ")
                if (failed.isNotEmpty()) append("Failed to delete: ${failed.joinToString(", ")}.")
                if (removed.isEmpty() && failed.isEmpty()) {
                    append("Neither gameassetbundles nor fileinfo was found.")
                }
            }.trim()
        } catch (e: Exception) {
            "Remove failed: ${e.message ?: "unknown error"}"
        }
    }
    override fun destroy() {
        System.exit(0)
    }
}
