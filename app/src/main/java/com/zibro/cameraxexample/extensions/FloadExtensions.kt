package com.zibro.cameraxexample.extensions

import android.content.res.Resources

//dp를 Pixel로 변환하는 함수
internal fun Float.fromDpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density).toInt()
}