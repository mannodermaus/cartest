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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
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

    Row(
        modifier = modifier.padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TemperatureDisplay(
            modifier = Modifier.fillMaxHeight(),
            car = car,
            label = "Left",
            areaId = VehicleAreaSeat.SEAT_ROW_1_LEFT
        )

        Text(
            modifier = Modifier.align(Alignment.CenterVertically),
            text = "HVAC",
            style = MaterialTheme.typography.headlineLarge
        )

        TemperatureDisplay(
            modifier = Modifier.fillMaxHeight(),
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

    val temperaturePair by carPropertyManager.collectPropertyAsState<Float, Pair<Float, Float>>(
        propertyId = VehiclePropertyIds.HVAC_TEMPERATURE_SET,
        areaId = areaId,
        key = displayUnit
    ) { config, value ->
        // The underlying car property always returns temperature in Celsius.
        // If displaying in Fahrenheit, convert the value using the car property's config array
        val displayValue = when (displayUnit) {
            VehicleUnit.CELSIUS -> value
            VehicleUnit.FAHRENHEIT -> value.toFahrenheit(config.configArray)
            else -> 0f
        }

        // The config array also describes the min and max temperature values.
        // Derive a fraction from those
        val valueFraction = value.lerpIn(
            min = config.configArray[0] / 10f,
            max = config.configArray[1] / 10f
        )

        displayValue to valueFraction
    }

    val temperature = temperaturePair?.first
    val temperatureFraction = temperaturePair?.second ?: 0f

    val formattedTemperature = remember(temperature, displayUnit) {
        when (displayUnit) {
            VehicleUnit.CELSIUS -> "$temperature °C"
            VehicleUnit.FAHRENHEIT -> "$temperature °F"
            else -> "?"
        }
    }

    val displayAlpha by animateFloatAsState(if (temperature != null) 1f else 0f)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val foregroundColor1 = Color.Green
    val foregroundColor2 = Color.Red
    // The hotter the HVAC, the more pronounced the background gradient
    val backgroundGradientStop by animateFloatAsState(0.75f - (temperatureFraction * 0.25f))
    val backgroundBrush = remember(temperatureFraction, backgroundGradientStop) {
        Brush.verticalGradient(
            0f to backgroundColor,
            backgroundGradientStop to backgroundColor,
            1f to Color(
                ColorUtils.blendARGB(
                    foregroundColor1.toArgb(),
                    foregroundColor2.toArgb(),
                    temperatureFraction
                )
            )
        )
    }

    Column(
        modifier = modifier
            .alpha(displayAlpha)
            .background(
                brush = backgroundBrush,
                shape = MaterialTheme.shapes.medium
            )
            .padding(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = formattedTemperature,
            style = MaterialTheme.typography.headlineMedium,
            autoSize = TextAutoSize.StepBased()
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

@Composable
private inline fun <reified T> CarPropertyManager.collectPropertyAsState(
    propertyId: Int,
    areaId: Int,
    key: Any? = Unit
): State<T?> = collectPropertyAsState<T, T>(propertyId, areaId, key) { _, value -> value }

@Suppress("UNCHECKED_CAST")
@Composable
private inline fun <reified I, reified O> CarPropertyManager.collectPropertyAsState(
    propertyId: Int,
    areaId: Int,
    key: Any? = Unit,
    crossinline valueMapper: (CarPropertyConfig<I>, I) -> O
): State<O?> {
    val state = remember { mutableStateOf<O?>(null) }

    DisposableEffect(propertyId, areaId, key) {
        val config = getCarPropertyConfig(propertyId) as CarPropertyConfig<I>

        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(p0: CarPropertyValue<*>) {
                state.value = valueMapper(config, p0.value as I)
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

private fun Float.lerpIn(min: Float, max: Float): Float = (this - min) / (max - min)
