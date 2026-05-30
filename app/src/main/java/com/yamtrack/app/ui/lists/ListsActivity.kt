package com.yamtrack.app.ui.lists

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.yamtrack.app.R
import com.yamtrack.app.data.model.CustomList
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.databinding.ActivityListsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ListsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListsBinding
    private val viewModel: ListsViewModel by viewModels()
    private lateinit var adapter: ListsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_create_list) {
                showCreateDialog(); true
            } else false
        }

        adapter = ListsAdapter(
            onClick = { list -> openList(list) },
            onLongPress = { list -> confirmDelete(list) }
        )
        binding.rvLists.layoutManager = LinearLayoutManager(this)
        binding.rvLists.adapter = adapter

        viewModel.lists.observe(this) { result ->
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
    }

    private fun openList(list: CustomList) {
        startActivity(Intent(this, ListItemsActivity::class.java).apply {
            putExtra(ListItemsActivity.EXTRA_LIST_ID, list.id)
            putExtra(ListItemsActivity.EXTRA_LIST_NAME, list.name)
        })
    }

    private fun showCreateDialog() {
        val pad = (resources.displayMetrics.density * 20).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.list_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val descInput = EditText(this).apply {
            hint = getString(R.string.list_description_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        container.addView(nameInput)
        container.addView(descInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.create_list)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.list_name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.createList(name, descInput.text.toString().trim())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(list: CustomList) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_list_title)
            .setMessage(getString(R.string.delete_list_message, list.name))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteList(list.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
