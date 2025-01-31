/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.element.android.features.messages.impl.media.local

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import com.squareup.anvil.annotations.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.di.AppScope
import io.element.android.libraries.di.ApplicationContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class AndroidLocalMediaActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val buildMeta: BuildMeta,
) : LocalMediaActions {

    private var activityContext: Context? = null

    @Composable
    override fun Configure() {
        val context = LocalContext.current
        return DisposableEffect(Unit) {
            activityContext = context
            onDispose {
                activityContext = null
            }
        }
    }

    override suspend fun saveOnDisk(localMedia: LocalMedia): Result<Unit> = withContext(coroutineDispatchers.io) {
        require(localMedia.uri.scheme == ContentResolver.SCHEME_FILE)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveOnDiskUsingMediaStore(localMedia)
            } else {
                saveOnDiskUsingExternalStorageApi(localMedia)
            }
        }.onSuccess {
            Timber.v("Save on disk succeed")
        }.onFailure {
            Timber.e(it, "Save on disk failed")
        }
    }

    override suspend fun share(localMedia: LocalMedia): Result<Unit> = withContext(coroutineDispatchers.io) {
        require(localMedia.uri.scheme == ContentResolver.SCHEME_FILE)
        runCatching {
            val shareableUri = localMedia.toShareableUri()
            val shareMediaIntent = Intent(Intent.ACTION_SEND)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(Intent.EXTRA_STREAM, shareableUri)
                .setTypeAndNormalize(localMedia.info.mimeType)
            withContext(coroutineDispatchers.main) {
                val intent = Intent.createChooser(shareMediaIntent, null)
                activityContext!!.startActivity(intent)
            }
        }.onSuccess {
            Timber.v("Share media succeed")
        }.onFailure {
            Timber.e(it, "Share media failed")
        }
    }

    override suspend fun open(localMedia: LocalMedia): Result<Unit> = withContext(coroutineDispatchers.io) {
        require(localMedia.uri.scheme == ContentResolver.SCHEME_FILE)
        runCatching {
            val openMediaIntent = Intent(Intent.ACTION_VIEW)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setDataAndType(localMedia.toShareableUri(), localMedia.info.mimeType)
            withContext(coroutineDispatchers.main) {
                activityContext!!.startActivity(openMediaIntent)
            }
        }.onSuccess {
            Timber.v("Open media succeed")
        }.onFailure {
            Timber.e(it, "Open media failed")
        }
    }

    private fun LocalMedia.toShareableUri(): Uri {
        val mediaAsFile = this.toFile()
        val authority = "${buildMeta.applicationId}.fileprovider"
        return FileProvider.getUriForFile(context, authority, mediaAsFile).normalizeScheme()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveOnDiskUsingMediaStore(localMedia: LocalMedia) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, localMedia.info.name)
            put(MediaStore.MediaColumns.MIME_TYPE, localMedia.info.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val outputUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (outputUri != null) {
            localMedia.openStream()?.use { input ->
                resolver.openOutputStream(outputUri).use { output ->
                    input.copyTo(output!!, DEFAULT_BUFFER_SIZE)
                }
            }
        }
    }

    private fun saveOnDiskUsingExternalStorageApi(localMedia: LocalMedia) {
        val target = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            localMedia.info.name
        )
        localMedia.openStream()?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun LocalMedia.openStream(): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    /**
     * Tries to extract a file from the uri.
     */
    private fun LocalMedia.toFile(): File {
        return uri.toFile()
    }
}
