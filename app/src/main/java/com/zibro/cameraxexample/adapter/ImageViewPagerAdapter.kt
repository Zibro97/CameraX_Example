package com.zibro.cameraxexample.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zibro.cameraxexample.databinding.ItemImageBinding
import com.zibro.cameraxexample.extensions.loadCenterCrop

class ImageViewPagerAdapter(var uriList:MutableList<Uri>) : RecyclerView.Adapter<ImageViewPagerAdapter.ViewHolder>(){

    inner class ViewHolder(private val binding:ItemImageBinding):RecyclerView.ViewHolder(binding.root){
        fun bind(uri:Uri) = with(binding){
            imageView.loadCenterCrop(uri.toString())
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ImageViewPagerAdapter.ViewHolder {
        return ViewHolder(ItemImageBinding.inflate(LayoutInflater.from(parent.context),parent,false))
    }

    override fun onBindViewHolder(holder: ImageViewPagerAdapter.ViewHolder, position: Int) {
        holder.bind(uriList[position])
    }

    override fun getItemCount(): Int = uriList.size

}