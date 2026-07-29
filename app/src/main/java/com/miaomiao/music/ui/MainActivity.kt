package com.miaomiao.music.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.miaomiao.music.R
import com.miaomiao.music.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class MainActivity : FragmentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main)

    private val pinnedPlaylists = mutableListOf<String>()

    private val playlistCategories = linkedMapOf(
        "语种" to listOf("华语", "欧美", "日语", "韩语", "粤语", "小语种", "闽南语"),
        "风格" to listOf("流行", "嘻哈说唱", "喊麦", "电子", "轻音乐", "慢摇DJ", "民谣", "摇滚", "国风", "古风", "另类/独立", "实验", "民族歌曲", "原声带", "世界音乐", "二次元", "节奏布鲁斯", "戏曲", "古典", "金属", "新世纪", "儿童音乐", "爵士", "蓝调", "乡村", "雷鬼", "拉丁音乐", "舞曲", "网络歌曲", "纯音乐", "交响乐", "朋克", "后摇", "迷幻"),
        "榜单" to listOf("热歌榜", "新歌榜", "飙升榜", "原创榜"),
        "场景" to listOf("清晨", "夜晚", "起床", "助眠", "学习", "工作", "运动", "驾车", "约会", "小酒馆", "KTV", "游戏直播", "咖啡厅", "瑜伽", "冥想", "下午茶", "散步", "洗澡"),
        "心情" to listOf("伤感", "怀旧", "浪漫", "治愈", "安静", "励志", "快乐", "感动", "孤独", "思念", "放松", "慵懒", "甜蜜", "清新", "热血", "空灵"),
        "主题" to listOf("影视原声", "餐厅", "旅行", "派对", "婚礼", "童年", "青春", "毕业", "圣诞", "新年", "情人节", "生日", "秋天", "冬天", "春天", "夏天")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadPinnedPlaylists()
        PlayerManager.init(applicationContext)
        registerPlayerCallbacks()
        setupTopBar()
        setupFuncStrip()
        setupPlaylists()
    }

    override fun onResume() {
        super.onResume()
        registerPlayerCallbacks()
        syncUI()
    }

    private fun loadPinnedPlaylists() {
        val prefs = getSharedPreferences("playlist_pins", Context.MODE_PRIVATE)
        val json = prefs.getString("list", "[]") ?: "[]"
        pinnedPlaylists.clear()
        if (json.length > 2) {
            json.removeSurrounding("[", "]").split("\",\"").forEach {
                pinnedPlaylists.add(it.removeSurrounding("\""))
            }
        }
    }

    private fun savePinnedPlaylists() {
        val json = pinnedPlaylists.joinToString(",") { "\"$it\"" }
        val prefs = getSharedPreferences("playlist_pins", Context.MODE_PRIVATE)
        prefs.edit().putString("list", "[$json]").apply()
    }

    private fun setupTopBar() {
        binding.searchBoxArea.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
    }

    private fun setupFuncStrip() {
        binding.btnGotoPlayer.setOnClickListener {
            if (PlayerManager.currentSong != null) {
                startActivity(Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            } else {
                Toast.makeText(this, "暂无播放", Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.CENTER, 0, 0)
                    show()
                }
            }
        }

        binding.btnFavorites.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }

        binding.btnRandomPlay.setOnClickListener {
            if (pinnedPlaylists.isEmpty()) {
                Toast.makeText(this, "请先置顶一些歌单", Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.CENTER, 0, 0)
                    show()
                }
                return@setOnClickListener
            }
            val pick = pinnedPlaylists.random()
            val intent = Intent(this, SearchResultActivity::class.java)
            intent.putExtra("keyword", "$pick歌单")
            startActivity(intent)
        }
    }

    private fun setupPlaylists() {
        val container = binding.playlistContainer
        container.removeAllViews()
        val density = resources.displayMetrics.density

        container.addView(makeSectionTitle("已置顶", density, topMargin = 0))
        val pinnedItems = pinnedPlaylists.map { makePinnedChip(it, density) } + listOf(makeAddChip(density))
        for (rowViews in pinnedItems.chunked(10)) {
            val row = makeFlowRow(density)
            for (v in rowViews) row.addView(v)
            container.addView(row)
        }

        for ((category, playlists) in playlistCategories) {
            container.addView(makeSectionTitle(category, density))
            for (rowItems in playlists.chunked(10)) {
                val row = makeFlowRow(density)
                for (name in rowItems) row.addView(makeCatChip(name, density))
                container.addView(row)
            }
        }
    }

    private fun makeFlowRow(density: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun makeSectionTitle(text: String, density: Float, topMargin: Int = 20): TextView {
        val top = (topMargin * density).toInt()
        val bottom = (10 * density).toInt()
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#FFF0F0F5"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, top, 0, bottom) }
        }
    }

    private fun makePinnedChip(name: String, density: Float): TextView {
        val hPad = (14 * density).toInt()
        val vPad = (7 * density).toInt()
        return TextView(this).apply {
            text = "📌 $name"
            setTextColor(Color.parseColor("#FF6C8CFF"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(hPad, vPad, hPad, vPad)
            setBackgroundResource(R.drawable.bg_pinned_chip)
            isFocusable = true
            isFocusableInTouchMode = true
            isLongClickable = true
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = (8 * density).toInt()
            params.bottomMargin = (8 * density).toInt()
            layoutParams = params

            setOnClickListener {
                val intent = Intent(this@MainActivity, SearchResultActivity::class.java)
                intent.putExtra("keyword", "$name歌单")
                startActivity(intent)
            }

            setOnLongClickListener {
                showDeleteDialog(name)
                true
            }
        }
    }

    private fun makeAddChip(density: Float): TextView {
        val hPad = (14 * density).toInt()
        val vPad = (7 * density).toInt()
        return TextView(this).apply {
            text = "+ 添加"
            setTextColor(Color.parseColor("#FF6C8CFF"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(hPad, vPad, hPad, vPad)
            setBackgroundResource(R.drawable.bg_add_chip)
            isFocusable = true
            isFocusableInTouchMode = true
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = (8 * density).toInt()
            params.bottomMargin = (8 * density).toInt()
            layoutParams = params

            setOnClickListener { showAddDialog() }
        }
    }

    private fun makeCatChip(name: String, density: Float): TextView {
        val hPad = (14 * density).toInt()
        val vPad = (6 * density).toInt()
        return TextView(this).apply {
            text = name
            setTextColor(Color.parseColor("#FFF0F0F5"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(hPad, vPad, hPad, vPad)
            setBackgroundResource(R.drawable.bg_cat_chip)
            isFocusable = true
            isFocusableInTouchMode = true
            isLongClickable = true
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = (8 * density).toInt()
            params.bottomMargin = (8 * density).toInt()
            layoutParams = params

            setOnClickListener {
                val intent = Intent(this@MainActivity, SearchResultActivity::class.java)
                intent.putExtra("keyword", "$name歌单")
                startActivity(intent)
            }

            setOnLongClickListener {
                if (!pinnedPlaylists.contains(name)) {
                    pinnedPlaylists.add(name)
                    savePinnedPlaylists()
                    setupPlaylists()
                    Toast.makeText(this@MainActivity, "已置顶「$name」", Toast.LENGTH_SHORT).apply {
                        setGravity(Gravity.CENTER, 0, 0)
                        show()
                    }
                }
                true
            }
        }
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_playlist, null)
        val input = view.findViewById<EditText>(R.id.et_playlist_name)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.tv_add_confirm).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isNotBlank() && !pinnedPlaylists.contains(name)) {
                pinnedPlaylists.add(name)
                savePinnedPlaylists()
                setupPlaylists()
                Toast.makeText(this@MainActivity, "已添加「$name」", Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.CENTER, 0, 0)
                    show()
                }
                dialog.dismiss()
            } else if (pinnedPlaylists.contains(name)) {
                Toast.makeText(this@MainActivity, "该歌单已存在", Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.CENTER, 0, 0)
                    show()
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteDialog(name: String) {
        val view = layoutInflater.inflate(R.layout.dialog_delete_playlist, null)
        view.findViewById<TextView>(R.id.tv_delete_message).text = "确定要删除「$name」吗？"
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.tv_delete_confirm).setOnClickListener {
            pinnedPlaylists.remove(name)
            savePinnedPlaylists()
            setupPlaylists()
            Toast.makeText(this@MainActivity, "已删除「$name」", Toast.LENGTH_SHORT).apply {
                setGravity(Gravity.CENTER, 0, 0)
                show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun registerPlayerCallbacks() {
        PlayerManager.onStateChanged = { _, _ -> runOnUiThread { syncUI() } }
        PlayerManager.onProgress = null
        PlayerManager.onError = null
        PlayerManager.onLyric = null
    }

    private fun syncUI() {
        val song = PlayerManager.currentSong
        if (song != null) {
            binding.tvNowPlayingInfo.text = "${song.name} - ${song.artist}"
        } else {
            binding.tvNowPlayingInfo.text = "暂无播放"
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val view = layoutInflater.inflate(R.layout.dialog_exit, null)
            val dialog = AlertDialog.Builder(this)
                .setView(view)
                .create()

            view.findViewById<android.widget.TextView>(R.id.tv_exit_app)
                .setOnClickListener { finishAffinity(); System.exit(0) }
            view.findViewById<android.widget.TextView>(R.id.tv_background_play)
                .setOnClickListener { dialog.dismiss(); moveTaskToBack(true) }

            dialog.show()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { PlayerManager.togglePlayPause(); return true }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) { PlayerManager.next(); return true }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { PlayerManager.prev(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        clearPlayerCallbacks()
    }

    private fun clearPlayerCallbacks() {
        PlayerManager.onStateChanged = null
        PlayerManager.onProgress = null
        PlayerManager.onError = null
        PlayerManager.onLyric = null
    }

    override fun onDestroy() {
        super.onDestroy()
        clearPlayerCallbacks()
        scope.cancel()
        if (isFinishing) {
            PlayerManager.release()
        }
    }
}
