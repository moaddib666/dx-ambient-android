package com.dx.ambient.data.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.dx.ambient.domain.model.LibraryMedia
import com.dx.ambient.domain.model.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Walks a Storage Access Framework tree (folder the user picked from internal storage, USB,
 * or the gallery) and lists the playable media beneath it (MVP feature 6).
 *
 * SAF is the compatibility anchor for projector hardware that may still run old Android
 * branches, so this is used in preference to MediaStore.
 */
class SafMediaIndexer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** Takes persistable read permission for [treeUri] so it survives reboots. */
    fun persistPermission(treeUri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun releasePermission(treeUri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /** Reads a display name for the tree itself (folder name shown to the user). */
    suspend fun readTreeName(treeUri: Uri): String = withContext(Dispatchers.IO) {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        queryDisplayName(docUri) ?: treeUri.lastPathSegment ?: "Folder"
    }

    /** Recursively indexes [treeUri], returning every video/audio/image found. */
    suspend fun index(treeUri: Uri): List<LibraryMedia> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LibraryMedia>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<String>().apply { add(rootDocId) }

        while (pending.isNotEmpty()) {
            // Respect cancellation on large/deep USB trees.
            currentCoroutineContext().ensureActive()
            val parentDocId = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol) ?: continue
                    val size = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(childDocId)
                        continue
                    }
                    val kind = mime.toMediaKind() ?: continue
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    results += LibraryMedia(
                        uri = childUri.toString(),
                        displayName = name,
                        mimeType = mime,
                        kind = kind,
                        sizeBytes = size,
                        durationMs = 0L,
                        sourceTreeUri = treeUri.toString(),
                    )
                }
            }
        }
        results
    }

    private fun queryDisplayName(docUri: Uri): String? =
        resolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun String.toMediaKind(): MediaKind? = when {
        startsWith("video/") -> MediaKind.VIDEO
        startsWith("audio/") -> MediaKind.AUDIO
        startsWith("image/") -> MediaKind.IMAGE
        else -> null
    }
}
