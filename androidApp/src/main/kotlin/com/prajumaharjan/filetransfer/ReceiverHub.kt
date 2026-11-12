package com.prajumaharjan.filetransfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean

object ReceiverHub {
    var running by mutableStateOf(false)
    var pending by mutableStateOf<Pair<OfferRequest, CompletableDeferred<Boolean>>?>(null)
    var transfer by mutableStateOf<TransferProgress?>(null)
    var cancelFlag = AtomicBoolean(false)
    var logs = mutableStateListOf<String>()
}