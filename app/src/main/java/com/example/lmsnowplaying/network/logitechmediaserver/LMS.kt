package com.example.lmsnowplaying.network.logitechmediaserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException

object LMS{

    private val lmsApi = ApiClient.apiService

    var isPlaying: Boolean = false
        private set

    var elapsedTime: Double = 0.0
        private set

    var trackDuration: Double = 0.0
        private set

    var playerMac: String = "00:00:00:00:00:00"
        private set

    var playerName: String = "No Player"
        private set

    var players: MutableList<Pair<String, String>> = arrayListOf()
        private set

    suspend fun getPlayers(){

        val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"players\", \"0\", \"8\"]]}"
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        val res = lmsApi.getPlayer(requestBody)

        val jsonData: String? = res.body()?.string()
        val obj = JSONObject(jsonData)
        val getObject = obj.getJSONObject("result")

        val loop = getObject.getJSONArray("players_loop")
        players.clear()
        for (i in 0 until loop.length()) {
            val item = loop.getJSONObject(i)
            val name = item.getString("name")
            val player_mac = item.getString("playerid")
            players.add(Pair(name, player_mac))
        }

        println(players)
    }

    fun setPlayer(name: String, mac: String){
        val previousMac = playerMac
        playerName = name
        playerMac = mac

        // Put the player we're switching away from into standby, same as
        // when the app is fully exited - otherwise self-powered players
        // like the Boom just keep playing in the background unnoticed.
        if (previousMac != "00:00:00:00:00:00" && previousMac != mac) {
            CoroutineScope(Dispatchers.IO).launch {
                playPauseFor(previousMac, false)
                powerFor(previousMac, false)
            }
        }
    }

    suspend fun getSongInfo(playerMAC: String, songID: Int): JSONArray {
        val reqString =
            "{\"method\": \"slim.request\", \"params\": [\"$playerMAC\", [\"songinfo\",0,100,\"track_id:$songID\",\"tags:acly4\"]]}"

        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        val res = lmsApi.getCurrentSong(requestBody)

        val jsonData: String? = res.body()?.string()
        val obj = JSONObject(jsonData)
        val getObject = obj.getJSONObject("result")

        return getObject.getJSONArray("songinfo_loop")
    }

    suspend fun playPause(){
        if (playerMac == "00:00:00:00:00:00") return
        val reqString: String = if(isPlaying){
            "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"pause\"]]}"
        }else{
            "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"play\"]]}"
        }
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        lmsApi.playPause(requestBody)
    }

    suspend fun playPause(play: Boolean){
        playPauseFor(playerMac, play)
        status()
    }

    private suspend fun playPauseFor(mac: String, play: Boolean){
        if (mac == "00:00:00:00:00:00") return
        val playString: String = if(play){
            "play"
        }else{
            "pause"
        }
        val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"$playString\"]]}"
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        lmsApi.playPause(requestBody)
    }

    suspend fun next(){
        if (playerMac == "00:00:00:00:00:00") return
        val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"playlist\",\"index\",\"+1\"]]}"
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        lmsApi.next(requestBody)
    }

    suspend fun prev(){
        if (playerMac == "00:00:00:00:00:00") return
        val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"playlist\",\"index\",\"-1\"]]}"
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        lmsApi.prev(requestBody)
    }

    // Puts the player into standby (0) or wakes it (1). Needed for
    // self-powered players like the Boom - just pausing/stopping playback
    // isn't enough for those, they need an actual power-off command.
    suspend fun power(on: Boolean){
        powerFor(playerMac, on)
    }

    private suspend fun powerFor(mac: String, on: Boolean){
        if (mac == "00:00:00:00:00:00") return
        val powerValue = if (on) "1" else "0"
        val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"power\", \"$powerValue\"]]}"
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        lmsApi.power(requestBody)
    }

    suspend fun status() {
        val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"status\", \"-\",1]]}"
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        val res = lmsApi.status(requestBody)
        val jsonData: String? = res.body()?.string()
        val obj = JSONObject(jsonData)
        val getObject = obj.getJSONObject("result")
        val mode = getObject.getString("mode")
        isPlaying = mode == "play"
        elapsedTime = getObject.optDouble("time", 0.0).let { if (it.isNaN()) 0.0 else it }
        trackDuration = getObject.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it }
    }

    suspend fun update(playerMAC: String): List<String> {
        val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMAC\", [\"status\", \"-\",1]]}"
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        val res = lmsApi.getCurrentSong(requestBody)
        val jsonData: String? = res.body()?.string()
        val obj = JSONObject(jsonData)
        val getObject = obj.getJSONObject("result")

        var title = ""
        var artist = ""
        var coverid = ""
        var portraitId = ""
        var album = ""
        var year = ""

        try {
            val resultLoop = getObject.getJSONArray("playlist_loop")
            val firstResult = resultLoop.getJSONObject(0)
            title = firstResult.getString("title")
            val id = firstResult.getString("id")

            if(id.first() != '-'){
                val res2 = getSongInfo(playerMAC, id.toInt())
                for (i in 0 until res2.length()) {
                    val item = res2.getJSONObject(i)
                    if (artist == "" && item.has("artist")) {
                        artist = item.getString("artist")
                    }
                    if (coverid == "" && item.has("coverid")) {
                        coverid = item.getString("coverid")
                    }
                    if (portraitId == "" && item.has("portraitid")) {
                        portraitId = item.getString("portraitid")
                    }
                    if (album == "" && item.has("album")) {
                        album = item.getString("album")
                    }
                    if (year == "" && item.has("year")) {
                        year = item.getString("year")
                    }
                }
            }

        }catch (e: JSONException){
            return listOf(title, artist, coverid, portraitId, album, year)
        }

        return listOf(title, artist, coverid, portraitId, album, year)
    }

}