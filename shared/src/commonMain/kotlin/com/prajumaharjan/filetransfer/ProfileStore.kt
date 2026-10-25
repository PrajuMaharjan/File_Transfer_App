package com.prajumaharjan.filetransfer

interface ProfileStore {
    fun load(): Profile?
    fun save(profile: Profile)
}