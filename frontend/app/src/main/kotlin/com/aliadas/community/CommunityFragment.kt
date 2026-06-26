package com.aliadas.community

import android.os.Bundle
import android.text.format.DateUtils
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aliadas.R
import com.aliadas.databinding.FragmentCommunityBinding
import com.aliadas.databinding.ItemPostBinding
import com.aliadas.network.PostRequest
import com.aliadas.network.PostResponse
import com.aliadas.network.RetrofitClient
import com.aliadas.utils.SessionManager
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!
    private val posts = mutableListOf<PostResponse>()
    private lateinit var adapter: PostAdapter
    private var currentFilter = "recent"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PostAdapter(posts,
            onLike = { post -> likePost(post) },
            onComment = { post -> showCommentsDialog(post) }
        )
        binding.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPosts.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { loadPosts() }

        // Filter chips
        binding.chipRecent.setOnClickListener { currentFilter = "recent"; loadPosts() }
        binding.chipPopular.setOnClickListener { currentFilter = "popular"; loadPosts() }
        binding.chipApoyo.setOnClickListener { currentFilter = "apoyo"; loadPosts() }

        // Publish button
        binding.btnPublish.setOnClickListener { publishPost() }

        loadPosts()
    }

    private fun loadPosts() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            try {
                val token = SessionManager.getBearerToken(requireContext())
                val res = RetrofitClient.api.getPosts(token, currentFilter)
                if (res.isSuccessful) {
                    posts.clear()
                    posts.addAll(res.body() ?: emptyList())
                    adapter.notifyDataSetChanged()
                    binding.tvEmpty.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al cargar publicaciones", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun publishPost() {
        val content = binding.etPost.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "Escribe algo para publicar", Toast.LENGTH_SHORT).show()
            return
        }
        val category = when {
            binding.chipCategory.checkedChipId == binding.chipCatApoyo.id -> "apoyo"
            binding.chipCategory.checkedChipId == binding.chipCatDuda.id -> "duda"
            binding.chipCategory.checkedChipId == binding.chipCatReporte.id -> "reporte"
            else -> "general"
        }
        lifecycleScope.launch {
            try {
                val token = SessionManager.getBearerToken(requireContext())
                val res = RetrofitClient.api.createPost(token, PostRequest(content, category))
                if (res.isSuccessful) {
                    binding.etPost.setText("")
                    Toast.makeText(requireContext(), "✅ Publicado anónimamente", Toast.LENGTH_SHORT).show()
                    loadPosts()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al publicar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun likePost(post: PostResponse) {
        lifecycleScope.launch {
            try {
                val token = SessionManager.getBearerToken(requireContext())
                RetrofitClient.api.likePost(token, post.id)
                loadPosts()
            } catch (e: Exception) { }
        }
    }

    private fun showCommentsDialog(post: PostResponse) {
        // Show comments bottom sheet
        val dialog = CommentsBottomSheet.newInstance(post.id)
        dialog.show(childFragmentManager, "comments")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PostAdapter(
    private val items: List<PostResponse>,
    private val onLike: (PostResponse) -> Unit,
    private val onComment: (PostResponse) -> Unit
) : RecyclerView.Adapter<PostAdapter.VH>() {

    inner class VH(val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val post = items[position]
        holder.binding.tvContent.text = post.content
        holder.binding.tvTime.text = DateUtils.getRelativeTimeSpanString(post.createdAt)
        holder.binding.tvLikes.text = post.likesCount.toString()
        holder.binding.tvComments.text = post.commentsCount.toString()
        holder.binding.tvCategory.text = "#${post.category}"
        holder.binding.btnLike.isSelected = post.hasLiked
        holder.binding.btnLike.setOnClickListener { onLike(post) }
        holder.binding.btnComment.setOnClickListener { onComment(post) }
    }
}
