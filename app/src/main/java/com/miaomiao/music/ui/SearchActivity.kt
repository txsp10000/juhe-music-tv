package com.miaomiao.music.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.FragmentActivity
import com.miaomiao.music.R
import com.miaomiao.music.databinding.ActivitySearchBinding
import com.miaomiao.music.model.SearchHistoryItem
import com.miaomiao.music.model.SearchHistoryManager

class SearchActivity : FragmentActivity() {
    private lateinit var binding: ActivitySearchBinding
    private val historyItems = mutableListOf<SearchHistoryItem>()
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyAdapter = HistoryAdapter(
            items = historyItems,
            onClick = { keyword ->
                startSearch(keyword)
            },
            onLongPress = { position ->
                if (position in historyItems.indices) {
                    val item = historyItems[position]
                    val customView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null)
                    val tvMessage = customView.findViewById<android.widget.TextView>(R.id.tv_confirm_message)
                    val tvOk = customView.findViewById<android.widget.TextView>(R.id.tv_confirm_ok)
                    tvMessage.text = "确定要删除「${item.keyword}」吗？"
                    tvOk.text = "删除"
                    val dialog = AlertDialog.Builder(this)
                        .setView(customView)
                        .create()
                    tvOk.setOnClickListener {
                        dialog.dismiss()
                        SearchHistoryManager.delete(this, item.keyword)
                        historyItems.removeAt(position)
                        historyAdapter.notifyItemRemoved(position)
                        historyAdapter.notifyItemRangeChanged(position, historyItems.size)
                        updateVisibility()
                    }
                    dialog.show()
                    tvOk.requestFocus()
                }
            }
        )
        binding.rvHistory.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter

        loadHistory()

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val keyword = binding.etSearch.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    startSearch(keyword)
                }
                true
            } else false
        }
    }

    private fun loadHistory() {
        historyItems.clear()
        historyItems.addAll(SearchHistoryManager.load(this))
        updateVisibility()
        historyAdapter.notifyDataSetChanged()
    }

    private fun updateVisibility() {
        if (historyItems.isEmpty()) {
            binding.tvNoHistory.visibility = View.VISIBLE
            binding.rvHistory.visibility = View.GONE
        } else {
            binding.tvNoHistory.visibility = View.GONE
            binding.rvHistory.visibility = View.VISIBLE
        }
    }

    private fun startSearch(keyword: String) {
        SearchHistoryManager.save(this, keyword)
        val intent = Intent(this, SearchResultActivity::class.java)
        intent.putExtra("keyword", keyword)
        startActivity(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }
}
