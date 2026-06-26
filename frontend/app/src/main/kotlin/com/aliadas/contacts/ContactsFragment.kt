package com.aliadas.contacts

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aliadas.R
import com.aliadas.databinding.FragmentContactsBinding
import com.aliadas.databinding.ItemContactBinding
import com.aliadas.databinding.DialogAddContactBinding
import com.aliadas.network.ContactRequest
import com.aliadas.network.ContactResponse
import com.aliadas.network.RetrofitClient
import com.aliadas.utils.SessionManager
import kotlinx.coroutines.launch

class ContactsFragment : Fragment() {

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!
    private val contacts = mutableListOf<ContactResponse>()
    private lateinit var adapter: ContactAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ContactAdapter(contacts,
            onDelete = { contact -> deleteContact(contact) }
        )
        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddContactDialog() }
        binding.swipeRefresh.setOnRefreshListener { loadContacts() }

        loadContacts()
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            binding.swipeRefresh.isRefreshing = true
            try {
                val token = SessionManager.getBearerToken(requireContext())
                val res = RetrofitClient.api.getContacts(token)
                if (res.isSuccessful) {
                    contacts.clear()
                    contacts.addAll(res.body() ?: emptyList())
                    adapter.notifyDataSetChanged()
                    updateTrustedContactsCache(contacts)
                    binding.tvEmpty.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al cargar contactos", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showAddContactDialog() {
        if (contacts.size >= 5) {
            Toast.makeText(requireContext(), "Máximo 5 contactos de confianza", Toast.LENGTH_SHORT).show()
            return
        }
        val dialogBinding = DialogAddContactBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle("Agregar contacto de confianza")
            .setView(dialogBinding.root)
            .setPositiveButton("Agregar") { _, _ ->
                val name = dialogBinding.etName.text.toString().trim()
                val phone = dialogBinding.etPhone.text.toString().trim()
                val relation = dialogBinding.etRelation.text.toString().trim().ifBlank { "Contacto" }
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    addContact(name, phone, relation)
                } else {
                    Toast.makeText(requireContext(), "Nombre y teléfono son requeridos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addContact(name: String, phone: String, relation: String) {
        lifecycleScope.launch {
            try {
                val token = SessionManager.getBearerToken(requireContext())
                val res = RetrofitClient.api.addContact(token, ContactRequest(name, phone, relation))
                if (res.isSuccessful) {
                    Toast.makeText(requireContext(), "✅ Contacto agregado", Toast.LENGTH_SHORT).show()
                    loadContacts()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al agregar contacto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteContact(contact: ContactResponse) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar contacto")
            .setMessage("¿Eliminar a ${contact.name} de tus contactos de confianza?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val token = SessionManager.getBearerToken(requireContext())
                        RetrofitClient.api.deleteContact(token, contact.id)
                        loadContacts()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error al eliminar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateTrustedContactsCache(contacts: List<ContactResponse>) {
        val prefs = requireContext().getSharedPreferences("aliadas_contacts", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("trusted_phones", contacts.map { it.phone }.toSet()).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ContactAdapter(
    private val items: List<ContactResponse>,
    private val onDelete: (ContactResponse) -> Unit
) : RecyclerView.Adapter<ContactAdapter.VH>() {

    inner class VH(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val contact = items[position]
        holder.binding.tvName.text = contact.name
        holder.binding.tvPhone.text = contact.phone
        holder.binding.tvRelation.text = contact.relation
        holder.binding.btnDelete.setOnClickListener { onDelete(contact) }
    }
}
