package com.example.cameraplusfacedetection

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.decodeStream
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.OnFailureListener
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {

    lateinit var mImageView: ImageView
    lateinit var textToBeDisplayed: TextView
    lateinit var btnCamera: Button
    lateinit var btnGallery: Button
    lateinit var imageFile: File
    lateinit var croppedImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mImageView = findViewById(R.id.image_view)
        textToBeDisplayed = findViewById(R.id.text_view)
        btnCamera = findViewById(R.id.btn_camera)
        btnGallery = findViewById(R.id.btn_gallery)
        croppedImage = findViewById(R.id.croppedImage)
        btnCamera.setOnClickListener { openCamera() }
        btnGallery.setOnClickListener { selectPicture() }
    }


    private fun selectPicture() {
        imageFile = createTempFile(imageFile)
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryResult.launch(intent)
    }

    private val cameraResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val bitmap = resizeImage(imageFile, imageFile.path, mImageView)
                mImageView.setImageBitmap(bitmap)
                if (bitmap != null) {
                    textToBeDisplayed.text = null

                    detectFaces(bitmap)
                }
            }
        }
    private val galleryResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val dataUri = it.data?.data
                val bitmap = resizeImage(imageFile, this, dataUri, mImageView)
                if (bitmap != null) {
                    textToBeDisplayed.text = null
                    mImageView.setImageBitmap(bitmap)
                    detectFaces(bitmap)
                }

            }
        }


    private fun openCamera() {
        imageFile = getPhotoFile("selfie")
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photo = FileProvider.getUriForFile(this, "$packageName.provider",
            imageFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photo)
        cameraResult.launch(intent)
    }

    fun createTempFile(file: File): File {
        val dir = File(Environment.getExternalStorageDirectory().path)
        if (!dir.exists() || !dir.isDirectory) {
            dir.mkdirs()
        }
        if (file == null) {
            return File(dir, "original.jpg")
        }
        return file
    }


    private fun getPhotoFile(fileName: String): File {
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(fileName, ".jpg", storageDir)
    }

    private fun detectFaces(bitmap: Bitmap) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap,90)
        detector.process(image).addOnSuccessListener { faces ->
            textToBeDisplayed.text = getInfoFromFaces(faces, bitmap)
        }.addOnFailureListener(
            OnFailureListener { textToBeDisplayed.text = (resources.getString(R.string.error)) })
    }

    private fun getInfoFromFaces(faces: List<Face>, bitmap: Bitmap): String {
        val result = StringBuilder()
        var smileProb = 0f
        var leftEyeOpenProb = 0f
        var rightEyeOpenProb = 0f
        for (face in faces) {
            if (face.smilingProbability != null) {
                smileProb = face.smilingProbability!!
            }
            if (face.leftEyeOpenProbability != null) {
                leftEyeOpenProb = face.leftEyeOpenProbability!!
            }
            if (face.rightEyeOpenProbability != null) {
                rightEyeOpenProb = face.rightEyeOpenProbability!!
            }
            result.append("Smile: ")
            if (smileProb > 0.5) {
                result.append("Yes")
            } else {
                result.append("No")
            }
            result.append("\nLeft eye: ")
            if (leftEyeOpenProb > 0.5) {
                result.append("Open")
            } else {
                result.append("Close")
            }
            result.append("\nRight eye: ")
            if (rightEyeOpenProb > 0.5) {
                result.append("Open")
            } else {
                result.append("Close")
            }
            result.append("\n Person's movement:")
            if (face.headEulerAngleY > 12.0) {
                result.append("Looking right")
            } else if (face.headEulerAngleY < -12.0) {
                result.append("Looking left")
            } else if (face.headEulerAngleY > -12.0 && face.headEulerAngleY < 12.0) {
                result.append("Looking straight")
            }
            result.append("\n\n")
        }
        if (faces.isNotEmpty() && !bitmap.equals("")) {
            Log.e("FACE", "${faces[0].boundingBox.width()}")
            cropDetectedFace(bitmap, faces)
        }


        return result.toString()
    }

    private fun cropDetectedFace(bitmap: Bitmap, face: List<Face>) {
        Log.e("FACE", "${face[0].boundingBox.width()}")
        val rect = face[0].boundingBox

        val x = Math.max(rect.left, 0)
        val y = Math.max(rect.top, 0)

        val width = rect.width()
        val height = rect.height()

        Log.e("FACE", "width is ${bitmap.width} -> x is $x  width -> $width")
        Log.e("FACE", "height is ${bitmap.height} -> y is $y  height -> $height")
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            x,
            y,
            if (y + height > bitmap.height) bitmap.height - y else height,
            if (x + width > bitmap.width) bitmap.width - x else width
        )
        croppedImage.setImageBitmap(croppedBitmap)

    }

    companion object {

        fun resizeImage(imageFile: File, context: Context, uri: Uri?, view: ImageView): Bitmap? {
            val options = BitmapFactory.Options()
            return try {
                decodeStream(context.contentResolver.openInputStream(uri!!), null, options)
                val photoW = options.outWidth
                val photoH = options.outHeight
                options.inSampleSize = (photoW / view.width).coerceAtMost(photoH / view.height)
                compressImage(imageFile,
                    decodeStream(context.contentResolver.openInputStream(
                        uri), null, options))
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                null
            }
        }

        fun resizeImage(imageFile: File, path: String?, view: ImageView): Bitmap? {
            val options = BitmapFactory.Options()
            val photoW = options.outWidth
            val photoH = options.outHeight
            options.inJustDecodeBounds = false
            options.inSampleSize = (photoW / view.width).coerceAtMost(photoH / view.height)
            val bitmap = BitmapFactory.decodeFile(path, options)
            return compressImage(imageFile, bitmap)
        }

        private fun compressImage(imageFile: File, bmp: Bitmap?): Bitmap? {
            try {
                val fos = FileOutputStream(imageFile)
                bmp?.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                fos.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return bmp
        }
    }
}