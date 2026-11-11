package com.nextdrive.pleos.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*

/** CARITDA's own palette and dimensions, not Hyundai/Pleos brand assets or certification. */
data class CaritdaPalette(val background:Int,val surface:Int,val inset:Int,val text:Int,val secondary:Int,
    val accent:Int,val onAccent:Int,val border:Int,val error:Int,val line:Int=border,
    val warning:Int=secondary,val warningSurface:Int=inset) {
    companion object {
        private fun c(s:String)=Color.parseColor(s)
        fun of(dark:Boolean)=if(dark)CaritdaPalette(c("#101D27"),c("#192C39"),c("#203F43"),c("#F1F6F9"),c("#BACBD6"),c("#9BE4D5"),c("#102C2B"),c("#8299A8"),c("#FFB4B4"),c("#506776"),c("#FFDA8C"),c("#44351E"))
        else CaritdaPalette(c("#F4F7F9"),c("#FFFFFF"),c("#E0EFF0"),c("#142D3D"),c("#4E6473"),c("#12596A"),c("#FFFFFF"),c("#718492"),c("#A72C39"),c("#C0CCD4"),c("#805000"),c("#FFF2D5"))
    }
}

object CaritdaTokens {
    const val TOUCH=76
    const val PAGE=32
    const val TINY=4
    const val SMALL=8
    const val MEDIUM=12
    const val GAP=16
    const val CARD_INSET=24
    const val ICON=28
    const val METRIC_ICON=28
    const val BORDER=2
    const val CARD_RADIUS=18
    const val BUTTON_RADIUS=12
    const val TITLE=32
    const val BODY=24
    const val SUPPORT=20
    const val SUBTITLE=24
    const val EMPHASIS=26
    const val METRIC=40
}
enum class ActionStyle { PRIMARY, SECONDARY, QUIET, DANGER }
enum class HmiIcon { HOME, RECORD, VEHICLE, PLUS, MENU, BACK, BATTERY, FUEL, DISTANCE, PERSON, CHEVRON, CHECK, CLOCK }
data class AttentionContent(val title:String,val preview:String,val explanation:String)

