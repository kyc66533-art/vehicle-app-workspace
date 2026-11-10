package com.nextdrive.pleos.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.ErrorCode
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.WheelValue
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Public typed SDK reads only. initialize() returning is not proof of a service connection. */
class PleosReadClient(private val context: Context, private val store: ObservationStore,
                      private val changed: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var generation = 0
    private var outstanding = mutableSetOf<String>()
    private var activeSession:String?=null
    var message = "SDK 2.3.2 준비됨 · 아직 연결을 요청하지 않았습니다."
        private set
    var busy = false
        private set
    private val source = if (Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))
        ObservationSource.PLEOS_EMULATOR_SDK else ObservationSource.PLEOS_VEHICLE_SDK

    fun readOnce() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (busy) return
        stop()
        val run = generation
        val session = ObservationStore.sessionId()
        activeSession=session
        outstanding = (setOf("EV_SOC", "ODOMETER", "FUEL_PERCENT")+VehicleHealth.labels.keys).toMutableSet()
        busy = true
        message = "서비스 연결 요청 중 · Capability 응답을 기다리고 있습니다."
        changed()
        fun dispatch(action: () -> Unit) { main.post { if (run == generation) action() } }
        fun finish(kind: String, status: ObservationStatus, raw: String?, unit: String?, supported: Boolean?, detail: String) {
            if (!outstanding.remove(kind)) return
            val at = if (status == ObservationStatus.RECEIVED) System.currentTimeMillis() else null
            try {
                store.append(VehicleObservation(kind, raw, unit, at, source, supported,
                    status == ObservationStatus.RECEIVED, session, status, detail))
                message = "SDK 응답 기록: $kind · $status"
            } catch (e: Exception) {
                message = "관측 저장 실패: ${e.javaClass.simpleName}. 기존 기록은 보존됩니다."
            }
            busy = outstanding.isNotEmpty()
            changed()
        }
        fun fail(kind: String, stage: String, error: Exception, supported: Boolean?) {
            // The shipped AAR obfuscates VehicleException; only public ErrorCode names are used.
            val code = ErrorCode.entries.firstOrNull { it.name == error.message }
            val status = when {
                error is SecurityException || code == ErrorCode.ERROR_SECURITY_PERMISSION -> ObservationStatus.PERMISSION_DENIED
                code == ErrorCode.ERROR_NOT_SUPPORTED_PROPERTY || code == ErrorCode.ERROR_NOT_SUPPORTED_AREA -> ObservationStatus.UNSUPPORTED
                code == ErrorCode.ERROR_EMPTY_RESULT -> ObservationStatus.INVALID
                else -> ObservationStatus.CONNECTION_FAILED
            }
            finish(kind, status, null, null, if (status == ObservationStatus.UNSUPPORTED) false else supported,
                "$stage · ${code?.name ?: error.javaClass.simpleName} · ${error.message.orEmpty().take(200)}")
        }
        fun request(kind: String, permission: String, unit: String,
                    capability: ((CapabilityConfig) -> Unit, (Exception) -> Unit) -> Unit,
                    read: ((Number?) -> Unit, (Exception) -> Unit) -> Unit) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                finish(kind, ObservationStatus.PERMISSION_DENIED, null, null, null, "선언 권한 미허용: $permission")
                return
            }
            val failed: (Exception) -> Unit = { e -> dispatch { fail(kind, "Capability", e, null) } }
            try {
                capability({ config -> dispatch {
                    if (kind !in outstanding) return@dispatch
                    val access = config.accessType
                    if (access != AccessType.READ && access != AccessType.READ_WRITE) {
                        finish(kind, ObservationStatus.UNSUPPORTED, null, null, false, "Capability access=$access · 읽기 미지원")
                    } else {
                        message = "서비스 Capability 응답 확인 · $kind access=$access · 읽는 중입니다."
                        changed()
                        try {
                            read({ value -> dispatch {
                                val number = value?.toDouble()
                                if (number == null || !number.isFinite() || number < 0 || (unit == "%" && number > 100)) {
                                    finish(kind, ObservationStatus.INVALID, null, null, true, "Capability access=$access · null/범위 밖 응답")
                                } else finish(kind, ObservationStatus.RECEIVED, value.toString(), unit, true,
                                    "Vehicle 2.3.2 · Capability access=$access · 공식 get 응답 · hardware=${Build.HARDWARE}")
                            } }, { e -> dispatch { fail(kind, "읽기 access=$access", e, true) } })
                        } catch (e: Exception) { fail(kind, "읽기 access=$access", e, true) }
                    }
                } }, failed)
            } catch (e: Exception) { failed(e) }
        }
        fun scalar(kind:String,permission:String,unit:String,
                   capability:((CapabilityConfig)->Unit,(Exception)->Unit)->Unit,
                   read:((Any?)->Unit,(Exception)->Unit)->Unit){
            if(context.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){finish(kind,ObservationStatus.PERMISSION_DENIED,null,null,null,"필요한 읽기 권한: $permission");return}
            try{capability({config->dispatch{
                if(kind !in outstanding)return@dispatch
                if(config.accessType !in setOf(AccessType.READ,AccessType.READ_WRITE)){finish(kind,ObservationStatus.UNSUPPORTED,null,null,false,"공식 Capability 읽기 미지원");return@dispatch}
                try{read({value->dispatch{
                    if(VehicleHealth.valid(kind,value))finish(kind,ObservationStatus.RECEIVED,value.toString(),unit,true,"Vehicle2.3.2 공개 GET · access="+config.accessType+" · hardware="+Build.HARDWARE)
                    else finish(kind,ObservationStatus.INVALID,null,null,true,"공식 응답이 없거나 유효하지 않아요. $kind")
                }},{e->dispatch{fail(kind,"읽기",e,true)}})}catch(e:Exception){fail(kind,"읽기",e,true)}
            }},{e->dispatch{fail(kind,"Capability",e,null)}})}catch(e:Exception){fail(kind,"Capability",e,null)}
        }
        main.postDelayed({
            if (run == generation) outstanding.toList().forEach {
                finish(it, ObservationStatus.TIMEOUT, null, null, null, "15초 안에 Capability/읽기 응답이 완료되지 않았습니다.")
            }
        }, 15_000)
        try {
            val sdk = Vehicle(context.applicationContext).also { vehicle = it }
            sdk.initialize()
            request("EV_SOC", "pleos.car.permission.CAR_ENERGY", "%",
                sdk.getEvBattery()::checkEvBatteryStateOfChargeCapabilities,
                { ok, bad -> sdk.getEvBattery().getEvBatteryStateOfCharge({ ok(it) }, bad) })
            request("ODOMETER", "pleos.car.permission.CAR_DRIVE", "km",
                sdk.getOdometer()::checkCurrentDistanceDrivenCapabilities,
                { ok, bad -> sdk.getOdometer().getCurrentDistanceDriven({ ok(it) }, bad) })
            request("FUEL_PERCENT", "pleos.car.permission.CAR_ENERGY", "%",
                sdk.getFuel()::checkFuelPercentCapabilities,
                { ok, bad -> sdk.getFuel().getFuelPercent({ ok(it) }, bad) })
            val door=sdk.getDoor()
            for((kind,value)in listOf("DOOR_FL" to DoorValue.ROW_1_LEFT,"DOOR_FR" to DoorValue.ROW_1_RIGHT,"DOOR_RL" to DoorValue.ROW_2_LEFT,"DOOR_RR" to DoorValue.ROW_2_RIGHT)){
                val area=DoorArea(value)
                scalar(kind,"pleos.car.permission.CAR_DOORS","position",{ok,bad->door.checkDoorPositionCapabilities({config,areas->if(areas.containsKey(area))ok(config)else dispatch{finish(kind,ObservationStatus.UNSUPPORTED,null,null,false,"해당 도어 영역 미지원")}},bad)},{ok,bad->door.getCurrentDoorPosition(area,{ok(it)},bad)})
            }
            val light=sdk.getLight()
            scalar("LOW_BEAM","pleos.car.permission.CAR_LIGHTS","state",{ok,bad->light.checkLowBeamLightStateCapabilities({config,_->ok(config)},bad)},{ok,bad->light.getCurrentLowBeamLightState({ok(it)},bad)})
            scalar("HIGH_BEAM","pleos.car.permission.CAR_LIGHTS","state",{ok,bad->light.checkHighBeamLightStateCapabilities({config,_->ok(config)},bad)},{ok,bad->light.getCurrentHighBeamLightState({ok(it)},bad)})
            scalar("TAIL_LAMP","pleos.car.permission.CAR_LIGHTS","state",{ok,bad->light.checkTailLampLightStateCapabilities({config,_->ok(config)},bad)},{ok,bad->light.getCurrentTailLampLightState({ok(it)},bad)})
            scalar("TURN_SIGNAL","pleos.car.permission.CAR_LIGHTS","state",sdk.getTurnSignal()::checkTurnSignalStateCapabilities,{ok,bad->sdk.getTurnSignal().getCurrentTurnSignalState({ok(it)},bad)})
            val safe=sdk.getSafety()
            scalar("ENGINE_OIL","pleos.car.permission.CAR_SAFETY","state",{ok,bad->safe.checkEngineOilLevelStateCapabilities({config,_->ok(config)},bad)},{ok,bad->safe.getEngineOilLevelState({ok(it)},bad)})
            scalar("BRAKE_FLUID","pleos.car.permission.CAR_SAFETY","state",{ok,bad->safe.checkBrakeFluidLevelStateCapabilities({config,_->ok(config)},bad)},{ok,bad->safe.getBrakeFluidLevelState({ok(it)},bad)})
            scalar("WASHER_LOW","pleos.car.permission.CAR_SAFETY","state",safe::checkLowWasherFluidWarningStateCapabilities,{ok,bad->safe.hasLowWasherFluidWarning({ok(it)},bad)})
            scalar("POWER_DOWN","pleos.car.permission.CAR_SAFETY","state",safe::checkPowerDownWarningIndicatorCapabilities,{ok,bad->safe.hasPowerDownWarningIndicator({ok(it)},bad)})
            scalar("BRAKE_PEDAL","pleos.car.permission.CAR_DRIVE","state",sdk.getBrake()::checkBrakePedalStatusCapabilities,{ok,bad->sdk.getBrake().getCurrentBrakePedalStatus({ok(it)},bad)})
            val tire=sdk.getTire()
            for((kind,value)in listOf("TIRE_LIMIT_FL" to WheelValue.LEFT_FRONT,"TIRE_LIMIT_FR" to WheelValue.RIGHT_FRONT,"TIRE_LIMIT_RL" to WheelValue.LEFT_REAR,"TIRE_LIMIT_RR" to WheelValue.RIGHT_REAR)){
                val area=WheelArea(value)
                scalar(kind,"pleos.car.permission.CAR_SAFETY","kPa",{ok,bad->tire.checkLowTirePressureCapabilities({config,areas->if(area in areas)ok(config)else dispatch{finish(kind,ObservationStatus.UNSUPPORTED,null,null,false,"해당 바퀴 영역 미지원")}},bad)},{ok,bad->tire.getLowTirePressure(area,{ok(it)},bad)})
            }
        } catch (e: Exception) { outstanding.toList().forEach { fail(it, "초기화", e, null) } }
    }
    fun stop() {
        generation++
        main.removeCallbacksAndMessages(null)
        activeSession?.let{session->outstanding.toList().forEach{kind->
            runCatching{store.append(VehicleObservation(kind,null,null,null,source,null,false,session,
                ObservationStatus.CONNECTION_FAILED,"앱 가시/안전 상태 종료로 완료 전 읽기를 중단했습니다."))}
        }}
        activeSession=null
        try { vehicle?.release() } catch (_: Exception) { }
        vehicle = null
        outstanding.clear()
        busy = false
        message = "SDK 2.3.2 준비됨 · 연결이 해제되었습니다. 이전 수신 기록은 아래에서 확인해 주세요."
    }
}
