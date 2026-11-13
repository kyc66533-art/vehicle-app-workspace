package com.nextdrive.pleos.voice

import com.nextdrive.pleos.model.SymptomType

sealed class VoiceCommand {
    data class Record(val text:String,val type:SymptomType):VoiceCommand()
    data class FollowUp(val question:String):VoiceCommand()
    data object Wake:VoiceCommand();data object Soc:VoiceCommand();data object Attention:VoiceCommand()
    data object Handover:VoiceCommand();data object Recent:VoiceCommand();data object Repeat:VoiceCommand()
    data object RecordContext:VoiceCommand();data object End:VoiceCommand();data object Clarify:VoiceCommand();data object StopListening:VoiceCommand()
}
/** Deliberately narrow local router. No model is allowed to decide a SQLite write. */
object VoiceCommandRouter {
    /** Observed ON_DEVICE result for the user's wake trial. This is NOT a wake match:
     * only the foreground standby host may ask for explicit confirmation of this whole phrase. */
    fun needsWakeConfirmation(raw:String)=raw.trim().replace(Regex("[\\s,.!?。]"),"")=="가있다"
    fun confirmsWake(raw:String)=raw.trim().replace(Regex("[\\s,.!?。]"),"") in setOf("네","예","맞아요")
    /** Orthographic whitespace only; no unverified phonetic aliases. */
    fun afterWake(raw:String):String?{
        val text=raw.trim()
        val wake=Regex("""^카\s*[잇있]\s*다(?:[,.!?，。\s]+|$)""").find(text)?:return null
        return text.substring(wake.value.length).trim()
    }
    fun route(raw:String,autoSymptoms:Boolean,anchored:Boolean):VoiceCommand {
        val text=afterWake(raw)?:raw.trim()
        val flat=text.replace(Regex("[\\s,.!?。]"),"")
        if(flat in setOf("음성대기꺼줘","음성대기꺼주세요","마이크꺼줘","마이크꺼주세요","청취중지","그만들어","듣지마","듣지마세요"))return VoiceCommand.StopListening
        if(raw.length>1000||Regex("[\"“”‘’]|(?:하지|주지|시키지)\\s*마|저장하지|기록하지|(?:기록|저장).{0,8}취소|라고\\s*(했|말)|예를\\s*들|만약").containsMatchIn(raw))return VoiceCommand.Clarify
        if(flat.isEmpty())return VoiceCommand.Wake
        if(Regex("^(그만(할게|해|할래|할게요)?|종료(해줘)?|대화종료|나중에다시볼게)$").matches(flat))return VoiceCommand.End
        if(Regex("배터리.*(얼마|남)|충전량|얼마나남았").containsMatchIn(flat))return VoiceCommand.Soc
        if(Regex("확인할(문제|내용)|차량(상태|안내)|차에문제").containsMatchIn(flat))return VoiceCommand.Attention
        if(Regex("다른운전자|인계|이전운전자").containsMatchIn(flat))return VoiceCommand.Handover
        if(Regex("최근기록|최근.*문제알려").containsMatchIn(flat))return VoiceCommand.Recent
        if(Regex("다시설명|다시읽|다시알려").containsMatchIn(flat))return VoiceCommand.Repeat
        if(Regex("^(이내용|이거|지금내용)(기록|저장)해(줘|주세요)$").matches(flat))return VoiceCommand.RecordContext
        val explicit=Regex("[.\\s,]*(기록|저장)해\\s*(줘|주세요)[.! ]*$")
        if(anchored&&!explicit.containsMatchIn(text)&&Regex("어떻게|정비소|점검|왜|해야|필요|원인").containsMatchIn(text))return VoiceCommand.FollowUp(text)
        val body=text.replace(explicit,"").trim().trimEnd('.').trim()
        val symptom=Regex("(차|차량|앞쪽|뒤쪽|핸들|바퀴|엔진|브레이크|배터리).{0,100}(소리|소음|진동|떨|경고|이상|냄새|안\\s*돼|작동하지)").containsMatchIn(body)
        val uncertain=Regex("[?？]|기록할까|저장할까|기록하는|기록방법|안\\s*나|없어요|없어").containsMatchIn(text)
        if(body.length>=3&&!uncertain&&(explicit.containsMatchIn(text)||autoSymptoms&&symptom))return VoiceCommand.Record(body,when{
            Regex("소리|소음").containsMatchIn(body)->SymptomType.NOISE
            Regex("진동|떨").containsMatchIn(body)->SymptomType.VIBRATION
            Regex("경고").containsMatchIn(body)->SymptomType.WARNING
            else->SymptomType.OTHER
        })
        if(anchored&&Regex("어떻게|정비소|점검|왜|해야|필요|원인").containsMatchIn(text))return VoiceCommand.FollowUp(text)
        return VoiceCommand.Clarify
    }
}
