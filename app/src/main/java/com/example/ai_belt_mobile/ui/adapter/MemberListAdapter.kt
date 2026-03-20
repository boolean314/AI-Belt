package com.example.ai_belt_mobile.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ai_belt_mobile.R
import com.google.android.material.button.MaterialButton


data class MemberItem(
    val id: Int,
    val name: String,
    val phone: String,
    val isEmergency: Boolean
)

class MemberListAdapter(
    private val onItemClick: (MemberItem) -> Unit = {},
    private val onEmergencyClick: (MemberItem) -> Unit = {}
) : ListAdapter<MemberItem, MemberListAdapter.MemberViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member, parent, false)
        return MemberViewHolder(view, onItemClick, onEmergencyClick)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

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
            btnEmergency.text = if (item.isEmergency) "紧急" else "普通"

            itemView.setOnClickListener { onItemClick(item) }
            btnEmergency.setOnClickListener { onEmergencyClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MemberItem>() {
        override fun areItemsTheSame(oldItem: MemberItem, newItem: MemberItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MemberItem, newItem: MemberItem): Boolean {
            return oldItem == newItem
        }
    }
}