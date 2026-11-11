package com.nextdrive.pleos.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/** App content only. Retains the OS window/insets and caps B's reading width on wide car displays. */
class CaritdaPage(context:Context,private val content:View):FrameLayout(context){
    init{fitsSystemWindows=true;addView(content,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,Gravity.CENTER_HORIZONTAL))}
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        val width=(MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight).coerceAtLeast(1)
        val target=minOf(width,(1160*resources.displayMetrics.density).toInt())
        if(content.layoutParams.width!=target)content.layoutParams=(content.layoutParams as LayoutParams).apply{this.width=target}
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}
