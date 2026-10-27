package com.prajumaharjan.filetransfer

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class DesktopProfileStore (
    private val file:File = File(System.getProperty("user.home"),"filetransfer/profile.json")
    ):ProfileStore{

        private val json=Json{
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        override fun load(): Profile?{
            return try{
                if(!file.exists()){
                    null
                }else{
                    json.decodeFromString(Profile.serializer(),file.readText())
                }
            }catch (e: Exception){
                null
            }
        }

        override fun save(profile: Profile){
            file.parentFile.mkdirs()
            val tmp=File(file.parentFile,file.name + ".tmp")
            tmp.writeText(json.encodeToString(Profile.serializer(),profile))
            Files.move(tmp.toPath(),file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }