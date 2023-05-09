package br.com.dhsabreu.aulaspl




import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.util.Log
import android.view.View
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import kotlinx.coroutines.delay
import kotlin.math.log10
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var TextSPL: AppCompatEditText
    private lateinit var btStart: AppCompatButton
    private lateinit var btStop: AppCompatButton
    private lateinit var alerta: AppCompatEditText
    private lateinit var btPanic: AppCompatButton
    //private lateinit var fusedLocationClient: FusedLocationProviderClient


    private val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 1
    private val referencia = 2e-5

    // Defina a taxa de amostragem e o número de canais
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT

    var dbRuido = Firebase.firestore

    

    var mostraDb = 0.0
    var decibel = ""
    var nivelMaxAlerta = 65.00
    var mediaDb = 0.0
    var limVal = 20

    var array = ArrayList<Double>()



    //looping para captação do audio

    private val handler = Handler(Looper.getMainLooper())

    private val runnable = object : Runnable {
        override fun run() {
            //calculo e função alerta
            TextSPL.text!!.clear()
            mostraDb = calculateSPL()
            Alerta(mostraDb)

            //append no app
            decibel = String.format("%.2f", mostraDb)
            TextSPL.text?.append(decibel)
            //handler.postDelayed(this, 1)

            //calculo médio e envio para banco de dados
            array.add(mostraDb)
            //val data = hashMapOf(
            //    "meansDb" to mostraDb
            //)
            //dbRuido.collection("dadosSonoros")
            //    .document("meansDb")
            //    .set(data)
            //Log.d("MEDIA", mostraDb.toString())

            //pega localizacao

            //if (ActivityCompat.checkSelfPermission(
               //     this,
              //      Manifest.permission.ACCESS_FINE_LOCATION
              //  ) == PackageManager.PERMISSION_GRANTED
          //  ) {
            //    fusedLocationClient.lastLocation
              //      .addOnSuccessListener { location: Location? ->
              //          val latLongString =
              //              "Latitude: ${location?.latitude}\nLongitude: ${location?.longitude}"
             //           Log.d("Localização", latLongString)
                  //  }
          //  } else {
             //   ActivityCompat.requestPermissions(
              //      this,
                 ////   arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                  //  1
             //   )
           // }
            if (array.size > 5) {
                var soma = 0.0
                for (x in array){
                    soma += x
                }

                mediaDb = soma / (array.size)
                var mediaformat = (mediaDb * 100.0).roundToInt() / 100.0
                dbRuido.collection("dadosSonoro")
                    .document()
                    .set(hashMapOf(
                        "data" to FieldValue.serverTimestamp(),
                        "dB" to mediaformat))
                array.clear()


            }

            handler.postDelayed(this, 1)

        }

    }



    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission()
        }

        TextSPL = findViewById(R.id.TextSPL)
        btStart = findViewById(R.id.btStart)
        btStop = findViewById(R.id.btStop)
        alerta = findViewById(R.id.alerta)
        btPanic = findViewById(R.id.btPanic)

        //inicio calculo spl
        handler.postDelayed(runnable, 1)



        btStart.setOnClickListener {
            handler.postDelayed(runnable, 1)
        }

        btStop.setOnClickListener(){
            handler.removeCallbacks(runnable)
        }

        //btPanic.setOnClickListener {
            //if (ActivityCompat.checkSelfPermission(
            //     this,
            //      Manifest.permission.ACCESS_FINE_LOCATION
            //  ) == PackageManager.PERMISSION_GRANTED
            //  ) {
            //    fusedLocationClient.lastLocation
            //      .addOnSuccessListener { location: Location? ->
            //          val latLongString =
            //              "Latitude: ${location?.latitude}\nLongitude: ${location?.longitude}"
            //           Log.d("Localização", latLongString)
            //  }
            //  } else {
            //   ActivityCompat.requestPermissions(
            //      this,
            ////   arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            //  1
            //   )
            // }
            //handler.postDelayed(runnable, 1)

       // }
    }

    private fun Alerta (dbValor: Double){
        if (dbValor > nivelMaxAlerta){
            val message = "Alerta de Ambiente Ruidoso \n"
            val messageShow = Editable.Factory.getInstance().newEditable(message)

            alerta.text = messageShow
            alerta.visibility = View.VISIBLE

            Handler().postDelayed({alerta.visibility = View.INVISIBLE}, 5000)
        }
    }
    fun calculateSPL(): Double {
        // Configura a gravação de áudio

        val audioBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission()
        }
        val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, audioBufferSize)
        audioRecord.startRecording()


        // Captura 1 segundo de áudio
        val audioSamples = ShortArray(SAMPLE_RATE)
        var readSize = 0
        while (readSize < SAMPLE_RATE) {
            readSize += audioRecord.read(audioSamples, readSize, SAMPLE_RATE - readSize)
        }

        // Calcula o nível de pressão sonora médio (SPL)
        var rms = 0.0
        for (sample in audioSamples) {
            rms += sample * sample.toDouble()
        }
        rms = Math.sqrt(rms / audioSamples.size)
        val db = 20 * log10(rms / referencia) - 94

        // Libera recursos de gravação de áudio
        audioRecord.stop()
        audioRecord.release()

        // Retorna o valor do SPL em decibéis (dB)
        return db
    }

    fun addElement(arr: DoubleArray, element: Double): DoubleArray {
        val mutableArray = arr.toMutableList()
        mutableArray.add(element)
        return mutableArray.toDoubleArray()
    }


    fun AppCompatActivity.requestRecordAudioPermission() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_PERMISSION_REQUEST_CODE
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Tratar exceção aqui, se necessário
        }
    }
    // Método para manipular a resposta do usuário às solicitações de permissão
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // A permissão foi concedida. Você pode iniciar a gravação de áudio aqui.
            } else {
                // A permissão foi negada. Você pode exibir uma mensagem para o usuário aqui.
            }
        }
    }
}


