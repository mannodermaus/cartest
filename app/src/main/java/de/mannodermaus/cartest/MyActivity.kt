package de.mannodermaus.cartest

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LifecycleResumeEffect

class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                MyScreen(Modifier.fillMaxSize())
            }
        }
    }

    @Composable
    private fun MyScreen(modifier: Modifier = Modifier) {
        val car = rememberCar()
        LaunchedEffect(car) {
            val car = car
            if (car != null) {
                val manager = car.getCarManager(CarPropertyManager::class.java)
                try {
                    val temp = manager.getFloatProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET, 0x1)
                    Log.d("cartest", "HVACtemp = $temp")
                } catch (e: Exception) {
                    Log.e("cartest", "error = ${e.message}")
                }
            }
        }

        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(R.drawable.map),
                contentDescription = "Map",
                contentScale = ContentScale.Crop
            )
        }
    }

    @Composable
    private fun rememberCar(): Car? {
        val context = LocalContext.current
        val carState = remember { mutableStateOf<Car?>(null) }

        LifecycleResumeEffect(Unit) {
            val handlerThread = HandlerThread("car").also(HandlerThread::start)
            val handler = Handler(handlerThread.looper)
            Car.createCar(
                /* context = */ context,
                /* handler = */ handler,
                /* waitTimeoutMs = */ 3_000
            ) { car, _ ->
                carState.value = car
            }

            onPauseOrDispose {
                carState.value?.disconnect()
                handlerThread.quit()
            }
        }

        return carState.value
    }
}
