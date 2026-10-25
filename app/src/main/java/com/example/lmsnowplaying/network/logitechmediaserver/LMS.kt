package com.example.lmsnowplaying.network.logitechmediaserver

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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

// The whole play queue and the position of the current track in it.
data class QueueList(
    val tracks: List<QueueTrack> = emptyList(),
    val currentIndex: Int = 0,
)

// A generic id/name pair, used for browsing artists and albums.
data class BrowseItem(
    val id: String,
    val name: String,
    // For albums: id of a track whose cover represents the album (used for
    // thumbnails); empty when unknown.
    val artworkId: String = "",
)

// Repeat / shuffle / Don't Stop The Music state of a player.
// repeat: 0 off, 1 song, 2 queue; shuffle: 0 off, 1 songs, 2 albums;
// dstmProvider: empty when Don't Stop The Music is off.
data class PlayModes(
    val repeat: Int,
    val shuffle: Int,
    val dstmProvider: String,
)

// Plugins that can keep the queue filled ("Auto Mix"). Only one of them
// may be active per player; LMSNP enforces that when switching one on.
enum class MixPlugin(val label: String) {
    SUGARCUBE("SugarCube"),
    MIPSTER("MIPster"),
    RANDOMFLOW("Random Flow"),
    RANDOMPLAY("Random Mix"),
    DSTM("Don't Stop The Music"),
}

enum class MixAction { START_MIX, START_BATCH, ADD_BATCH }

// Which of those plugins are installed (available) and which one is on.
data class MixState(
    val available: Set<MixPlugin> = emptySet(),
    val on: Set<MixPlugin> = emptySet(),
)

// An entry in the music folder tree: a sub folder or a track.
data class FolderEntry(
    val id: String,
    val name: String,
    val isFolder: Boolean,
)

// An entry in the Lyrion favorites: a favorite (station, track...) or a folder of favorites.
data class FavoriteEntry(
    val id: String,
    val name: String,
    val isFolder: Boolean,
)

// Result of a library search: matching artists, albums and tracks.
data class SearchResults(
    val artists: List<BrowseItem> = emptyList(),
    val albums: List<BrowseItem> = emptyList(),
    val tracks: List<BrowseTrack> = emptyList(),
)

