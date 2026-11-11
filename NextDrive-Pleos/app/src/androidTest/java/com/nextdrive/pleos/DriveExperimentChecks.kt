package com.nextdrive.pleos

import android.app.Instrumentation
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.pm.PackageManager
import android.hardware.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Experimental only: no risk score, diagnosis, production menu or background collection. */
class DriveExperimentChecks(private val inst:Instrumentation){
    data class Point(val at:Long,val speed:Double,val session:String)
    data class Change(val at:Long,val acceleration:Double)
    companion object{
        fun changes(points:List<Point>):List<Change> = points.zipWithNext().mapNotNull{(a,b)->
            val dt=(b.at-a.at)/1000.0
            if(a.session!=b.session||dt !in 0.1..5.0||!a.speed.isFinite()||!b.speed.isFinite()||a.speed<0||b.speed<0)null else Change(b.at,(b.speed-a.speed)/dt)
        }
    }
    private val c=inst.targetContext
    private fun main(block:()->Unit){inst.runOnMainSync(block)}
    fun run():List<String>{
        ValidationEnvironment.requireIsolated(c);val lines=mutableListOf<String>()
        fun verify(v:Boolean,t:String){check(v){t};lines.add("PASS $t")}
        val p=listOf(Point(1000,0.0,"a"),Point(2000,2.0,"a"),Point(3000,0.0,"a"))
        verify(changes(p).map{it.acceleration}==listOf(2.0,-2.0),"DX01 acceleration/deceleration computed in m/s² from timestamps")
        verify(changes(listOf(p[0],Point(9000,4.0,"a"))).isEmpty(),"DX02 sampling gaps are not joined")
        verify(changes(listOf(p[1],p[0])).isEmpty(),"DX03 reversed timestamps rejected")
        verify(changes(listOf(p[0],p[1].copy(session="b"))).isEmpty(),"DX04 sessions not joined")
        verify(changes(listOf(p[0],p[1].copy(speed=Double.NaN))).isEmpty(),"DX05 invalid speed not zero-filled")
        val latch=CountDownLatch(1);var car:Car?=null
        main{try{car=Car.createCar(c,android.os.Handler(android.os.Looper.getMainLooper()),Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT){connected,ready->
            if(ready)try{val manager=connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                for((property,label)in listOf(VehiclePropertyIds.PERF_VEHICLE_SPEED to "SPEED",VehiclePropertyIds.GEAR_SELECTION to "GEAR")){
                    val permission=if(label=="SPEED")"android.car.permission.CAR_SPEED"else"android.car.permission.CAR_POWERTRAIN"
                    if(c.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){lines.add("RUNTIME $label PERMISSION_DENIED; capability and receipt remain unverified");continue}
                    try{val config=manager.getCarPropertyConfig(property)
                        if(config==null)lines.add("RUNTIME $label UNSUPPORTED")
                        else{val value=manager.getProperty<Any>(property,0);lines.add("RUNTIME $label status="+value.status+" value="+value.value+" vehicle_timestamp_ns="+value.timestamp+" config_hz="+config.minSampleRate+"~"+config.maxSampleRate+"; one read is not continuous-data validation")}
                    }catch(_:SecurityException){lines.add("RUNTIME $label PERMISSION_DENIED")}catch(_:Exception){lines.add("RUNTIME $label READ_FAILED")}
                }
            }finally{latch.countDown()}
        }}catch(_:Exception){lines.add("RUNTIME CAR_SERVICE_UNAVAILABLE");latch.countDown()}}
        if(!latch.await(8,TimeUnit.SECONDS))lines.add("RUNTIME CAR_SERVICE_TIMEOUT")
        main{car?.disconnect()}
        val sensors=c.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if(sensor==null)lines.add("RUNTIME RAW_VIBRATION_SENSOR_UNAVAILABLE")else{
            val samples=java.util.Collections.synchronizedList(mutableListOf<Double>());val sensorLatch=CountDownLatch(1)
            val listener=object:SensorEventListener{
                override fun onAccuracyChanged(s:Sensor?,accuracy:Int){}
                override fun onSensorChanged(e:SensorEvent){if(e.values.size>=3){val norm=kotlin.math.sqrt(e.values.take(3).sumOf{it.toDouble()*it.toDouble()});if(norm.isFinite())samples.add(norm);if(samples.size>=20)sensorLatch.countDown()}}
            }
            val registered=mainRegister(sensors,listener,sensor);sensorLatch.await(3,TimeUnit.SECONDS);main{sensors.unregisterListener(listener)}
            lines.add("RUNTIME RAW_ACCELEROMETER registered=$registered samples="+samples.size+" includes_gravity=true; no fault labels/no diagnosis")
        }
        lines.add("RUNTIME RAW_AUDIO_NOT_CAPTURED; explicit microphone consent and independent labelled dataset required")
        return lines
    }
    private fun mainRegister(m:SensorManager,l:SensorEventListener,s:Sensor):Boolean{var ok=false;main{ok=m.registerListener(l,s,SensorManager.SENSOR_DELAY_GAME)};return ok}
}
