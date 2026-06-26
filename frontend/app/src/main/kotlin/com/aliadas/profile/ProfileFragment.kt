package com.aliadas.profile

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aliadas.R
import com.aliadas.auth.LoginActivity
import com.aliadas.databinding.FragmentProfileBinding
import com.aliadas.network.RetrofitClient
import com.aliadas.network.UpdateProfileRequest
import com.aliadas.utils.SessionManager
import kotlinx.coroutines.launch
import android.content.Intent
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView

// Maps avatar string IDs to drawable resource names
val AVATAR_DRAWABLES = mapOf(
    "avatar_flower" to R.drawable.ic_avatar_flower,
    "avatar_star" to R.drawable.ic_avatar_star,
    "avatar_moon" to R.drawable.ic_avatar_moon,
    "avatar_sun" to R.drawable.ic_avatar_sun,
    "avatar_butterfly" to R.drawable.ic_avatar_butterfly,
    "avatar_leaf" to R.drawable.ic_avatar_leaf,
    "avatar_heart" to R.drawable.ic_avatar_heart,
    "avatar_diamond" to R.drawable.ic_avatar_diamond,
    "avatar_crown" to R.drawable.ic_avatar_crown,
    "avatar_wave" to R.drawable.ic_avatar_wave,
    "avatar_fire" to R.drawable.ic_avatar_fire,
    "avatar_snowflake" to R.drawable.ic_avatar_snowflake
)

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var currentAvatar = "avatar_flower"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProfile()

        binding.btnChangeAvatar.setOnClickListener { showAvatarPicker() }
        binding.btnSave.setOnClickListener { saveProfile() }
        binding.btnLogout.setOnClickListener { logout() }
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val token = SessionManager.getBearerToken(requireContext())
                val res = RetrofitClient.api.getProfile(token)
                if (res.isSuccessful) {
                    val profile = res.body()!!
                    binding.etName.setText(profile.name)
                    binding.tvEmail.text = profile.email
                    currentAvatar = profile.avatarIcon
                    updateAvatarImage(currentAvatar)
                }
            } catch (e: Exception) { }
        }
    }

    private fun updateAvatarImage(avatarKey: String) {
        val drawableRes = AVATAR_DRAWABLES[avatarKey] ?: R.drawable.ic_avatar_flower
        binding.ivAvatar.setImageResource(drawableRes)
    }

    private fun showAvatarPicker() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_avatar_picker, null)
        val grid = dialogView.findViewById<GridLayout>(R.id.gridAvatars)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Elige tu avatar")
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .create()

        AVATAR_DRAWABLES.forEach { (key, drawableRes) ->
            val iv = ImageView(requireContext()).apply {
                setImageResource(drawableRes)
                val dp56 = (56 * resources.displayMetrics.density).toInt()
                val params = GridLayout.LayoutParams().apply {
                    width = dp56; height = dp56
                    setMargins(8, 8, 8, 8)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                background = requireContext().getDrawable(R.drawable.bg_avatar_selector)
                setOnClickListener {
                    currentAvatar = key
                    updateAvatarImage(key)
                    dialog.dismiss()
                }
            }
            grid.addView(iv)
        }
        dialog.show()
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val token = SessionManager.getBearerToken(requireContext())
                val res = RetrofitClient.api.updateProfile(token, UpdateProfileRequest(name, currentAvatar))
                if (res.isSuccessful) {
                    Toast.makeText(requireContext(), "✅ Perfil actualizado", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error al guardar", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cerrar sesión")
            .setMessage("¿Segura que quieres cerrar sesión?")
            .setPositiveButton("Sí") { _, _ ->
                lifecycleScope.launch {
                    SessionManager.clearSession(requireContext())
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
