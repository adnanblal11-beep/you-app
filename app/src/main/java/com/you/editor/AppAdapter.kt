package com.you.editor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.you.editor.databinding.ItemAppBinding

class AppAdapter(
    private val apps: List<AppInfo>,
    private val onAction: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.binding.appName.text = app.name
        holder.binding.packageName.text = app.packageName
        holder.binding.appIcon.setImageDrawable(app.icon)
        holder.binding.btnAction.setOnClickListener { onAction(app) }
    }

    override fun getItemCount() = apps.size
}