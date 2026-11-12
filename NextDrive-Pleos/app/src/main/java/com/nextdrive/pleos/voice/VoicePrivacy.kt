package com.nextdrive.pleos.voice

import android.content.Context
import com.nextdrive.pleos.data.ConversationStore

object VoicePrivacy {
    fun withdrawCloud(context:Context,subject:String){
        val prefs=context.getSharedPreferences("voice-privacy-v1",Context.MODE_PRIVATE)
        val pending=prefs.getStringSet("pending",emptySet()).orEmpty()+subject
        check(prefs.edit().putStringSet("pending",pending).commit())
        VoiceSettings(context).revokeCloud(subject)
        try{
            forgetDialogue(context,subject)
            ConversationStore(context).use{it.withdrawCloud(subject)}
            check(prefs.edit().putStringSet("pending",pending-subject).commit())
        }catch(e:Exception){
            if((e as? com.nextdrive.pleos.ai.PortableFailure)?.code=="REQUEST_PENDING")com.nextdrive.pleos.ai.PortableOperationLock.whenIdle{runCatching{reconcile(context)}}
            throw e
        }
    }
    fun reconcile(context:Context){context.getSharedPreferences("voice-privacy-v1",Context.MODE_PRIVATE).getStringSet("pending",emptySet()).orEmpty().toList().forEach{withdrawCloud(context,it)}}
    fun forgetDialogue(context:Context,subject:String,turns:Set<String>?=null)=com.nextdrive.pleos.ai.PortableOperationLock.run{forgetDialogueLocked(context,subject,turns)}
    private fun forgetDialogueLocked(context:Context,subject:String,turns:Set<String>?){
        val store=com.nextdrive.pleos.ai.PortableAiStore(context,"portable-issue-v1-$subject")
        val root=store.read();val jobs=root.optJSONObject("jobs")?:return
        val keys=jobs.keys().asSequence().filter{it.startsWith("dialogue:")&&(turns==null||it.removePrefix("dialogue:") in turns)}.toList()
        for(key in keys){val job=jobs.getJSONObject(key);val pending=job.optJSONObject("pending")
            if(pending==null)jobs.remove(key)else{
                val meta=org.json.JSONObject();for(field in listOf("intent","operation","created"))if(pending.has(field))meta.put(field,pending.get(field))
                jobs.put(key,org.json.JSONObject().put("pending",meta.put("state","PRIVACY_WITHDRAWN")))
            }
        }
        if(keys.isNotEmpty())store.write(root)
    }
    fun expireDialogue(context:Context,subject:String,now:Long=System.currentTimeMillis()){
        val store=com.nextdrive.pleos.ai.PortableAiStore(context,"portable-issue-v1-$subject");val root=store.read();val jobs=root.optJSONObject("jobs")?:return
        val turns=jobs.keys().asSequence().filter{key->key.startsWith("dialogue:")&&jobs.getJSONObject(key).let{j->
            val at=j.optJSONObject("last")?.optLong("saved_at")?:j.optJSONObject("pending")?.optLong("created")?:now
            now-at>30L*86400000
        }}.map{it.removePrefix("dialogue:")}.toSet()
        if(turns.isNotEmpty())forgetDialogue(context,subject,turns)
    }
}
