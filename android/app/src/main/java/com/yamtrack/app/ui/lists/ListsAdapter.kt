package com.yamtrack.app.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yamtrack.app.data.model.CustomList
import com.yamtrack.app.databinding.ItemListCardBinding

class ListsAdapter(
    private val onClick: (CustomList) -> Unit,
    private val onLongPress: (CustomList) -> Unit
) : ListAdapter<CustomList, ListsAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemListCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemListCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onLongPress(getItem(pos)); true
                } else false
            }
        }

        fun bind(list: CustomList) {
            binding.tvName.text = list.name
            val parts = mutableListOf("${list.itemsCount} items")
            list.owner?.username?.let { parts.add("by $it") }
            binding.tvMeta.text = parts.joinToString(" · ")
        }
    }

    class Diff : DiffUtil.ItemCallback<CustomList>() {
        override fun areItemsTheSame(o: CustomList, n: CustomList) = o.id == n.id
        override fun areContentsTheSame(o: CustomList, n: CustomList) = o == n
    }
}
