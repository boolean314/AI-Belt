package com.example.ai_belt_mobile.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ai_belt_mobile.R
import com.google.android.material.button.MaterialButton


data class MemberItem(
    val name: String,
    val phone: String
)

class MemberListAdapter(
    private val data: List<MemberItem>,
    private val onItemClick: (MemberItem) -> Unit = {},
    private val onEmergencyClick: (MemberItem) -> Unit = {}
) : RecyclerView.Adapter<MemberListAdapter.MemberViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member, parent, false)
        return MemberViewHolder(view, onItemClick, onEmergencyClick)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    class MemberViewHolder(
        itemView: View,
        private val onItemClick: (MemberItem) -> Unit,
        private val onEmergencyClick: (MemberItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.member_name)
        private val tvPhone: TextView = itemView.findViewById(R.id.member_phone)
        private val btnEmergency: MaterialButton = itemView.findViewById(R.id.emergency_btn)

        fun bind(item: MemberItem) {
            tvName.text = item.name
            tvPhone.text = item.phone

            itemView.setOnClickListener { onItemClick(item) }
            btnEmergency.setOnClickListener { onEmergencyClick(item) }
        }
    }
}