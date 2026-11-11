package com.nextdrive.pleos.integration

/** One completed SDK session can update only the original, unchanged editing draft once.
 * This does not create an issue, choose an author, or treat partial speech as a result. */
class SpeechDraftDelivery(private val draftId:String, private val ownerId:String, private val original:String) {
    private var consumed=false
    fun accept(text:String,currentDraft:String?,currentOwner:String?,currentText:String,editable:Boolean):String? {
        if(consumed)return null
        consumed=true
        if(!editable||currentDraft!=draftId||currentOwner!=ownerId||currentText!=original)return null
        val final=text.trim()
        if(final.isEmpty()||final.length>800)return null
        val merged=if(original.isBlank())final else original+"\n"+final
        return merged.takeIf{it.length<=1000}
    }
}
