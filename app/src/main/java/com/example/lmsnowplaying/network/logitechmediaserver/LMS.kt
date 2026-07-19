package com.example.lmsnowplaying.network.logitechmediaserver

import android.util.Log
import com.example.lmsnowplaying.settings.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.io.IOException

private const val TAG = "LMS"

// One entry in the upcoming playlist queue, used for the "next track"
// preview and the pick-a-different-track list.
data class QueueTrack(
    val absoluteIndex: Int,
    val title: String,
    val artist: String,
    val coverId: String,
)

// A generic id/name pair, used for browsing artists and albums.
data class BrowseItem(
    val id: String,
    val name: String,
)

// A track found while browsing an album, used to pick a replacement for
// the upcoming ("next") track in the queue.
data class BrowseTrack(
    val id: String,
    val title: String,
    val trackNum: Int,
)

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

    // A player that just went to standby, or whose app just closed, is
    // often briefly unreachable (connection refused/timeout/EOF while it
    // wakes back up). None of our network calls should be able to crash
    // the whole app over that - we log it and let the caller treat it as
    // "didn't work this time" instead.
    private suspend fun <T> safeCall(what: String, default: T, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: IOException) {
            Log.w(TAG, "$what failed (network): ${e.message}")
            default
        } catch (e: JSONException) {
            Log.w(TAG, "$what failed (bad response): ${e.message}")
            default
        }
    }

    suspend fun getPlayers() {
        safeCall("getPlayers", Unit) {
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
        return safeCall("getSongInfo", JSONArray()) {
            val reqString =
                "{\"method\": \"slim.request\", \"params\": [\"$playerMAC\", [\"songinfo\",0,100,\"track_id:$songID\",\"tags:acly4\"]]}"

            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.getCurrentSong(requestBody)

            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")

            getObject.getJSONArray("songinfo_loop")
        }
    }

    suspend fun playPause(){
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("playPause", Unit) {
            val reqString: String = if (isPlaying) {
                "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"pause\"]]}"
            } else {
                "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"play\"]]}"
            }
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playPause(requestBody)
        }
    }

    suspend fun playPause(play: Boolean){
        playPauseFor(playerMac, play)
        status()
    }

    private suspend fun playPauseFor(mac: String, play: Boolean){
        if (mac == "00:00:00:00:00:00") return
        safeCall("playPauseFor", Unit) {
            val playString: String = if (play) {
                "play"
            } else {
                "pause"
            }
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"$playString\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playPause(requestBody)
        }
    }

    suspend fun next(){
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("next", Unit) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"playlist\",\"index\",\"+1\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.next(requestBody)
        }
    }

    suspend fun prev(){
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("prev", Unit) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"playlist\",\"index\",\"-1\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.prev(requestBody)
        }
    }

    // Puts the player into standby (0) or wakes it (1). Needed for
    // self-powered players like the Boom - just pausing/stopping playback
    // isn't enough for those, they need an actual power-off command.
    suspend fun power(on: Boolean){
        powerFor(playerMac, on)
    }

    private suspend fun powerFor(mac: String, on: Boolean){
        if (mac == "00:00:00:00:00:00") return
        safeCall("powerFor", Unit) {
            val powerValue = if (on) "1" else "0"
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"power\", \"$powerValue\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.power(requestBody)
        }
    }

    suspend fun status() {
        safeCall("status", Unit) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"status\", \"-\",1]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.status(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val mode = getObject.optString("mode", "")
            isPlaying = mode == "play"
            elapsedTime = getObject.optDouble("time", 0.0).let { if (it.isNaN()) 0.0 else it }
            trackDuration = getObject.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it }
        }
    }

    suspend fun update(playerMAC: String): List<String> {
        return safeCall("update", listOf("", "", "", "", "", "")) {
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

                if (id.first() != '-') {
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

            } catch (e: JSONException) {
                return@safeCall listOf(title, artist, coverid, portraitId, album, year)
            }

            listOf(title, artist, coverid, portraitId, album, year)
        }
    }

    // Fetches the upcoming queue starting at the currently playing track
    // (index 0 in the returned list = current track). Used both for the
    // "next track" preview and the pick-a-different-track list.
    suspend fun getUpcoming(mac: String, count: Int = 25): List<QueueTrack> {
        if (mac == "00:00:00:00:00:00") return emptyList()
        return safeCall("getUpcoming", emptyList()) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"status\", \"-\", $count, \"tags:ac\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.queue(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")

            val curIndex = getObject.optInt("playlist_cur_index", 0)
            val loop = getObject.optJSONArray("playlist_loop") ?: JSONArray()
            val list = mutableListOf<QueueTrack>()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                list.add(
                    QueueTrack(
                        absoluteIndex = curIndex + i,
                        title = item.optString("title", ""),
                        artist = item.optString("artist", ""),
                        coverId = item.optString("coverid", ""),
                    )
                )
            }
            list
        }
    }

    // Jumps directly to an arbitrary (absolute) position in the playlist,
    // e.g. to play a track further down the queue than just "next".
    suspend fun jumpTo(mac: String, absoluteIndex: Int) {
        if (mac == "00:00:00:00:00:00") return
        safeCall("jumpTo", Unit) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"playlist\",\"index\",\"$absoluteIndex\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playlistIndex(requestBody)
        }
    }

    // Replaces whatever is currently queued up as the "next" track with a
    // newly chosen one: removes the old next-in-queue entry (if any), then
    // inserts the chosen track right after the currently playing one.
    suspend fun replaceNext(mac: String, newTrackId: String) {
        if (mac == "00:00:00:00:00:00") return
        safeCall("replaceNext", Unit) {
            val upcoming = getUpcoming(mac, 2)
            val hasNext = upcoming.size > 1
            if (hasNext) {
                val nextIndex = upcoming[1].absoluteIndex
                val delReq = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"playlist\",\"delete\",\"$nextIndex\"]]}"
                val delBody = delReq.toRequestBody("application/json".toMediaTypeOrNull())
                lmsApi.playlistDelete(delBody)
            }

            val insertReq = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"playlistcontrol\",\"cmd:insert\",\"track_id:$newTrackId\"]]}"
            val insertBody = insertReq.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playlistControl(insertBody)
        }
    }

    // Outcome of a "replace next track" attempt, used to give an accurate
    // toast without needlessly calling out SugarCube to people who don't
    // use it.
    enum class ReplaceResult { SUGARCUBE, DSTM, RANDOM }

    // Result of attempting the SugarCube GET: whether the HTTP call itself
    // succeeded, and (only meaningful if it did) whether the next track
    // actually changed afterwards.
    private data class SugarCubeAttempt(val httpOk: Boolean, val changed: Boolean)

    // Cached once per app run: is the SugarCube plugin actually reachable on
    // this server? Null = not checked yet. Avoids repeatedly trying (and
    // silently failing) the SugarCube call, and the associated delay, for
    // people who simply don't have SugarCube installed.
    private var sugarCubeAvailable: Boolean? = null

    // Triggers the SugarCube plugin's own "Replace This Track" action for
    // the next-queued track (the exact same GET its own button fires), so
    // we get its music-similarity pick instead of a random track.
    private suspend fun replaceNextViaSugarCube(mac: String): SugarCubeAttempt {
        if (mac == "00:00:00:00:00:00") return SugarCubeAttempt(httpOk = false, changed = false)
        if (sugarCubeAvailable == false) return SugarCubeAttempt(httpOk = false, changed = false)

        return safeCall("replaceNextViaSugarCube", SugarCubeAttempt(httpOk = false, changed = false)) {
            val before = getUpcoming(mac, 2).getOrNull(1)

            val url = "${AppConfig.current.lmsUrl}/plugins/SugarCube/settings/quickplay.html?player=$mac&forcereplace=1"
            val client = OkHttpClient.Builder()
                .addInterceptor(BasicAuthInterceptor(AppConfig.current.lmsUsername, AppConfig.current.lmsPassword))
                .build()
            val request = Request.Builder().url(url).build()

            val ok = try {
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
            sugarCubeAvailable = ok
            if (!ok) return@safeCall SugarCubeAttempt(httpOk = false, changed = false)

            // Give SugarCube a brief moment to actually update the playlist
            // before we re-check - the GET returning doesn't guarantee the
            // playlist change has landed yet.
            kotlinx.coroutines.delay(600)

            val after = getUpcoming(mac, 2).getOrNull(1)
            val changed = after != null && (after.title != before?.title || after.coverId != before.coverId)
            SugarCubeAttempt(httpOk = true, changed = changed)
        }
    }

    // Don't Stop The Music has no standalone "generate one now" command -
    // it only reacts to the server's own playlist events, and only adds
    // tracks (via whichever provider is configured for this player, e.g.
    // MusicIP/SugarCube, LastMix, Random Mix) once the queue is close to
    // running out. We can legitimately trigger that: if "next" is the only
    // track left in the queue, deleting it drops the remaining count to 0 -
    // safely below DSTM's default threshold - which fires its own
    // "delete" playlist notification and lets it fill the gap. Only
    // attempted when there's nothing else queued after "next", so this
    // never deletes tracks the user still wants further down the queue.
    private suspend fun replaceNextViaDstm(mac: String): Boolean {
        if (mac == "00:00:00:00:00:00") return false

        return safeCall("replaceNextViaDstm", false) {
            val queue = getUpcoming(mac, 3)
            val tailLength = queue.size - 1
            if (tailLength != 1) return@safeCall false

            // DSTM explicitly refuses to add anything while Lyrion's own Random
            // Mix (RandomPlay) plugin is active on this player - "disable" only
            // stops it from adding further tracks, it doesn't interrupt what's
            // already playing.
            val rpReq = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"randomplay\", \"disable\"]]}"
            val rpBody = rpReq.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.randomPlay(rpBody)

            val before = queue.getOrNull(1)
            val nextIndex = queue[1].absoluteIndex
            val delReq = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"playlist\",\"delete\",\"$nextIndex\"]]}"
            val delBody = delReq.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playlistDelete(delBody)

            // Give DSTM more of a chance to react to the "delete" notification
            // and (if configured) add a replacement via its provider - it can
            // delay its own reaction (e.g. until later in the current track),
            // so this is a best-effort wait, not a guarantee.
            kotlinx.coroutines.delay(5000)

            val after = getUpcoming(mac, 2).getOrNull(1)
            after != null && (after.title != before?.title || after.coverId != before?.coverId)
        }
    }

    // The current track's genre_id and artist_id (tags p and s), used to
    // keep the random fallback within the same genre/artist rather than
    // picking from the whole library.
    private suspend fun getCurrentGenreArtist(mac: String): Pair<String, String> {
        return safeCall("getCurrentGenreArtist", Pair("", "")) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"status\", \"-\", 1, \"tags:ps\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.status(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("playlist_loop") ?: JSONArray()
            if (loop.length() == 0) return@safeCall Pair("", "")
            val item = loop.getJSONObject(0)
            Pair(item.optString("genre_id", ""), item.optString("artist_id", ""))
        }
    }

    // Fetches up to [count] tracks matching the given filter (a tagged
    // param like "genre_id:7" or "artist_id:3", or null for the whole
    // library) and returns one random track id from that set, or null if
    // none were found.
    private suspend fun randomTrackId(filter: String?, count: Int = 300): String? {
        return safeCall("randomTrackId", null) {
            val filterParam = if (filter != null) ", \"$filter\"" else ""
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"tracks\", 0, $count$filterParam]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.browseTracks(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("titles_loop") ?: JSONArray()
            if (loop.length() == 0) return@safeCall null
            val randomItem = loop.getJSONObject((0 until loop.length()).random())
            randomItem.optString("id").takeIf { it.isNotEmpty() }
        }
    }

    // Native LMS fallback for when SugarCube isn't installed/enabled, or
    // didn't change anything: picks a random track, preferring the current
    // track's genre first, then its artist, then finally anything in the
    // library - and puts it in the "next" slot the same way replaceNext()
    // does.
    private suspend fun replaceNextWithRandom(mac: String) {
        if (mac == "00:00:00:00:00:00") return

        val (genreId, artistId) = getCurrentGenreArtist(mac)

        val trackId = (if (genreId.isNotEmpty()) randomTrackId("genre_id:$genreId") else null)
            ?: (if (artistId.isNotEmpty()) randomTrackId("artist_id:$artistId") else null)
            ?: randomTrackId(null)

        if (trackId != null) {
            replaceNext(mac, trackId)
        }
    }

    // Tries SugarCube's own replace action first (music-similarity pick),
    // then gives DSTM a legitimate chance to fill the gap via whichever
    // provider is configured for this player, and only then falls back to
    // a genuinely random library track - so the button never dead-ends,
    // and people who don't use SugarCube/DSTM never see them mentioned.
    suspend fun replaceNextSmart(mac: String): ReplaceResult {
        val attempt = replaceNextViaSugarCube(mac)
        if (attempt.changed) return ReplaceResult.SUGARCUBE

        if (replaceNextViaDstm(mac)) return ReplaceResult.DSTM

        replaceNextWithRandom(mac)
        return ReplaceResult.RANDOM
    }

    // --- Library browsing (artist -> album -> track), used to pick the
    // replacement for the "next" track. ---

    suspend fun getArtists(count: Int = 500): List<BrowseItem> {
        return safeCall("getArtists", emptyList()) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"artists\", 0, $count]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.browseArtists(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("artists_loop") ?: JSONArray()
            val list = mutableListOf<BrowseItem>()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                list.add(BrowseItem(item.optString("id"), item.optString("artist")))
            }
            list
        }
    }

    suspend fun getAlbums(artistId: String, count: Int = 500): List<BrowseItem> {
        return safeCall("getAlbums", emptyList()) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"albums\", 0, $count, \"artist_id:$artistId\", \"sort:yearalbum\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.browseAlbums(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("albums_loop") ?: JSONArray()
            val list = mutableListOf<BrowseItem>()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                list.add(BrowseItem(item.optString("id"), item.optString("album")))
            }
            list
        }
    }

    suspend fun getTracks(albumId: String, count: Int = 200): List<BrowseTrack> {
        return safeCall("getTracks", emptyList()) {
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"titles\", 0, $count, \"album_id:$albumId\", \"sort:tracknum\", \"tags:t\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.browseTracks(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("titles_loop") ?: JSONArray()
            val list = mutableListOf<BrowseTrack>()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                list.add(BrowseTrack(item.optString("id"), item.optString("title"), item.optInt("tracknum", 0)))
            }
            list
        }
    }

}
