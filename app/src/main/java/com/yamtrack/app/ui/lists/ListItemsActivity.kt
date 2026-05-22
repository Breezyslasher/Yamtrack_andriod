package com.yamtrack.app.ui.lists

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.databinding.ActivityListItemsBinding
import com.yamtrack.app.ui.details.MediaDetailsActivity
import com.yamtrack.app.ui.home.MediaAdapter
import dagger.hilt.android.AndroidEntryPoint

/**
 * Displays the items in a single CustomList. Tapping an item opens its
 * detail screen; long-pressing prompts to remove it from the list.
 */
@AndroidEntryPoint
class ListItemsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LIST_ID = "list_id"
        const val EXTRA_LIST_NAME = "list_name"
    }

    private lateinit var binding: ActivityListItemsBinding
    private val viewModel: ListItemsViewModel by viewModels()
    private lateinit var adapter: MediaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListItemsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val listId = intent.getLongExtra(EXTRA_LIST_ID, -1)
        if (listId < 0) return finish()

        binding.toolbar.title = intent.getStringExtra(EXTRA_LIST_NAME)
            ?: getString(R.string.lists)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = MediaAdapter(
            onItemClick = { item -> openDetails(item) }
        )
        binding.rvItems.layoutManager = GridLayoutManager(this, 3)
        binding.rvItems.adapter = adapter

        viewModel.items.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    binding.progressBar.visibility = View.GONE
                    adapter.submitList(result.data)
                    binding.tvEmpty.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                    bindLongPress(result.data)
                }
                is Result.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = result.message
                }
                is Result.Loading -> {
                    if (adapter.itemCount == 0) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewModel.toast.observe(this) { msg ->
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                viewModel.toastShown()
            }
        }

        viewModel.load(listId)
    }

    private fun openDetails(item: MediaItem) {
        val type = item.mediaType ?: return
        startActivity(Intent(this, MediaDetailsActivity::class.java).apply {
            putExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE, type.value)
            putExtra(MediaDetailsActivity.EXTRA_SOURCE, item.source)
            putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, item.mediaId)
        })
    }

    /** MediaAdapter doesn't expose a long-press callback, so attach one
     *  via the RecyclerView after each rebind. */
    private fun bindLongPress(items: List<MediaItem>) {
        binding.rvItems.post {
            for (i in items.indices) {
                val vh = binding.rvItems.findViewHolderForAdapterPosition(i) ?: continue
                vh.itemView.setOnLongClickListener {
                    confirmRemove(items[i]); true
                }
            }
        }
    }

    private fun confirmRemove(item: MediaItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.remove_from_list_title)
            .setMessage(getString(R.string.remove_from_list_message,
                item.title.ifBlank { "—" }))
            .setPositiveButton(R.string.remove) { _, _ -> viewModel.removeItem(item) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
