package com.zibro.cameraxexample.util

import android.annotation.TargetApi
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.zibro.cameraxexample.R
import java.io.File
import java.io.FileOutputStream

object PathUtil {
    //갤러리용 폴더 생성하여 파일을 넣어주는 함수
    fun getOutputDirectory(activity : Activity) : File = with(activity){
        //갤러리용 폴더 생성
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it,getString(R.string.app_name)).apply { mkdir() } }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    //String Path 가져오기 위한 함수
    fun getPath(context: Context, uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return context.getExternalFilesDir(null)?.absolutePath.toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), id.toLong()
                )
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(
                    split[1]
                )
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            cursor = context.contentResolver.query(
                uri!!, projection, selection, selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    //외장StorageDocument 인지 체크
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    //DownloadsDocument 인지 체크
    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    //MediaDocument 인지 체크
    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    @TargetApi(Build.VERSION_CODES.Q)
    fun getOutputDirectoryAndWrite(
        resolver: ContentResolver,
        uri: Uri,
        write: (FileOutputStream) -> Unit
    ) {
        resolver.openFileDescriptor(uri, "w")?.use {
            write(FileOutputStream(it.fileDescriptor))
        }
    }
}