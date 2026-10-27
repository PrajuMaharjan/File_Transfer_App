package com.prajumaharjan.filetransfer

import android.content.Context

class AndroidProfileStore (context: Context) : ProfileStore{
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    override fun load(): Profile? {
        val id = prefs.getString("id",null) ?: return null
        val name = prefs.getString("name",null) ?: return null
        val avatar = try{
            AvatarColor.valueOf(prefs.getString("avatar","") ?: "")
        }catch (e: Exception){
            AvatarColor.GREY
        }
        return Profile(id,name,avatar)
    }

    override fun save(profile: Profile) {
        prefs.edit()
            .putString("id",profile.id)
            .putString("name",profile.name)
            .putString("avatar",profile.avatar.name)
            .apply()
    }
}