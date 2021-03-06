/*
 * Copyright (c) 2015 PocketHub
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
package com.github.pockethub.android.util

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import com.github.pockethub.android.R
import com.jakewharton.picasso.OkHttp3Downloader
import com.meisolsson.githubsdk.model.User
import com.squareup.picasso.Picasso
import com.squareup.picasso.Transformation
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Avatar utilities
 *
 * @constructor Create avatar helper
 */
@Singleton
class AvatarLoader @Inject constructor(context: Context) {

    companion object {
        private const val DISK_CACHE_SIZE = 50 * 1024 * 1024 // 50MB

        private const val TAG = "AvatarLoader"

        private const val CORNER_RADIUS_IN_DIP = 3f

        /**
         * The max size of avatar images, used to rescale images to save memory.
         */
        private var avatarSize = 0
    }

    private val p: Picasso

    private val cornerRadius: Float

    private val transformation = RoundedCornersTransformation()

    init {
        // Install an HTTP cache in the application cache directory.
        val cacheDir = File(context.cacheDir, "http")
        val cache = Cache(cacheDir, DISK_CACHE_SIZE.toLong())

        val client = OkHttpClient.Builder()
                .cache(cache)
                .build()

        p = Picasso.Builder(context).downloader(OkHttp3Downloader(client)).build()

        val density = context.resources.displayMetrics.density
        cornerRadius = CORNER_RADIUS_IN_DIP * density

        if (avatarSize == 0) {
            avatarSize = getMaxAvatarSize(context)
        }

        // TODO remove this eventually
        // Delete the old cache
        val avatarDir = File(context.cacheDir, "avatars/github.com")
        if (avatarDir.isDirectory) {
            deleteCache(avatarDir)
        }
    }

    /**
     * Bind view to image at URL
     *
     * @param view The ImageView that is to display the user's avatar.
     * @param user A User object that points to the desired user.
     */
    fun bind(view: ImageView, user: User?) {
        bind(view, getAvatarUrl(user))
    }

    private fun bind(view: ImageView, url: String?) {
        var url = url
        if (url == null) {
            p.load(R.drawable.spinner_inner).resize(avatarSize, avatarSize).into(view)
            return
        }

        if (url.contains("?") && !url.contains("gravatar")) {
            url = url.substring(0, url.indexOf("?"))
        }

        p.load(url)
                .placeholder(R.drawable.gravatar_icon)
                .resize(avatarSize, avatarSize)
                .transform(transformation)
                .into(view)
    }

    private fun getAvatarUrl(user: User?): String? {
        if (user == null) {
            return null
        }

        val avatarUrl = user.avatarUrl()
        val email = user.email()
        return if (avatarUrl != null && avatarUrl.isNotBlank()) {
            HttpUrl.parse(avatarUrl)
                ?.newBuilder()
                ?.addQueryParameter("size", avatarSize.toString())
                ?.build()
                ?.toString()
        } else if (email != null && email.isNotBlank()){
            getAvatarUrl(GravatarUtils.getHash(user.email()))
        } else {
            // This redirects to avatar URL
            "http://github.com/${user.login()}.png?size=$avatarSize"
        }
    }

    private fun getAvatarUrl(id: String?): String? {
        return if (!id.isNullOrEmpty()) "http://gravatar.com/avatar/$id?d=404" else null
    }

    private fun getMaxAvatarSize(context: Context): Int {
        val attrs = intArrayOf(android.R.attr.layout_height)
        val array = context.theme.obtainStyledAttributes(R.style.AvatarXLarge, attrs)
        // Passing default value of 100px, but it shouldn't resolve to default anyway.
        val size = array.getLayoutDimension(0, 100)
        array.recycle()
        return size
    }

    private fun deleteCache(cache: File): Boolean {
        if (cache.isDirectory) {
            for (f in cache.listFiles()) {
                deleteCache(f)
            }
        }
        return cache.delete()
    }

    inner class RoundedCornersTransformation : Transformation {

        override fun transform(source: Bitmap): Bitmap {
            return ImageUtils.roundCorners(source, cornerRadius)
        }

        override fun key(): String {
            return "RoundedCornersTransformation"
        }
    }
}
