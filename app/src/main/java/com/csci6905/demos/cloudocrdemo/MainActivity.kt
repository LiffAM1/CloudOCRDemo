package com.csci6905.demos.cloudocrdemo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Base64.encodeToString
import android.util.Log
import android.widget.*
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.functions.*
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.gson.*
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var functions: FirebaseFunctions
    lateinit var imageView: ImageView
    lateinit var ocrTextView: TextView
    lateinit var loadImageButton: Button
    lateinit var annotateButton: Button
    lateinit var englishHintCheckbox: CheckBox
    lateinit var handwrittenHintCheckbox: CheckBox
    private val pickImage = 100
    private var imageUri: Uri? = null
    private var useEnglishHint = false
    private var useHandwrittenHint = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Initialize Cloud Functions
        functions = Firebase.functions

        // Initialize the UI
        imageView = findViewById(R.id.imageView)
        loadImageButton = findViewById(R.id.buttonLoadPicture)

        // Initially, annotate button is disabled
        annotateButton = findViewById(R.id.buttonAnnotate)
        annotateButton.isEnabled = false

        ocrTextView = findViewById(R.id.textView)
        englishHintCheckbox = findViewById(R.id.englishHintCheckbox)
        handwrittenHintCheckbox = findViewById(R.id.handwrittenHintCheckbox)
        loadImageButton.setOnClickListener {
            val gallery = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            startActivityForResult(gallery, pickImage)
        }
        annotateButton.setOnClickListener {
            sendCloudAPIRequest()
        }
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser == null) {
            signIn()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == pickImage) {
            imageUri = data?.data
            imageView.setImageURI(imageUri)
            annotateButton.isEnabled = true
            ocrTextView.setText(null)
        }
    }

    private fun signIn() {
        auth.signInWithEmailAndPassword("testuser@test.com", "testOCRAccount")
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(baseContext, "Sign in success.",
                        Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun sendCloudAPIRequest() {
        if (imageUri == null) return;
        var base64encoded = loadBitmapAndScale(imageUri!!)
        // Create json request to cloud vision
        val request = JsonObject()
        // Add image to request
        val image = JsonObject()
        image.add("content", JsonPrimitive(base64encoded))
        request.add("image", image)
        //Add features to the request
        val feature = JsonObject()
        feature.add("type", JsonPrimitive("TEXT_DETECTION"))
        // Alternatively, for DOCUMENT_TEXT_DETECTION:
        // feature.add("type", JsonPrimitive("DOCUMENT_TEXT_DETECTION"))
        val features = JsonArray()
        features.add(feature)
        request.add("features", features)

        val imageContext = JsonObject()
        val languageHints = JsonArray()
        if (englishHintCheckbox.isChecked) {
            languageHints.add("en")
        }
        if (handwrittenHintCheckbox.isChecked) {
            languageHints.add("handwrit")
        }
        imageContext.add("languageHints", languageHints)
        request.add("imageContext", imageContext)
        annotateImage(request.toString())
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(baseContext, "Task failed.",
                        Toast.LENGTH_SHORT).show()
                } else {
                    val annotation = task.result!!.asJsonArray[0].asJsonObject["fullTextAnnotation"].asJsonObject
                    Toast.makeText(baseContext,"Annotation complete!",Toast.LENGTH_SHORT).show()
                    ocrTextView.setText(annotation["text"].asString);
                }
            }
    }

    private fun loadBitmapAndScale(path: Uri): String {
        var bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, path)
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = 1024
        var resizedHeight = 768
        var scaledBitmap = Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
        val byteArrayOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val imageBytes: ByteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    private fun annotateImage(requestJson: String): Task<JsonElement> {
        return functions
            .getHttpsCallable("annotateImage")
            .call(requestJson)
            .continueWith { task ->
                // This continuation runs on either success or failure, but if the task
                // has failed then result will throw an Exception which will be
                // propagated down.
                val result = task.result?.data
                JsonParser.parseString(Gson().toJson(result))
            }
    }

}