package com.saanjh.music

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale

data class Song(val id: Long, val title: String, val artist: String, val uri: Uri, val duration: Long)

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 9, 13)
    private val panel = Color.rgb(20, 22, 31)
    private val accent = Color.rgb(182, 156, 255)
    private var songs = mutableListOf<Song>()
    private var shown = mutableListOf<Song>()
    private var player: MediaPlayer? = null
    private var current = -1
    private var shuffle = false
    private var repeat = false
    private lateinit var list: LinearLayout
    private lateinit var titleNow: TextView
    private lateinit var artistNow: TextView
    private lateinit var playButton: Button
    private lateinit var status: TextView
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) scanAllMusic() else status.text = "Allow audio access or choose a folder."
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            scanFolder(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) scanAllMusic()
        else permission.launch(p)
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(label: String, primary: Boolean = false, action: () -> Unit): Button =
        Button(this).apply {
            text = label; isAllCaps = false; textSize = 13f
            setTextColor(if (primary) bg else Color.WHITE)
            background = rounded(if (primary) accent else Color.rgb(39, 35, 53), 28f)
            setOnClickListener { action() }
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg)
            setPadding(20, 18, 20, 12)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = text("Saanjh", 25f, Color.WHITE, true)
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("↻") { scanAllMusic() })
        root.addView(header)
        root.addView(text("OFFLINE MUSIC PLAYER", 10f, accent, true).apply { letterSpacing = .18f })
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(18, 18, 18, 18)
            background = rounded(Color.rgb(25, 22, 39), 28f)
        }
        val h = text("Your music, your space.", 21f, Color.WHITE, true)
        hero.addView(h)
        hero.addView(text("Play music stored on your phone. No streaming, no account.", 12f, Color.LTGRAY))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actions.addView(button("Select folder", true) { folderPicker.launch(null) })
        actions.addView(button("Scan all songs") { scanAllMusic() })
        hero.addView(actions)
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 18; bottomMargin = 14 })
        status = text("Choose a folder or scan your music.", 12f, Color.LTGRAY)
        root.addView(status)
        root.addView(text("YOUR SONGS", 15f, Color.WHITE, true).apply { setPadding(0, 14, 0, 8) })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val now = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12)
            background = rounded(panel, 20f)
        }
        titleNow = text("Nothing playing", 14f, Color.WHITE, true)
        artistNow = text("Select music to get started", 11f, Color.LTGRAY)
        now.addView(titleNow); now.addView(artistNow)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        controls.addView(button("⏮") { previous() })
        playButton = button("▶", true) { togglePlay() }
        controls.addView(playButton)
        controls.addView(button("⏭") { next() })
        controls.addView(button("Shuffle") { shuffle = !shuffle; status.text = if (shuffle) "Shuffle on" else "Shuffle off" })
        controls.addView(button("Repeat") { repeat = !repeat; status.text = if (repeat) "Repeat on" else "Repeat off" })
        now.addView(controls)
        root.addView(now, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 })
        setContentView(root)
    }

    private fun scanAllMusic() {
        songs.clear()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            while (c.moveToNext()) addSong(c, collection)
        }
        renderSongs()
    }

    private fun scanFolder(tree: Uri) {
        songs.clear()
        val children = Uri.parse(tree.toString().trimEnd('/') + "/children")
        // SAF providers expose folder contents through DocumentsContract; recursively query document IDs.
        try {
            val rootId = android.provider.DocumentsContract.getTreeDocumentId(tree)
            scanDocumentTree(tree, rootId)
        } catch (_: Exception) {
            status.text = "Couldn't read this folder. Try Scan all songs."
        }
        renderSongs()
    }

    private fun scanDocumentTree(tree: Uri, documentId: String) {
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val projection = arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
        contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                val id = c.getString(idCol); val name = c.getString(nameCol); val mime = c.getString(mimeCol)
                if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) scanDocumentTree(tree, id)
                else if (mime.startsWith("audio/") || name.matches(Regex("(?i).+\\.(mp3|m4a|aac|wav|ogg|flac|opus)$"))) {
                    val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    songs.add(Song(id.hashCode().toLong(), name.substringBeforeLast('.', name), "Local audio", uri, 0))
                }
            }
        }
    }

    private fun addSong(c: Cursor, collection: Uri) {
        val id = c.getLong(0)
        songs.add(Song(id, c.getString(1) ?: "Unknown title", c.getString(2) ?: "Unknown artist",
            Uri.withAppendedPath(collection, id.toString()), c.getLong(3)))
    }

    private fun renderSongs() {
        list.removeAllViews()
        shown = songs.toMutableList()
        status.text = "${songs.size} song(s) found"
        if (songs.isEmpty()) {
            list.addView(text("No music found. Choose a folder containing audio files.", 13f, Color.LTGRAY).apply { setPadding(8, 24, 8, 24) })
        }
        songs.forEachIndexed { index, song ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(10, 12, 10, 12); background = rounded(panel, 16f)
                setOnClickListener { play(index) }
            }
            val icon = text("♫", 22f, accent, true).apply { gravity = Gravity.CENTER; background = rounded(Color.rgb(40, 34, 59), 14f) }
            row.addView(icon, LinearLayout.LayoutParams(46, 46))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 0, 0, 0) }
            info.addView(text(song.title, 14f, Color.WHITE, true))
            info.addView(text(song.artist, 11f, Color.LTGRAY))
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 7 })
        }
    }

    private fun play(index: Int) {
        if (songs.isEmpty()) return
        current = index.coerceIn(0, songs.lastIndex)
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, songs[current].uri)
                setOnPreparedListener { it.start(); playButton.text = "Ⅱ" }
                setOnCompletionListener { if (repeat) play(current) else next() }
                setOnErrorListener { _, _, _ -> status.text = "Unable to play this audio file"; true }
                prepareAsync()
            }
            titleNow.text = songs[current].title
            artistNow.text = songs[current].artist
            playButton.text = "…"
        } catch (_: Exception) { status.text = "Could not open this song." }
    }

    private fun togglePlay() {
        val p = player
        if (p == null) { if (songs.isNotEmpty()) play(0); return }
        if (p.isPlaying) { p.pause(); playButton.text = "▶" }
        else { p.start(); playButton.text = "Ⅱ" }
    }
    private fun next() { if (songs.isNotEmpty()) play(if (shuffle) (songs.indices).random() else (current + 1) % songs.size) }
    private fun previous() { if (songs.isNotEmpty()) play(if (current <= 0) songs.lastIndex else current - 1) }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
