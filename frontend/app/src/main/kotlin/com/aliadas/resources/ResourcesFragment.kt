package com.aliadas.resources

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aliadas.R
import com.aliadas.databinding.FragmentResourcesBinding
import com.aliadas.databinding.ItemResourceBinding
import com.aliadas.network.ResourceResponse
import com.aliadas.network.RetrofitClient
import com.aliadas.utils.SessionManager
import kotlinx.coroutines.launch

class ResourcesFragment : Fragment() {

    private var _binding: FragmentResourcesBinding? = null
    private val binding get() = _binding!!
    private val resources = mutableListOf<ResourceResponse>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResourcesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ResourceAdapter(resources) { resource ->
            handleResourceAction(resource)
        }
        binding.rvResources.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResources.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { loadResources() }
        loadResources()
    }

    private fun loadResources() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            try {
                val token = SessionManager.getBearerToken(requireContext())
                val res = RetrofitClient.api.getResources(token)
                if (res.isSuccessful) {
                    resources.clear()
                    resources.addAll(res.body() ?: emptyList())
                    binding.rvResources.adapter?.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al cargar recursos", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun handleResourceAction(resource: ResourceResponse) {
        when {
            resource.actionUrl.startsWith("tel:") -> {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(resource.actionUrl)))
            }
            resource.actionUrl == "map" -> {
                // Navigate to map tab
                requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                    R.id.bottom_nav
                )?.selectedItemId = R.id.nav_home
            }
            resource.actionUrl.startsWith("http") -> {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resource.actionUrl)))
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ResourceAdapter(
    private val items: List<ResourceResponse>,
    private val onAction: (ResourceResponse) -> Unit
) : RecyclerView.Adapter<ResourceAdapter.VH>() {

    inner class VH(val binding: ItemResourceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemResourceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val res = items[position]
        holder.binding.tvTitle.text = res.title
        holder.binding.tvDescription.text = res.description
        holder.binding.btnAction.text = res.actionLabel
        holder.binding.chip24h.visibility = if (res.isAvailable24h) View.VISIBLE else View.GONE
        holder.binding.btnAction.setOnClickListener { onAction(res) }
    }
}