// A track found while browsing an album, used to pick a replacement for
// the upcoming ("next") track in the queue.
data class BrowseTrack(
    val id: String,
    val title: String,
    val trackNum: Int,
    // Cover id for thumbnails; empty when unknown.
    val coverId: String = "",
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

    // Observable copy of playerName: the screen collects this so the name
    // updates the moment another player is picked (also when that player has
    // nothing queued and no other value on screen changes).
    val playerNameFlow = kotlinx.coroutines.flow.MutableStateFlow("No Player")

    @Volatile
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
            // Build the new list first and swap it in one go: clearing the
            // shared list in place let the menu (main thread) see an empty
            // or half-filled list while this runs on the IO thread.
            val fresh: MutableList<Pair<String, String>> = arrayListOf()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                val name = item.getString("name")
                val player_mac = item.getString("playerid")
                fresh.add(Pair(name, player_mac))
            }
            players = fresh

            println(players)
        }
    }

    fun setPlayer(name: String, mac: String){
        val previousMac = playerMac
        playerName = name
        playerNameFlow.value = name
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
        return safeCall("update", listOf("", "", "", "", "", "", "")) {
            // "tags:acly4K" asks for artist/coverid/album/year plus "K"
            // (artwork_url) directly on the status call. artwork_url is how
            // remote sources - internet radio, Spotify/Tidal-style plugins -
            // carry their own cover art; those tracks have no local coverid
            // and no real library track_id to look up via songinfo at all.
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMAC\", [\"status\", \"-\",1,\"tags:acly4K\"]]}"
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
            var artworkUrl = ""

            try {
                val resultLoop = getObject.getJSONArray("playlist_loop")
                val firstResult = resultLoop.getJSONObject(0)
                title = firstResult.getString("title")
                artist = firstResult.optString("artist", "")
                coverid = firstResult.optString("coverid", "")
                album = firstResult.optString("album", "")
                year = firstResult.optString("year", "")
                artworkUrl = firstResult.optString("artwork_url", "")
                val id = firstResult.optString("id", "")
                Log.w(TAG, "update: id=\"$id\" title=\"$title\" coverid=\"$coverid\" artwork_url=\"$artworkUrl\"")

                // The extra songinfo lookup is only meaningful (and only
                // valid to call) for a real library track - a plain,
                // non-negative numeric id. Remote/stream ids aren't real
                // track_ids and songinfo can't look them up; toIntOrNull()
                // safely skips those instead of throwing on id.toInt().
                val numericId = id.toIntOrNull()
                if (numericId != null && numericId >= 0) {
                    val res2 = getSongInfo(playerMAC, numericId)
                    Log.w(TAG, "update: getSongInfo returned ${res2.length()} entries: $res2")
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
                Log.w(TAG, "update: parsing playlist_loop/songinfo failed: ${e.message}")
                return@safeCall listOf(title, artist, coverid, portraitId, album, year, artworkUrl)
            }

            listOf(title, artist, coverid, portraitId, album, year, artworkUrl)
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
    // Plays or queues a whole album or a single track on the current player:
    //   cmd "load"   - replace the queue and start playing
    //   cmd "insert" - put it right after the current track
    //   cmd "add"    - append it to the end of the queue
    // Pass either albumId or trackId.
    suspend fun playlistControl(
        cmd: String,
        albumId: String? = null,
        trackId: String? = null,
        folderId: String? = null,
    ): Boolean {
        if (playerMac == "00:00:00:00:00:00") return false
        val target = when {
            albumId != null -> "album_id:$albumId"
            trackId != null -> "track_id:$trackId"
            folderId != null -> "folder_id:$folderId"
            else -> return false
        }
        return safeCall("playlistControl($cmd)", false) {
            if (cmd == "load") power(true)
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"playlistcontrol\",\"cmd:$cmd\",\"$target\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playlistControl(requestBody)
            true
        }
    }

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
    // toast without needlessly calling out a plugin to people who don't
    // use it.
    enum class ReplaceResult { SUGARCUBE, MIPSTER, RANDOMFLOW, DSTM, RANDOM, SKIPPED_QUEUE, NO_NEXT }

    // Outcome of a plugin's "replacenext" JSON-RPC call: whether the call
    // itself succeeded, and (only meaningful if it did) whether the next
    // track actually changed afterwards.
    private data class PluginReplaceAttempt(val ok: Boolean, val changed: Boolean)

    // Triggers SugarCube's or RandomFlow's own "replace next track" action
    // via its JSON-RPC command - both plugins expose the identical
    // ["<plugin>", "replacenext"] shape (only the plugin name differs), the
    // same action their own "Replace This Track" button fires, so we get
    // their music-similarity pick instead of a random track.
    //
    // Deliberately does NOT try to force the plugin's Chain/Auto Mix
    // setting on first: on both plugins, enabling that switches Don't Stop
    // The Music off as a side effect, and the app has no business silently
    // flipping a standing player setting (and losing the user's DSTM setup)
    // just because they tapped this button once. If the plugin silently
    // declines "replacenext" because Chain/Auto Mix happens to be off (or
    // the queue isn't in the exact shape it wants), that's simply reported
    // as "no change" below, and the caller falls through to DSTM/random
    // like any other no-op attempt.
    private suspend fun replaceNextViaPlugin(mac: String, pluginCommand: String): PluginReplaceAttempt {
        if (mac == "00:00:00:00:00:00") return PluginReplaceAttempt(ok = false, changed = false)

        return safeCall("replaceNextViaPlugin($pluginCommand)", PluginReplaceAttempt(ok = false, changed = false)) {
            val before = getUpcoming(mac, 2).getOrNull(1)

            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$mac\", [\"$pluginCommand\", \"replacenext\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.pluginReplaceNext(requestBody)

            // The plugin may need several seconds (e.g. a large MusicIP/
            // SugarCube request), and the response returning doesn't
            // guarantee the playlist change has landed yet. Poll for the
            // change for up to ~8s; stop as soon as it shows up. If the
            // plugin silently declined, this costs the full wait before
            // falling through to the next tier.
            var changed = false
            for (i in 0 until 16) {
                kotlinx.coroutines.delay(500)
                val after = getUpcoming(mac, 2).getOrNull(1)
                if (after != null && (after.title != before?.title || after.coverId != before?.coverId)) {
                    changed = true
                    break
                }
            }
            PluginReplaceAttempt(ok = true, changed = changed)
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

    // Native LMS fallback for when the chosen plugin isn't installed/
    // enabled, or didn't change anything: picks a random track, preferring
    // the current track's genre first, then its artist, then finally
    // anything in the library - and puts it in the "next" slot the same way
    // replaceNext() does.
    //
    // Only does this if "next" is the last track queued - same reasoning as
    // replaceNextViaDstm()'s tailLength check: if the user has an album or
    // a deliberate batch of tracks queued up, silently swapping out the
    // next one for something unrelated would break that sequence. SugarCube
    // and RandomFlow both already refuse to act in that situation; this
    // fallback needs the same guard, since it has no such judgment of its
    // own otherwise.
    private suspend fun replaceNextWithRandom(mac: String): Boolean {
        if (mac == "00:00:00:00:00:00") return false

        val queue = getUpcoming(mac, 3)
        val tailLength = queue.size - 1
        if (tailLength != 1) return false

        val (genreId, artistId) = getCurrentGenreArtist(mac)

        val trackId = (if (genreId.isNotEmpty()) randomTrackId("genre_id:$genreId") else null)
            ?: (if (artistId.isNotEmpty()) randomTrackId("artist_id:$artistId") else null)
            ?: randomTrackId(null)

        if (trackId != null) {
            replaceNext(mac, trackId)
            return true
        }
        return false
    }

    // Uses the Auto Mix plugin that is switched on for this player
    // (SugarCube, MIPster or Random Flow - see the Mix menu) for its
    // music-similarity pick, then gives DSTM a legitimate chance to fill the
    // gap via whichever provider is configured for this player, and only then
    // falls back to a genuinely random library track - so the button never
    // dead-ends, and people who don't use any of these never see them mentioned.
    // Name of the active plugin if it was tried but changed nothing during
    // the last replaceNextSmart() call, so the UI can tell the user to check
    // the plugin settings. Null otherwise.
    @Volatile
    var lastDeclinedPlugin: String? = null

    suspend fun replaceNextSmart(mac: String): ReplaceResult {
        lastDeclinedPlugin = null
        val active = getMixState().on.firstOrNull { it != MixPlugin.DSTM }
        when (active) {
            MixPlugin.SUGARCUBE -> {
                val attempt = replaceNextViaPlugin(mac, "sugarcube")
                if (attempt.changed) return ReplaceResult.SUGARCUBE
                if (attempt.ok) lastDeclinedPlugin = "SugarCube"
            }
            MixPlugin.MIPSTER -> {
                val attempt = replaceNextViaPlugin(mac, "mipster")
                if (attempt.changed) return ReplaceResult.MIPSTER
                if (attempt.ok) lastDeclinedPlugin = "MIPster"
            }
            MixPlugin.RANDOMFLOW -> {
                val attempt = replaceNextViaPlugin(mac, "randomflow")
                if (attempt.changed) return ReplaceResult.RANDOMFLOW
                if (attempt.ok) lastDeclinedPlugin = "Random Flow"
            }
            else -> { /* no plugin active: straight to DSTM */ }
        }

        if (replaceNextViaDstm(mac)) return ReplaceResult.DSTM

        if (replaceNextWithRandom(mac)) return ReplaceResult.RANDOM

        // Nothing replaced anything - most likely because there's an album
        // or a deliberate batch queued up (more than just "next" left to
        // play), which every tier above declines to disturb on purpose.
        if (getUpcoming(mac, 3).size <= 1) return ReplaceResult.NO_NEXT
        return ReplaceResult.SKIPPED_QUEUE
    }

    // --- Library browsing (artist -> album -> track), used to pick the
    // replacement for the "next" track. ---

    // Library-wide search (artists, albums, tracks matching the text).
    // Lyrion pages through one combined list (artists first, then albums,
    // then tracks), so a small count would be filled by artists alone -
    // ask for plenty so albums and tracks make it into the response too.
    suspend fun search(term: String, count: Int = 300): SearchResults {
        return safeCall("search", SearchResults()) {
            val params = JSONArray().put("").put(
                JSONArray().put("search").put(0).put(count).put("term:$term")
            )
            val reqString = JSONObject().put("method", "slim.request").put("params", params).toString()
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.search(requestBody)
            val jsonData: String? = res.body()?.string()
            val result = JSONObject(jsonData).getJSONObject("result")

            val artists = mutableListOf<BrowseItem>()
            val loopA = result.optJSONArray("contributors_loop") ?: JSONArray()
            for (i in 0 until loopA.length()) {
                val o = loopA.getJSONObject(i)
                artists.add(BrowseItem(o.optString("contributor_id"), o.optString("contributor")))
            }
            val albums = mutableListOf<BrowseItem>()
            val loopB = result.optJSONArray("albums_loop") ?: JSONArray()
            for (i in 0 until loopB.length()) {
                val o = loopB.getJSONObject(i)
                albums.add(BrowseItem(o.optString("album_id"), o.optString("album")))
            }
            val tracks = mutableListOf<BrowseTrack>()
            val loopC = result.optJSONArray("tracks_loop") ?: JSONArray()
            for (i in 0 until loopC.length()) {
                val o = loopC.getJSONObject(i)
                tracks.add(BrowseTrack(o.optString("track_id"), o.optString("track"), 0))
            }

            // Thumbnails: the search command carries no artwork info, so ask
            // for it separately with the same term and match by id. If either
            // lookup fails the rows simply have no picture.
            val artworkByAlbum = try {
                val loop = rpcLoop(listOf("albums", 0, count, "search:$term", "tags:lj"), "albums_loop")
                (0 until loop.length()).associate {
                    val o = loop.getJSONObject(it)
                    o.optString("id") to o.optString("artwork_track_id")
                }
            } catch (e: Exception) { emptyMap() }
            val coverByTrack = try {
                val loop = rpcLoop(listOf("titles", 0, count, "search:$term", "tags:c"), "titles_loop")
                (0 until loop.length()).associate {
                    val o = loop.getJSONObject(it)
                    o.optString("id") to o.optString("coverid")
                }
            } catch (e: Exception) { emptyMap() }

            val albumsOut = albums.map { it.copy(artworkId = artworkByAlbum[it.id] ?: "") }
            val tracksOut = tracks.map { it.copy(coverId = coverByTrack[it.id] ?: "") }
            SearchResults(artists, albumsOut, tracksOut)
        }
    }

    // --- Repeat / shuffle / Don't Stop The Music state of the current player ---

    // Last Don't Stop The Music provider seen on this player (so the repeat
    // button can switch it back on after switching it off).
    @Volatile
    var lastDstmProvider: String = ""

    private suspend fun rpcPlayer(mac: String, command: List<Any>): JSONObject {
        val cmd = JSONArray()
        command.forEach { cmd.put(it) }
        val params = JSONArray().put(mac).put(cmd)
        val reqString = JSONObject().put("method", "slim.request").put("params", params).toString()
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        val res = lmsApi.rpc(requestBody)
        val jsonData: String? = res.body()?.string()
        return JSONObject(jsonData).optJSONObject("result") ?: JSONObject()
    }

    suspend fun getPlayModes(): PlayModes? {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return null
        return safeCall("getPlayModes", null) {
            val status = rpcPlayer(mac, listOf("status", "-", 1))
            val dstm = rpcPlayer(mac, listOf("playerpref", "plugin.dontstopthemusic:provider", "?"))
            val provider = dstm.optString("_p2", "")
            if (provider.isNotBlank() && provider != "0") lastDstmProvider = provider
            PlayModes(
                repeat = status.optInt("playlist repeat", 0),
                shuffle = status.optInt("playlist shuffle", 0),
                dstmProvider = if (provider == "0") "" else provider,
            )
        }
    }

    // mode: 0 = off, 1 = repeat the current song, 2 = repeat the whole queue
    suspend fun setRepeat(mode: Int) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("setRepeat", Unit) { rpcPlayer(mac, listOf("playlist", "repeat", mode)); Unit }
    }

    // mode: 0 = off, 1 = shuffle songs, 2 = shuffle albums (releases)
    suspend fun setShuffle(mode: Int) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("setShuffle", Unit) { rpcPlayer(mac, listOf("playlist", "shuffle", mode)); Unit }
    }

    // The Don't Stop The Music source to switch on: the last one seen on this
    // player, or else the first one the plugin offers (it lists its own
    // sources, including those of SugarCube / Random Flow, via the same
    // "dontstopthemusicsetting" command its settings menu uses). Empty if
    // there is none.
    suspend fun ensureDstmProvider(): String {
        if (lastDstmProvider.isNotBlank()) return lastDstmProvider
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return ""
        return safeCall("ensureDstmProvider", "") {
            val res = rpcPlayer(mac, listOf("dontstopthemusicsetting"))
            val loop = res.optJSONArray("item_loop") ?: JSONArray()
            for (i in 0 until loop.length()) {
                val cmd = loop.getJSONObject(i).optJSONObject("actions")
                    ?.optJSONObject("do")?.optJSONArray("cmd") ?: continue
                val token = cmd.optString(2, "")
                if (token.isNotBlank() && token != "0") {
                    lastDstmProvider = token
                    return@safeCall token
                }
            }
            ""
        }
    }

    // --- Auto Mix plugins (SugarCube, MIPster, Random Flow, Don't Stop The Music) ---

    // Random Flow keeps its "mix running" state on the sync-group master,
    // so commands/queries for it must use the master's id.
    private suspend fun masterMac(mac: String): String {
        val status = rpcPlayer(mac, listOf("status", "-", 1))
        return status.optString("sync_master", "").ifBlank { mac }
    }

    // The player (mac) for which this app started a Random Mix and has not
    // switched it off since.
    @Volatile
    private var randomMixMac: String? = null

    private suspend fun playerPref(mac: String, name: String): String =
        rpcPlayer(mac, listOf("playerpref", name, "?")).optString("_p2", "")

    // Which Auto Mix plugins exist on the server and which one is on for
    // the current player. A plugin counts as available when its setting
    // can be read.
    suspend fun getMixState(): MixState {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return MixState()
        return safeCall("getMixState", MixState()) {
            val available = mutableSetOf<MixPlugin>()
            val on = mutableSetOf<MixPlugin>()

            val sc = playerPref(mac, "plugin.SugarCube:sugarcube_status")
            if (sc.isNotBlank()) { available.add(MixPlugin.SUGARCUBE); if (sc == "1") on.add(MixPlugin.SUGARCUBE) }

            val mip = playerPref(mac, "plugin.mipster:chainStatus")
            if (mip.isNotBlank()) { available.add(MixPlugin.MIPSTER); if (mip == "1") on.add(MixPlugin.MIPSTER) }

            val rf = rpcPlayer(masterMac(mac), listOf("randomflow", "mixsettings"))
            if (rf.has("mixRunning")) {
                available.add(MixPlugin.RANDOMFLOW)
                if (rf.optString("mixRunning").lowercase() in listOf("1", "true")) on.add(MixPlugin.RANDOMFLOW)
            }

            // Lyrion's own Random Mix: "randomplayisactive" answers with the
            // running mix type (empty when none); no answer = plugin missing.
            val rp = rpcPlayer(mac, listOf("randomplayisactive"))
            if (rp.has("_randomplayisactive")) {
                available.add(MixPlugin.RANDOMPLAY)
                val type = rp.optString("_randomplayisactive", "")
                if ((type.isNotBlank() && type != "0" && type != "null") || randomMixMac == mac) {
                    on.add(MixPlugin.RANDOMPLAY)
                }
            }

            val dstmMenu = rpcPlayer(mac, listOf("dontstopthemusicsetting"))
            if ((dstmMenu.optJSONArray("item_loop")?.length() ?: 0) > 1) {
                available.add(MixPlugin.DSTM)
                val provider = playerPref(mac, "plugin.dontstopthemusic:provider")
                if (provider.isNotBlank() && provider != "0") {
                    lastDstmProvider = provider
                    on.add(MixPlugin.DSTM)
                }
            }
            MixState(available, on)
        }
    }

    // The whole queue of the current player (for the Queue panel).
    suspend fun getQueue(count: Int = 500): QueueList {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return QueueList()
        return safeCall("getQueue", QueueList()) {
            val status = rpcPlayer(mac, listOf("status", 0, count, "tags:ac"))
            val loop = status.optJSONArray("playlist_loop") ?: JSONArray()
            val tracks = (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                QueueTrack(
                    absoluteIndex = o.optInt("playlist index", it),
                    title = o.optString("title", ""),
                    artist = o.optString("artist", ""),
                    coverId = o.optString("coverid", ""),
                )
            }
            QueueList(tracks, status.optInt("playlist_cur_index", 0))
        }
    }

    // Removes one track (by its position in the queue) from the queue.
    suspend fun removeFromQueue(index: Int) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("removeFromQueue", Unit) { rpcPlayer(mac, listOf("playlist", "delete", index)); Unit }
    }

    // Whether "replace next track" makes sense right now: not while a stream
    // plays, and not while a batch runs (the same rule the Auto Mix plugins
    // use: more than 2 tracks left after the current position = a batch).
    suspend fun canReplaceNext(): Boolean {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return false
        return safeCall("canReplaceNext", true) {
            val status = rpcPlayer(mac, listOf("status", "-", 1, "tags:"))
            val item = status.optJSONArray("playlist_loop")?.optJSONObject(0)
            val isStream = item != null && (item.optInt("remote", 0) == 1 || item.optLong("id", 0L) < 0L)
            val remaining = status.optInt("playlist_tracks", 0) - status.optInt("playlist_cur_index", 0)
            !isStream && remaining <= 2
        }
    }

    // The Auto Mix plugin that is switched on, but only while a stream
    // (internet radio etc.) is playing - that is when a mix has to be
    // started by hand. Null otherwise (also when only DSTM is on).
    suspend fun getStreamMixPlugin(): MixPlugin? {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return null
        return safeCall("getStreamMixPlugin", null) {
            val status = rpcPlayer(mac, listOf("status", "-", 1, "tags:"))
            val item = status.optJSONArray("playlist_loop")?.optJSONObject(0) ?: return@safeCall null
            val isStream = item.optInt("remote", 0) == 1 || item.optLong("id", 0L) < 0L
            if (!isStream) return@safeCall null
            getMixState().on.firstOrNull { it != MixPlugin.DSTM && it != MixPlugin.RANDOMPLAY }
        }
    }

    private suspend fun applyMixPlugin(plugin: MixPlugin, enable: Boolean) {
        val mac = playerMac
        val flag = if (enable) "1" else "0"
        when (plugin) {
            MixPlugin.SUGARCUBE -> rpcPlayer(mac, listOf("sugarcube", "setting", "sugarcube_status:$flag"))
            MixPlugin.MIPSTER -> rpcPlayer(mac, listOf("mipster", "setting", "chainStatus:$flag"))
            MixPlugin.RANDOMFLOW -> rpcPlayer(masterMac(mac), listOf("randomflow", "setautomix", flag))
            // Switching Random Mix on starts a mix of random songs right away
            MixPlugin.RANDOMPLAY -> {
                rpcPlayer(mac, listOf("randomplay", if (enable) "tracks" else "disable"))
                // Lyrion doesn't report a mix that was started this way as
                // active, so the app keeps track of it itself.
                randomMixMac = if (enable) mac else null
            }
            MixPlugin.DSTM -> {
                val provider = if (enable) ensureDstmProvider() else "0"
                if (provider.isNotBlank()) {
                    rpcPlayer(mac, listOf("playerpref", "plugin.dontstopthemusic:provider", provider))
                }
            }
        }
    }

    // Switches one plugin on or off. Switching a mix plugin on first switches
    // the other mix plugins that are on off, so only one keeps the queue
    // filled. Don't Stop The Music is left alone in both directions: Lyrion
    // has a hook (canConflictWithDSTM) that makes DSTM stand aside while a
    // mix plugin is mixing, so DSTM keeps its own setting.
    suspend fun setMixPlugin(plugin: MixPlugin, enable: Boolean, current: MixState) {
        if (playerMac == "00:00:00:00:00:00") return
        safeCall("setMixPlugin", Unit) {
            if (enable && plugin != MixPlugin.DSTM) {
                for (other in current.on) {
                    if (other != plugin && other != MixPlugin.DSTM) applyMixPlugin(other, false)
                }
            }
            applyMixPlugin(plugin, enable)
        }
    }

    // Starts a new mix / starts a batch / adds a batch with the given
    // plugin (not available for Don't Stop The Music).
    suspend fun mixAction(plugin: MixPlugin, action: MixAction) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("mixAction", Unit) {
            when (plugin) {
                MixPlugin.SUGARCUBE -> rpcPlayer(mac, listOf("sugarcube", when (action) {
                    MixAction.START_MIX -> "startchain"
                    MixAction.START_BATCH -> "startbatch"
                    MixAction.ADD_BATCH -> "addbatch"
                }))
                MixPlugin.MIPSTER -> rpcPlayer(mac, when (action) {
                    MixAction.START_MIX -> listOf("mipster", "setting", "startChain:1")
                    MixAction.START_BATCH -> listOf("mipster", "batch", "mood", "none", 0, "play")
                    MixAction.ADD_BATCH -> listOf("mipster", "batch", "mood", "none", 0, "add")
                })
                MixPlugin.RANDOMFLOW -> rpcPlayer(masterMac(mac), listOf("randomflow", when (action) {
                    MixAction.START_MIX -> "startmix"
                    MixAction.START_BATCH -> "startbatch"
                    MixAction.ADD_BATCH -> "topup"
                }))
                MixPlugin.RANDOMPLAY ->
                    if (action == MixAction.START_MIX) rpcPlayer(mac, listOf("randomplay", "tracks"))
                MixPlugin.DSTM -> Unit
            }
            Unit
        }
    }

    // Provider "0" switches Don't Stop The Music off for this player.
    suspend fun setDstmProvider(provider: String) {
        val mac = playerMac
        if (mac == "00:00:00:00:00:00") return
        safeCall("setDstmProvider", Unit) {
            rpcPlayer(mac, listOf("playerpref", "plugin.dontstopthemusic:provider", provider)); Unit
        }
    }

    // One level of the favorites. An empty parentId means the top level.
    suspend fun getFavorites(parentId: String, count: Int = 1000): List<FavoriteEntry> {
        return safeCall("getFavorites", emptyList()) {
            val cmd = mutableListOf<Any>("favorites", "items", 0, count, "want_url:1")
            if (parentId.isNotBlank()) cmd.add("item_id:$parentId")
            val loop = rpcLoop(cmd, "loop_loop")
            (0 until loop.length()).mapNotNull {
                val o = loop.getJSONObject(it)
                val id = o.optString("id")
                val name = o.optString("name")
                if (id.isBlank() || name.isBlank()) null
                else FavoriteEntry(id, name, o.optInt("hasitems", 0) > 0)
            }
        }
    }

    // Plays or queues a favorite (or a whole folder of them) on the current player:
    //   cmd "load" (replace the queue and play), "insert" (play next), "add" (at the end)
    suspend fun favoritesControl(cmd: String, itemId: String): Boolean {
        if (playerMac == "00:00:00:00:00:00") return false
        return safeCall("favoritesControl($cmd)", false) {
            if (cmd == "load") power(true)
            val lmsCmd = if (cmd == "load") "play" else cmd
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"$playerMac\", [\"favorites\",\"playlist\",\"$lmsCmd\",\"item_id:$itemId\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            lmsApi.playlistControl(requestBody)
            true
        }
    }

    suspend fun getGenres(count: Int = 2000): List<BrowseItem> {
        return safeCall("getGenres", emptyList()) {
            val loop = rpcLoop(listOf("genres", 0, count), "genres_loop")
            (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                BrowseItem(o.optString("id"), o.optString("genre"))
            }
        }
    }

    suspend fun getArtistsByGenre(genreId: String, count: Int = 5000): List<BrowseItem> {
        return safeCall("getArtistsByGenre", emptyList()) {
            val loop = rpcLoop(listOf("artists", 0, count, "genre_id:$genreId"), "artists_loop")
            (0 until loop.length()).map {
                val o = loop.getJSONObject(it)
                BrowseItem(o.optString("id"), o.optString("artist"))
            }
        }
    }

    // One level of the music folder tree. An empty folderId means the top level.
    suspend fun getFolder(folderId: String, count: Int = 2000): List<FolderEntry> {
        return safeCall("getFolder", emptyList()) {
            val cmd = mutableListOf<Any>("musicfolder", 0, count)
            if (folderId.isNotBlank()) cmd.add("folder_id:$folderId")
            val loop = rpcLoop(cmd, "folder_loop")
            (0 until loop.length()).mapNotNull {
                val o = loop.getJSONObject(it)
                val type = o.optString("type")
                if (type == "folder" || type == "track") {
                    FolderEntry(o.optString("id"), o.optString("filename"), type == "folder")
                } else null
            }
        }
    }

    // Sends one server-wide (no player) JSON-RPC query and returns the named result loop.
    private suspend fun rpcLoop(command: List<Any>, loopName: String): JSONArray {
        val cmd = JSONArray()
        command.forEach { cmd.put(it) }
        val params = JSONArray().put("").put(cmd)
        val reqString = JSONObject().put("method", "slim.request").put("params", params).toString()
        val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
        val res = lmsApi.search(requestBody)
        val jsonData: String? = res.body()?.string()
        return JSONObject(jsonData).getJSONObject("result").optJSONArray(loopName) ?: JSONArray()
    }

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
            val reqString = "{\"method\": \"slim.request\", \"params\": [\"\", [\"albums\", 0, $count, \"artist_id:$artistId\", \"sort:yearalbum\", \"tags:lj\"]]}"
            val requestBody = reqString.toRequestBody("application/json".toMediaTypeOrNull())
            val res = lmsApi.browseAlbums(requestBody)
            val jsonData: String? = res.body()?.string()
            val obj = JSONObject(jsonData)
            val getObject = obj.getJSONObject("result")
            val loop = getObject.optJSONArray("albums_loop") ?: JSONArray()
            val list = mutableListOf<BrowseItem>()
            for (i in 0 until loop.length()) {
                val item = loop.getJSONObject(i)
                list.add(BrowseItem(item.optString("id"), item.optString("album"), item.optString("artwork_track_id")))
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