/** Original consistent 24-unit outline symbols. No OEM or third-party icon data. */
class CaritdaIcon(private val icon:HmiIcon,private val tint:Int):Drawable(){
    override fun draw(canvas:Canvas){
        val save=canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=tint;style=Paint.Style.STROKE;strokeWidth=1.8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        fun path(vararg xy:Float){val q=Path();q.moveTo(xy[0],xy[1]);for(i in 2 until xy.size step 2)q.lineTo(xy[i],xy[i+1]);canvas.drawPath(q,p)}
        when(icon){
            HmiIcon.HOME->{path(3f,11f,12f,3f,21f,11f);path(5f,10f,5f,21f,10f,21f,10f,15f,14f,15f,14f,21f,19f,21f,19f,10f)}
            HmiIcon.RECORD->{canvas.drawRoundRect(5f,3f,19f,21f,2f,2f,p);path(9f,8f,15f,8f);path(9f,12f,15f,12f);path(9f,16f,13f,16f)}
            HmiIcon.VEHICLE->{path(3f,16f,3f,10f,6f,5f,18f,5f,21f,10f,21f,16f,3f,16f);path(4f,10f,20f,10f);path(5f,16f,5f,20f);path(19f,16f,19f,20f);path(6f,13f,8f,13f);path(16f,13f,18f,13f)}
            HmiIcon.PLUS->{path(12f,5f,12f,19f);path(5f,12f,19f,12f)}
            HmiIcon.MENU->{path(5f,6f,19f,6f);path(5f,12f,19f,12f);path(5f,18f,19f,18f)}
            HmiIcon.BACK->path(14f,5f,7f,12f,14f,19f)
            HmiIcon.CHEVRON->path(9f,5f,16f,12f,9f,19f)
            HmiIcon.BATTERY->{canvas.drawRoundRect(3f,6f,20f,18f,2f,2f,p);path(22f,10f,22f,14f);path(7f,9f,7f,15f);path(11f,9f,11f,15f)}
            HmiIcon.FUEL->{canvas.drawRoundRect(4f,3f,14f,21f,1f,1f,p);canvas.drawRect(7f,6f,11f,10f,p);path(14f,12f,17f,12f,17f,18f,21f,18f,21f,8f,18f,5f);path(2f,21f,16f,21f)}
            HmiIcon.DISTANCE->{canvas.drawArc(3f,4f,21f,22f,180f,180f,false,p);path(5f,18f,19f,18f);path(12f,15f,16f,9f)}
            HmiIcon.PERSON->{canvas.drawCircle(12f,7f,4f,p);canvas.drawArc(4f,13f,20f,26f,180f,180f,false,p)}
            HmiIcon.CHECK->path(4f,12f,10f,18f,20f,6f)
            HmiIcon.CLOCK->{canvas.drawCircle(12f,12f,9f,p);path(12f,6f,12f,12f,16f,14f)}
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha:Int){}
    override fun setColorFilter(colorFilter:ColorFilter?){}
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

/** Native View components; callers own navigation, safety and every data mutation. */
class CaritdaComponents(private val context:Context,val colors:CaritdaPalette){
    fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
    fun column()=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;layoutParams=LinearLayout.LayoutParams(-1,-2)}
    fun shape(color:Int,radius:Int=CaritdaTokens.CARD_RADIUS,stroke:Int?=null)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();stroke?.let{setStroke(dp(CaritdaTokens.BORDER),it)}}
    fun text(value:String,size:Int=CaritdaTokens.BODY,bold:Boolean=false,secondary:Boolean=false)=TextView(context).apply{
        text=value;textSize=size.toFloat();setTextColor(if(secondary)colors.secondary else colors.text)
        typeface=Typeface.create(if(bold)"sans-serif-medium" else "sans-serif",Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(),1.2f);setPadding(0,dp(6),0,dp(6));layoutParams=LinearLayout.LayoutParams(-1,-2)
    }
    fun card()=column().apply{
        background=GradientDrawable().apply{setColor(this@CaritdaComponents.colors.surface);cornerRadius=dp(CaritdaTokens.CARD_RADIUS).toFloat();setStroke(dp(1),this@CaritdaComponents.colors.line)}
        setPadding(dp(CaritdaTokens.CARD_INSET),dp(CaritdaTokens.CARD_INSET),dp(CaritdaTokens.CARD_INSET),dp(CaritdaTokens.CARD_INSET))
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(CaritdaTokens.SMALL),0,dp(CaritdaTokens.SMALL))}
    }
    fun appHeader(actions:List<Button>)=LinearLayout(context).apply{
        orientation=LinearLayout.HORIZONTAL;tag="navigation_bar"
        actions.forEach{addView(it,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(4),0,dp(4),dp(4))})}
    }
    /** B navigation: one home action, two child actions. Short windows may scroll this header. */
    fun screenHeader(title:String,brand:Boolean,actions:List<Button>,available:Float):LinearLayout=column().apply{
        tag="navigation_bar";setPadding(0,dp(8),0,dp(16))
        val narrow=available<600||context.resources.configuration.fontScale>=1.5f
        val heading=column().apply{
            addView(text(title,CaritdaTokens.TITLE,true).apply{tag=if(brand)"app_brand"else"page_title"})
            if(brand)addView(text("차량 기록을 잇다",20,secondary=true))
        }
        if(narrow){
            if(brand)addView(heading)
            addView(appHeader(actions))
            if(!brand)addView(heading)
        }else{
            val row=LinearLayout(context).apply{gravity=android.view.Gravity.CENTER_VERTICAL}
            if(!brand&&actions.size==2)row.addView(actions.first(),LinearLayout.LayoutParams(-2,-2).apply{setMargins(0,0,dp(24),0)})
            row.addView(heading,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(actions.last(),LinearLayout.LayoutParams(-2,-2).apply{setMargins(dp(24),0,0,0)})
            addView(row)
        }
    }
    fun sectionTitle(value:String,icon:HmiIcon?=null)=text(value,24,true).apply{
        icon?.let{setCompoundDrawablesRelative(CaritdaIcon(it,colors.accent).apply{setBounds(0,0,dp(28),dp(28))},null,null,null);compoundDrawablePadding=dp(12)}
    }
    fun checkCard(title:String,reason:String,origin:String)=card().apply{
        background=shape(colors.warningSurface,CaritdaTokens.CARD_RADIUS,colors.warning)
        addView(text(origin,20,true).apply{setTextColor(colors.warning)})
        addView(text(title,24,true));addView(text(reason,22,secondary=true))
    }
    fun pageHeading(title:String,leading:Button?,available:Float)=LinearLayout(context).apply{
        gravity=android.view.Gravity.CENTER_VERTICAL
        val stacked=available<600 || context.resources.configuration.fontScale>=1.5f
        orientation=if(stacked)LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        layoutParams=LinearLayout.LayoutParams(-1,-2)
        leading?.let{button->
            button.maxWidth=dp((available-48).toInt().coerceAtLeast(76))
            addView(button,LinearLayout.LayoutParams(-2,-2).apply{setMargins(0,0,dp(16),0)})
        }
        val heading=text(title,CaritdaTokens.TITLE,true).apply{tag="page_title"}
        addView(heading,if(stacked)LinearLayout.LayoutParams(-1,-2)else LinearLayout.LayoutParams(0,-2,1f))
    }
    fun attention(content:AttentionContent)=card().apply{
        tag="attention_card";addView(text(content.title,CaritdaTokens.EMPHASIS,true).apply{tag="home_counts"})
        if(content.preview.isNotBlank())addView(text(content.preview,22))
        if(content.explanation.isNotBlank())addView(text(content.explanation,20,secondary=true))
    }
    fun issueRecord(category:String,statusText:String,body:String,metadata:String,unread:String,key:String,enabled:Boolean,open:()->Unit)=card().apply{
        tag="issue_record_card";addView(status("$category · $statusText"))
        addView(text(body,CaritdaTokens.SUBTITLE,true));addView(text(metadata,CaritdaTokens.SUPPORT,secondary=true))
        if(unread.isNotBlank())addView(text(unread,20,true))
        addView(action("내용 보기",key,ActionStyle.QUIET,enabled,HmiIcon.CHEVRON,open))
    }
    fun loading(value:String)=text(value,22,secondary=true).apply{tag="loading_state";accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
    fun action(value:String,key:String,style:ActionStyle=ActionStyle.SECONDARY,enabled:Boolean=true,icon:HmiIcon?=null,click:()->Unit)=Button(context).apply{
        text=value;tag=key;textSize=CaritdaTokens.BODY.toFloat();isAllCaps=false;maxLines=Int.MAX_VALUE;ellipsize=null;minHeight=dp(CaritdaTokens.TOUCH);minimumHeight=minHeight;minimumWidth=dp(CaritdaTokens.TOUCH)
        stateListAnimator=null;elevation=0f
        setPadding(dp(CaritdaTokens.CARD_INSET),dp(CaritdaTokens.GAP),dp(CaritdaTokens.CARD_INSET),dp(CaritdaTokens.GAP));setLineSpacing(dp(3).toFloat(),1.05f)
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        val foreground=when(style){ActionStyle.PRIMARY->colors.onAccent;ActionStyle.DANGER->colors.error;else->colors.text}
        val fill=when(style){ActionStyle.PRIMARY->colors.accent;ActionStyle.SECONDARY->colors.surface;else->colors.background}
        setTextColor(if(enabled)foreground else colors.secondary)
        background=StateListDrawable().apply{
            addState(intArrayOf(-android.R.attr.state_enabled),shape(colors.inset,CaritdaTokens.BUTTON_RADIUS,colors.border))
            addState(intArrayOf(android.R.attr.state_focused),shape(fill,CaritdaTokens.BUTTON_RADIUS,if(style==ActionStyle.PRIMARY)colors.onAccent else colors.accent))
            addState(intArrayOf(android.R.attr.state_selected),shape(fill,CaritdaTokens.BUTTON_RADIUS,colors.accent))
            addState(intArrayOf(android.R.attr.state_pressed),shape(fill,CaritdaTokens.BUTTON_RADIUS,foreground))
            addState(intArrayOf(),shape(fill,CaritdaTokens.BUTTON_RADIUS,when(style){ActionStyle.PRIMARY->colors.accent;ActionStyle.QUIET->null;else->colors.border}))
        }
        icon?.let{setCompoundDrawablesRelative(CaritdaIcon(it,if(enabled)foreground else colors.secondary).apply{setBounds(0,0,dp(CaritdaTokens.ICON),dp(CaritdaTokens.ICON))},null,null,null);compoundDrawablePadding=dp(CaritdaTokens.MEDIUM)}
        isEnabled=enabled;isFocusable=true
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(8),0,dp(8))}
        setOnClickListener{click()}
    }
    fun status(value:String)=text(value,CaritdaTokens.SUPPORT,true).apply{setTextColor(colors.secondary);tag="status_label"}
    fun state(title:String,body:String,error:Boolean=false)=card().apply{
        addView(text(title,24,true).apply{if(error)setTextColor(colors.error)})
        if(body.isNotBlank())addView(text(body,22,secondary=true))
        tag=if(error)"error_state"else"empty_state"
    }
    fun metric(name:String,value:String,time:String,state:String,key:String,icon:HmiIcon)=column().apply{
        tag="metric_$key"
        addView(text(name,22,true).apply{setCompoundDrawablesRelative(CaritdaIcon(icon,colors.secondary).apply{setBounds(0,0,dp(CaritdaTokens.METRIC_ICON),dp(CaritdaTokens.METRIC_ICON))},null,null,null);compoundDrawablePadding=dp(CaritdaTokens.MEDIUM)})
        addView(text(value,CaritdaTokens.METRIC,true).apply{tag="value_$key";setPadding(0,dp(12),0,dp(8))})
        addView(text(time,22,true).apply{tag="time_$key"})
        if(state.isNotBlank())addView(text(state,20,secondary=true).apply{tag="attempt_$key"})
    }
    fun history(title:String,subtitle:String,key:String,enabled:Boolean,click:()->Unit)=action("$title\n$subtitle",key,ActionStyle.QUIET,enabled,HmiIcon.CLOCK,click)
    fun expandable(title:String,key:String,expanded:Boolean,enabled:Boolean,onToggle:()->Unit)=action((if(expanded)"− " else "+ ")+title,key,ActionStyle.QUIET,enabled,HmiIcon.CHEVRON,onToggle)
    fun twoPane(primary:View,secondary:View,wide:Boolean)=column().apply{
        if(!wide){addView(primary);addView(secondary)}else{
            val row=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL}
            row.addView(primary,LinearLayout.LayoutParams(0,-2,1.4f).apply{setMargins(0,0,dp(24),0)})
            row.addView(secondary,LinearLayout.LayoutParams(0,-2,1f));addView(row)
        }
    }
    /** Buttons retain their labels and target height; no shrinking to fit a grid. */
    fun grid(children:List<View>,columns:Int):LinearLayout=column().apply{
        children.chunked(columns.coerceAtLeast(1)).forEach{group->
            val row=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;layoutParams=LinearLayout.LayoutParams(-1,-2)}
            group.forEachIndexed{i,v->row.addView(v,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(if(i==0)0 else dp(CaritdaTokens.SMALL),dp(CaritdaTokens.TINY),if(i==group.lastIndex)0 else dp(CaritdaTokens.SMALL),dp(CaritdaTokens.TINY))})}
            addView(row)
        }
    }
}
