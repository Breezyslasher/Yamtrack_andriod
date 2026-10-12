package com.yamtrack.app.ui.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yamtrack.app.R
import com.yamtrack.app.data.model.CastMember
import com.yamtrack.app.databinding.ItemCastBinding

/**
 * Horizontal cast rail. [onClick] is null when the source has no person
 * pages (anime voice actors come from AniList, people endpoints are TMDB).
 */
class CastAdapter(
    private val onClick: ((CastMember) -> Unit)?
) : ListAdapter<CastMember, CastAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCastBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemCastBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            if (onClick != null) {
                binding.root.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onClick.invoke(getItem(pos))
                }
            } else {
                binding.root.isClickable = false
                binding.root.background = null
            }
        }

        fun bind(member: CastMember) {
            binding.tvName.text = member.name.orEmpty()
            binding.root.contentDescription = member.name
            val role = member.character?.takeIf { it.isNotBlank() }
            binding.tvRole.text = role
            binding.tvRole.visibility = if (role != null) View.VISIBLE else View.GONE
            if (!member.image.isNullOrBlank()) {
                binding.ivPhoto.load(member.image) {
                    crossfade(true)
                    error(R.drawable.ic_person)
                }
            } else {
                binding.ivPhoto.setImageResource(R.drawable.ic_person)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<CastMember>() {
        override fun areItemsTheSame(o: CastMember, n: CastMember) =
            o.personId == n.personId && o.character == n.character
        override fun areContentsTheSame(o: CastMember, n: CastMember) = o == n
    }
}
