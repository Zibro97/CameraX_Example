package com.zibro.cameraxexample

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.viewpager2.widget.ViewPager2
import com.zibro.cameraxexample.adapter.ImageViewPagerAdapter
import com.zibro.cameraxexample.databinding.ActivityImageListBinding
import com.zibro.cameraxexample.util.PathUtil
import java.io.File
import java.io.FileNotFoundException

class ImageListActivity : AppCompatActivity() {
    private lateinit var binding:ActivityImageListBinding

    private val uriList by lazy<List<Uri>> { intent.getParcelableArrayListExtra(URI_LIST_KEY)!! }

    private lateinit var imageViewPagerAdapter: ImageViewPagerAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
    }
    private fun initViews() = with(binding){
        setSupportActionBar(toolbar)
        setUpImageList(uriList)
    }
    private var currentUri : Uri? = null
    private fun setUpImageList(uriList:List<Uri>) = with(binding){
        if(::imageViewPagerAdapter.isInitialized.not()){
            imageViewPagerAdapter = ImageViewPagerAdapter(uriList.toMutableList())
        }
        imageViewpager.adapter = imageViewPagerAdapter
        indicator.setViewPager(imageViewpager)
        imageViewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback(){
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                toolbar.title = if(imageViewPagerAdapter.uriList.isNotEmpty()){
                    currentUri = imageViewPagerAdapter.uriList[position]
                    getString(R.string.images_page,position+1,imageViewPagerAdapter.uriList.size)
                }else{
                    currentUri = null
                    ""
                }
            }
        })
        deleteButton.setOnClickListener {
            currentUri?.let { uri ->
                removeImage(uri)
            }
        }
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(URI_LIST_KEY, ArrayList<Uri>().apply { imageViewPagerAdapter.uriList.forEach { add(it) } })
        })
        finish()
    }
    private fun removeImage(uri:Uri){
        val file = File(PathUtil.getPath(this,uri)?:throw FileNotFoundException())
        file.delete()
        val removedImageIndex = imageViewPagerAdapter.uriList.indexOf(uri)
        imageViewPagerAdapter.uriList.removeAt(removedImageIndex)
        imageViewPagerAdapter.notifyItemRemoved(removedImageIndex)
        binding.indicator.setViewPager(binding.imageViewpager)

        if (imageViewPagerAdapter.uriList.isNotEmpty()) {
            currentUri = if (removedImageIndex == 0) {
                imageViewPagerAdapter.uriList[removedImageIndex]
            } else {
                imageViewPagerAdapter.uriList[removedImageIndex - 1]
            }
        }

        MediaScannerConnection.scanFile(
            this, arrayOf(file.path), arrayOf(file.name)
        ) { _, _ ->
            contentResolver.delete(uri, null, null)
        }

        if (imageViewPagerAdapter.uriList.isEmpty()) {
            Toast.makeText(this, "삭제할 수 있는 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
            onBackPressed()
        } else {
            binding.toolbar.title = getString(R.string.images_page, removedImageIndex + 1, imageViewPagerAdapter.uriList.size)
        }
    }

    companion object{
        const val URI_LIST_KEY = "uriList"

        const val IMAGE_LIST_REQUEST_CODE = 100

        fun newIntent(activity:Activity,uriList:List<Uri>) = Intent(activity,ImageListActivity::class.java).apply{
            putExtra(URI_LIST_KEY,ArrayList<Uri>().apply { uriList.forEach { add(it) } })
        }
    }
}