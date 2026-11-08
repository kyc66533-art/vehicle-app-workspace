package com.nextdrive.pleos.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.nextdrive.pleos.analytics.*
import kotlin.math.max

/** Real epoch spacing, no line across session/error/gap boundaries. Text alternative is mandatory. */
class VehicleChart(c:Context,private val analysis:VehicleAnalysis,private val colors:CaritdaPalette,private val threshold:Int?,private val time:(Long)->String):View(c){
    init{minimumHeight=(260*resources.displayMetrics.density).toInt();contentDescription="수신 시각별 변화 그래프. ${analysis.textSummary}. 세션·수신 오류·공백은 선을 잇지 않아요.";importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;tag="analysis_chart"}
    override fun onMeasure(w:Int,h:Int){setMeasuredDimension(MeasureSpec.getSize(w),resolveSize(minimumHeight,h))}
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);val ps=analysis.points;if(ps.isEmpty())return
        val d=resources.displayMetrics.density;val left=62*d;val right=width-20*d;val top=26*d;val bottom=height-56*d
        if(right<=left||bottom<=top)return
        val minAt=ps.minOf{it.at};val maxAt=ps.maxOf{it.at};val lo=if(analysis.kind=="EV_SOC")0.0 else ps.minOf{it.number};val hi=if(analysis.kind=="EV_SOC")100.0 else max(lo+1,ps.maxOf{it.number})
        fun x(at:Long)=left+((at-minAt).toDouble()/max(1L,maxAt-minAt)*(right-left)).toFloat()
        fun y(v:Double)=bottom-((v-lo)/(hi-lo)*(bottom-top)).toFloat()
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=colors.secondary;strokeWidth=2*d;textSize=16*resources.displayMetrics.scaledDensity}
        canvas.drawLine(left,top,left,bottom,p);canvas.drawLine(left,bottom,right,bottom,p)
        canvas.drawText(VehicleAnalysis.fmt(hi),4*d,top+12*d,p);canvas.drawText(VehicleAnalysis.fmt(lo),4*d,bottom,p)
        if(threshold!=null&&analysis.kind=="EV_SOC"){p.color=colors.secondary;p.pathEffect=DashPathEffect(floatArrayOf(8*d,6*d),0f);canvas.drawLine(left,y(threshold.toDouble()),right,y(threshold.toDouble()),p);p.pathEffect=null}
        p.color=colors.accent;p.strokeWidth=3*d
        ps.forEachIndexed{i,v->if(i>0&&ps[i-1].segment==v.segment)canvas.drawLine(x(ps[i-1].at),y(ps[i-1].number),x(v.at),y(v.number),p);canvas.drawCircle(x(v.at),y(v.number),4*d,p)}
        analysis.events.forEach{e->ps.find{it.ref.storeId==e.store&&it.ref.rowId==e.row}?.let{v->p.color=colors.text;p.style=Paint.Style.STROKE;canvas.drawCircle(x(v.at),y(v.number),8*d,p);p.style=Paint.Style.FILL}}
        p.color=colors.secondary;p.textSize=14*resources.displayMetrics.scaledDensity
        canvas.drawText(time(minAt).substringBefore(" KST").substringBefore(" America"),left,bottom+26*d,p)
        if(width/d>600){p.textAlign=Paint.Align.RIGHT;canvas.drawText(time(maxAt).substringBefore(" KST").substringBefore(" America"),right,bottom+48*d,p)}
    }
}
