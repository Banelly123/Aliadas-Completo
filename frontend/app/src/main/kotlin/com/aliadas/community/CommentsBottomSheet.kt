package com.aliadas.community

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aliadas.databinding.BottomSheetCommentsBinding
import com.aliadas.databinding.ItemCommentBinding
import com.aliadas.network.CommentRequest
import com.aliadas.network.CommentResponse
import com.aliadas.network.RetrofitClient
import com.aliadas.utils.SessionManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import android.text.format.DateUtils

class CommentsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCommentsBinding? = null
    private val binding get() = _binding!!
    private var postId: Int = 0
    private val comments = mutableListOf<CommentResponse>()
    private lateinit var adapter: CommentAdapter

    companion object {
        fun newInstance(postId: Int) = CommentsBottomSheet().apply {
            arguments = Bundle().apply { putInt("postId", postId) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postId = arguments?.getInt("postId") ?: return

        adapter = CommentAdapter(comments)
        binding.rvComments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvComments.adapter = adapter

        binding.btnSend.setOnClickListener { sendComment() }
        loadComments()
    }

    private fun loadComments() {
        lifecycleScope.launch {
            try {
                val token = SessionManager.getBearerToken(requireContext())
                val res = RetrofitClient.api.getComments(token, postId)
                if (res.isSuccessful) {
                    comments.clear()
                    comments.addAll(res.body() ?: emptyList())
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) { }
        }
    }

    private fun sendComment() {
        val text = binding.etComment.text.toString().trim()
        if (text.isEmpty()) return
        lifecycleScope.launch {
            try {
                val token = SessionManager.getBearerToken(requireContext())
                RetrofitClient.api.addComment(token, postId, CommentRequest(text))
                binding.etComment.setText("")
                loadComments()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al comentar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class CommentAdapter(private val items: List<CommentResponse>) : RecyclerView.Adapter<CommentAdapter.VH>() {
    inner class VH(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.binding.tvContent.text = items[position].content
        holder.binding.tvTime.text = DateUtils.getRelativeTimeSpanString(items[position].createdAt)
    }
}
