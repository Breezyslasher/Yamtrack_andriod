package com.yamtrack.app.ui.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yamtrack.app.R
import com.yamtrack.app.data.model.WatchProvider
import com.yamtrack.app.databinding.ItemProviderBinding

class ProvidersAdapter(
    private val onClick: (WatchProvider) -> Unit
) : ListAdapter<WatchProvider, ProvidersAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemProviderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemProviderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
        }

        fun bind(provider: WatchProvider) {
            binding.tvName.text = provider.providerName.orEmpty()
            binding.root.contentDescription = provider.providerName

            val context = binding.root.context
            val prices = listOfNotNull(
                provider.rentPrice?.takeIf { it.isNotBlank() }
                    ?.let { context.getString(R.string.provider_rent_price, it) },
                provider.buyPrice?.takeIf { it.isNotBlank() }
                    ?.let { context.getString(R.string.provider_buy_price, it) }
            )
            binding.tvPrice.text = prices.joinToString("\n")
            binding.tvPrice.visibility = if (prices.isEmpty()) View.GONE else View.VISIBLE
            if (!provider.image.isNullOrBlank()) {
                binding.ivLogo.load(provider.image) {
                    crossfade(true)
                    error(R.drawable.placeholder_poster)
                }
            } else {
                binding.ivLogo.setImageResource(R.drawable.placeholder_poster)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<WatchProvider>() {
        override fun areItemsTheSame(o: WatchProvider, n: WatchProvider) =
            o.providerId == n.providerId
        override fun areContentsTheSame(o: WatchProvider, n: WatchProvider) = o == n
    }
}
