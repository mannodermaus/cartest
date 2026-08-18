package de.mannodermaus.cartest

import android.car.Car
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
        // TODO: Accessing CarPropertyManager properties requires permissions
        //  that cannot be requested by ordinary apps. Check if signing as privileged app works
        val car = rememberCar()

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
