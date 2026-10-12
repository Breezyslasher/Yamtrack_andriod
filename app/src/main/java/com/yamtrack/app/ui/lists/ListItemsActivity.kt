package com.yamtrack.app.ui.lists

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.ArtworkOverrides
import com.yamtrack.app.databinding.ActivityListItemsBinding
import com.yamtrack.app.ui.common.ArtworkPicker
import com.yamtrack.app.ui.details.MediaDetailsActivity
import com.yamtrack.app.ui.home.MediaAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Displays the items in a single CustomList. Tapping an item opens its
 * detail screen; long-pressing offers to change its artwork or remove it
 * from the list.
 */
@AndroidEntryPoint
class ListItemsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LIST_ID = "list_id"
        const val EXTRA_LIST_NAME = "list_name"
    }

    private lateinit var binding: ActivityListItemsBinding
    private val viewModel: ListItemsViewModel by viewModels()

    @Inject lateinit var artworkPicker: ArtworkPicker
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
            onItemClick = { item -> openDetails(item) },
            onItemLongClick = { item -> showItemActions(item) }
        )
        binding.rvItems.layoutManager = GridLayoutManager(this, 3)
        binding.rvItems.adapter = adapter

        ArtworkOverrides.version.observe(this) { adapter.notifyDataSetChanged() }

        viewModel.items.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    binding.progressBar.visibility = View.GONE
                    adapter.submitList(result.data)
                    binding.tvEmpty.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
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

    /** Long-press -> change the item's artwork or remove it from the list. */
    private fun showItemActions(item: MediaItem) {
        val target = ArtworkPicker.Target.of(item)
        if (target == null) {
            confirmRemove(item)
            return
        }
        val actions = arrayOf(
            getString(R.string.change_artwork),
            getString(R.string.remove_from_list_title)
        )
        AlertDialog.Builder(this)
            .setTitle(item.title.ifBlank { null })
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> artworkPicker.show(this, lifecycleScope, target)
                    1 -> confirmRemove(item)
                }
            }
            .show()
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
