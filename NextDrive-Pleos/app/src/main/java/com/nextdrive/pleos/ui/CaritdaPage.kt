package com.nextdrive.pleos.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/** App content only. Retains the OS window/insets and caps B's reading width on wide car displays. */
class CaritdaPage(context:Context,private val content:View):FrameLayout(context){
    /** One fixed wordmark of the app name (not a third-party logo). It lives outside the scrolling body, so it stays put on every screen. */
    private val mark=TextView(context).apply{
        tag="brand_mark";text="CARITDA";setTextSize(TypedValue.COMPLEX_UNIT_SP,22f);typeface=Typeface.DEFAULT_BOLD;letterSpacing=0.1f
        setTextColor(Color.argb(200,95,108,122));isClickable=false;isFocusable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    init{
        fitsSystemWindows=true
        addView(content,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT,Gravity.CENTER_HORIZONTAL))
        val d=resources.displayMetrics.density
        addView(mark,LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT,Gravity.BOTTOM or Gravity.START).apply{setMargins((16*d).toInt(),0,0,(12*d).toInt())})
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        val width=(MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight).coerceAtLeast(1)
        val target=minOf(width,(1160*resources.displayMetrics.density).toInt())
        if(content.layoutParams.width!=target)content.layoutParams=(content.layoutParams as LayoutParams).apply{this.width=target}
        // Show the mark only in the empty side margin; on narrow windows it would sit on top of the content, so it is hidden there.
        mark.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
        val gutter=(width-target)/2
        val backgroundColor=(content.background as? ColorDrawable)?.color?:Color.WHITE
        mark.setTextColor(CaritdaPalette.of(Color.luminance(backgroundColor)<0.5).secondary)
        val wanted=if(gutter>=mark.measuredWidth+(32*resources.displayMetrics.density).toInt())View.VISIBLE else View.INVISIBLE
        if(mark.visibility!=wanted)mark.visibility=wanted
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}
