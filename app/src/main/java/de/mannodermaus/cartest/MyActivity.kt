package de.mannodermaus.cartest

import android.car.Car
import android.car.VehicleAreaSeat
import android.car.VehicleAreaType
import android.car.VehiclePropertyIds
import android.car.VehicleUnit
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                MyScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun MyScreen(modifier: Modifier = Modifier) {
    val car by rememberCar()

    Box(modifier) {
        Image(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(R.drawable.map),
            contentDescription = "Map",
            contentScale = ContentScale.Crop
        )

        TemperatureDisplay(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            car = car,
            label = "Left",
            areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT
        )

        TemperatureDisplay(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            car = car,
            label = "Right",
            areaId = VehicleAreaSeat.SEAT_ROW_1_RIGHT
        )
    }
}

@Composable
private fun TemperatureDisplay(
    car: Car?,
    label: String,
    areaId: Int,
    modifier: Modifier = Modifier
) {
    val carPropertyManager = car?.getCarManager(CarPropertyManager::class.java) ?: return

    val displayUnit by carPropertyManager.collectPropertyAsState<Int>(
        propertyId = VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
        areaId = VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
    )

    val temperature by carPropertyManager.collectPropertyAsState<Float>(
        propertyId = VehiclePropertyIds.HVAC_TEMPERATURE_SET,
        areaId = areaId,
        key = displayUnit
    ) { config, value ->
        // The underlying car property always returns temperature in Celsius.
        // Convert it manually if the display unit is Fahrenheit using the car property config
        when (displayUnit) {
            VehicleUnit.CELSIUS -> value
            VehicleUnit.FAHRENHEIT -> value.toFahrenheit(config.configArray)
            else -> 0f
        }
    }

    val formattedTemperature = remember(temperature, displayUnit) {
        when (displayUnit) {
            VehicleUnit.CELSIUS -> "$temperature °C"
            VehicleUnit.FAHRENHEIT -> "$temperature °F"
            else -> "?"
        }
    }

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium
            )
            .padding(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = formattedTemperature,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
private fun rememberCar(): State<Car?> {
    val context = LocalContext.current
    val carState = remember { mutableStateOf<Car?>(null) }

    DisposableEffect(Unit) {
        val handlerThread = HandlerThread("car").also(HandlerThread::start)
        val handler = Handler(handlerThread.looper)
        Car.createCar(
            /* context = */ context,
            /* handler = */ handler,
            /* waitTimeoutMs = */ 3_000
        ) { car, ready ->
            carState.value = if (ready) car else null
        }

        onDispose {
            carState.value?.disconnect()
            handlerThread.quit()
        }
    }

    return carState
}

@Suppress("UNCHECKED_CAST")
@Composable
private inline fun <reified T> CarPropertyManager.collectPropertyAsState(
    propertyId: Int,
    areaId: Int,
    key: Any? = Unit,
    crossinline valueMapper: (CarPropertyConfig<T>, T) -> T = { _, value -> value }
): State<T?> {
    val state = remember { mutableStateOf<T?>(null) }

    DisposableEffect(propertyId, areaId, key) {
        val config = getCarPropertyConfig(propertyId) as CarPropertyConfig<T>

        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(p0: CarPropertyValue<*>) {
                state.value = valueMapper(config, p0.value as T)
            }

            override fun onErrorEvent(p0: Int, p1: Int) {
                Log.e("cartest", "Error: $p0, $p1")
                state.value = null
            }
        }

        subscribePropertyEvents(
            /* propertyId = */ propertyId,
            /* areaId = */ areaId,
            /* updateRateHz = */ CarPropertyManager.SENSOR_RATE_ONCHANGE,
            /* carPropertyEventCallback = */ callback
        )

        onDispose {
            unsubscribePropertyEvents(callback)
        }
    }

    return state
}

private fun Float.toFahrenheit(configArray: List<Int>): Float {
    // Reference for this conversion is found at:
    // https://developer.android.com/reference/android/car/VehiclePropertyIds#HVAC_TEMPERATURE_SET
    val minTempC = configArray[0] / 10f
    val incrementC = configArray[2] / 10f
    val minTempF = configArray[3] / 10f
    val incrementF = configArray[5] / 10f

    // Round to the closest increment
    val numIncrements = ((this - minTempC) / incrementC).roundToInt()
    return incrementF * numIncrements + minTempF
}
