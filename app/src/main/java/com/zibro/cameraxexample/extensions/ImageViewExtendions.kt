package com.zibro.cameraxexample.extensions

import android.annotation.SuppressLint
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory

private val factory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()

@SuppressLint("CheckResult")
fun ImageView.loadCenterCrop(url:String, corner:Float = 0f){
    Glide.with(this)
        .load(url)
        .transition(DrawableTransitionOptions.withCrossFade(factory))
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .centerCrop()
        .apply{
            if(corner > 0 ) transform(CenterCrop(),RoundedCorners(corner.fromDpToPx()))
        }
        .into(this)
}
internal fun ImageView.clear() = Glide.with(context).clear(this)