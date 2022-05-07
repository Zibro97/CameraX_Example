package com.zibro.cameraxexample

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
import androidx.camera.core.ImageCapture.FLASH_MODE_AUTO
import androidx.camera.core.impl.ImageOutputConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.zibro.cameraxexample.ImageListActivity.Companion.IMAGE_LIST_REQUEST_CODE
import com.zibro.cameraxexample.databinding.ActivityMainBinding
import com.zibro.cameraxexample.extensions.clear
import com.zibro.cameraxexample.extensions.loadCenterCrop
import com.zibro.cameraxexample.util.PathUtil
import java.io.File
import java.io.FileNotFoundException
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    //MainActivity 바인딩 객체
    private lateinit var binding : ActivityMainBinding

    private lateinit var cameraExecutor: ExecutorService

    private val cameraMainExecutor by lazy { ContextCompat.getMainExecutor(this) }

    //카메라 권한 얻어오면 이후 실행
    //CameraProvider 객체
    private val cameraProviderFuture by lazy { ProcessCameraProvider.getInstance(this) }

    //ImageCapture 객체 (캡처 시점에 사용)
    private lateinit var imageCapture : ImageCapture

    //디스플레이가 바뀌었을때(회전) 감지하여 rotation 얻어오기 위
    //Display 리스너를 등록해줄 객체 생성
    private val displayManager by lazy { getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    private var root : View? = null

    private var displayId : Int = -1

    private var camera : Camera? = null

    private var isCapturing : Boolean = false

    private var isFlashEnabled = false

    private val displayListener = object : DisplayManager.DisplayListener{
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int){}
        //화면 회전 시 대응
        override fun onDisplayChanged(displayId: Int) {
            if(this@MainActivity.displayId == displayId){
                if(::imageCapture.isInitialized && root != null){
                    imageCapture.targetRotation = root?.display?.rotation ?: ImageOutputConfig.INVALID_ROTATION
                }
            }
        }
    }
    //상세화면에서 보여질 이미지 리스트들
    private var uriList = mutableListOf<Uri>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        root = binding.root
        setContentView(root)

        if(allPermissionsGranted()){
            //카메라 권한이 있을 경우
            startCamera(binding.viewFinder)
        }else {
            //카메라 권한이 없을 경우
            ActivityCompat.requestPermissions(this,REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera(viewFinder : PreviewView){
        displayManager.registerDisplayListener(displayListener,null)
        cameraExecutor = Executors.newSingleThreadExecutor()
        viewFinder.postDelayed({
            displayId = viewFinder.display.displayId
            bindCameraUseCase()
        },10)
    }

    private fun bindCameraUseCase() = with(binding){
        val rotation = viewFinder.display.rotation
        //카메라 설정(후면)
        val cameraSelector = CameraSelector.Builder().requireLensFacing(LENS_FACING).build()

        cameraProviderFuture.addListener({
            //카메라 객체
            val cameraProvider : ProcessCameraProvider = cameraProviderFuture.get()
            //preview 세팅
            val preview = Preview.Builder().apply {
                //화면 상의 비율 설정
                setTargetAspectRatio(AspectRatio.RATIO_4_3)
                setTargetRotation(rotation)
                //setTargetResolution() : default는 최고 해상도 카메라 사용
            }.build()
            //이미지 캡처
            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(rotation)
                .setFlashMode(FLASH_MODE_AUTO)

            imageCapture = imageCaptureBuilder.build()

            try {
                //카메라가 쓰이고 있으면 기존에 바인딩 되어 있는 카메라는 바인딩 해제
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this@MainActivity,cameraSelector,preview, imageCapture
                )
                preview.setSurfaceProvider(viewFinder.surfaceProvider)
                bindCaptureListener()
                bindZoomListener()
                bindLightSwitchListener()
                //initFlashAndAddListener()
                bindPreviewImageViewClickListener()
            }catch (e:Exception){
                e.printStackTrace()
            }
        }, cameraMainExecutor)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindZoomListener() = with(binding){
        //preview 영역에 zoom/In/Out을 했을 때 두손가락이 keyDown이벤트를 받아 얼마나 늘어나고 줄어드는지 값을 받아 콜백으로 넘겨줌
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener(){
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                camera?.cameraControl?.setZoomRatio(currentZoomRatio * delta)
                return true
            }
        }
        val scaleGestureDetector = ScaleGestureDetector(this@MainActivity, listener)
        viewFinder.setOnTouchListener{ _,event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }
    private fun initFlashAndAddListener() = with(binding){
        //기기의 플래시 유무
        val hasFlash = camera?.cameraInfo?.hasFlashUnit()?:false
        flashSwitch.isGone = hasFlash.not()
        if(hasFlash){
            flashSwitch.setOnCheckedChangeListener { _, isChecked ->
                isFlashEnabled = isChecked
            }
        }else{
            isFlashEnabled = false
            flashSwitch.setOnCheckedChangeListener(null)
        }
    }

    private fun bindCaptureListener() = with(binding){
        captureButton.setOnClickListener{
            if(!isCapturing){
                isCapturing = true
                captureCamera()
            }
        }
    }

    //이미지가 저장이 됐으면 갤러리에 보여지도록 수정하게 하는 함수
    private fun updateSavedImageContent() {
        contentUri?.let {
            isCapturing = try {
                val file = File(PathUtil.getPath(this,it) ?: throw FileNotFoundException())
                //jpeg 이미지를 외부에서도 읽을 수 있도록 연결해줌
                MediaScannerConnection.scanFile(this, arrayOf(file.path), arrayOf("image/jpeg"),null)
                //핸들러를 통해서 메인스레드에서 이미지 처리
                Handler(Looper.getMainLooper()).post{
                    binding.previewImageView.loadCenterCrop(url = it.toString(), corner = 4f)
                }
                if(isFlashEnabled) flashLight(false)
                uriList.add(it)
                false
            }catch (e:FileNotFoundException){
                e.printStackTrace()
                false
            }
        }
    }

    private var contentUri : Uri? = null

    private fun captureCamera(){
        if(::imageCapture.isInitialized.not()) return
        val photoFile = File(
            PathUtil.getOutputDirectory(this),
            SimpleDateFormat(FILENAME_FORMAT, Locale.KOREA).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        if(isFlashEnabled) flashLight(true)
        imageCapture.takePicture(outputOptions,cameraExecutor,object : ImageCapture.OnImageSavedCallback{
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = outputFileResults.savedUri ?: Uri.fromFile(photoFile)
                contentUri = savedUri
                updateSavedImageContent()
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
                isCapturing = false
            }
        })
    }

    //플래시 키는 함수
    private fun flashLight(isFlashed:Boolean){
        val hasFlash = camera?.cameraInfo?.hasFlashUnit()
        if(true == hasFlash){
            camera?.cameraControl?.enableTorch(isFlashed)
        }
    }

    private fun bindPreviewImageViewClickListener() = with(binding){
        previewImageView.setOnClickListener {
            startActivityForResult(
                ImageListActivity.newIntent(this@MainActivity,uriList), IMAGE_LIST_REQUEST_CODE
            )
        }
    }
    private fun bindLightSwitchListener() = with(binding) {
        flashSwitch.setOnCheckedChangeListener { _, isChecked ->
            isFlashEnabled = isChecked
        }
    }

    //권한 요청 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == REQUEST_CODE_PERMISSIONS){
            if(allPermissionsGranted()){
                startCamera(binding.viewFinder)
            }else{
                Toast.makeText(this,"카메라 권한이 없습니다.",Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_LIST_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            uriList = data?.getParcelableArrayListExtra(ImageListActivity.URI_LIST_KEY) ?: uriList
            if (uriList.isNotEmpty()) {
                binding.previewImageView.loadCenterCrop(url = uriList.first().toString(), corner = 4f)
            } else {
                binding.previewImageView.clear()
            }
        }
    }

    companion object{
        //파일명 Format
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        private val LENS_FACING: Int = CameraSelector.LENS_FACING_BACK
    }
}