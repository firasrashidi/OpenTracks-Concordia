package de.dennisguse.opentracks.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.concurrent.TimeUnit;

import de.dennisguse.opentracks.data.models.TrackPoint;

/**
 * Estimates the altitude gain and altitude loss using the device's pressure
 * sensor (i.e., barometer).
 */
public class AltitudeSumManager implements SensorEventListener {

    private static final String TAG = AltitudeSumManager.class.getSimpleName();

    private boolean isConnected = false;

    private float lastAcceptedPressureValueHpa;

    private float lastSeenSensorValueHpa;

    private Float altitudeGainMetres;
    private Float altitudeLossMetres;

    public void start(Context context, Handler handler) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        Sensor pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (pressureSensor == null) {
            Log.w(TAG, "No pressure sensor available.");
            isConnected = false;
        } else {
            isConnected = sensorManager.registerListener(this, pressureSensor, (int) TimeUnit.SECONDS.toMicros(5),
                    handler);
        }

        lastAcceptedPressureValueHpa = Float.NaN;
        reset();
    }

    public void stop(Context context) {
        Log.d(TAG, "Stop");

        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(this);

        isConnected = false;
        reset();
    }

    @VisibleForTesting
    public void setConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }

    public void fill(@NonNull TrackPoint trackPoint) {
        trackPoint.setAltitudeGain(altitudeGainMetres);
        trackPoint.setAltitudeLoss(altitudeLossMetres);
    }

    @Nullable
    public Float getAltitudeGain_m() {
        return isConnected ? altitudeGainMetres : null;
    }

    @VisibleForTesting
    public void setAltitudeGain_m(float altitudeGainMetres) {
        this.altitudeGainMetres = altitudeGainMetres;
    }

    @VisibleForTesting
    public void addAltitudeGain_m(float altitudeGainMetres) {
        this.altitudeGainMetres = this.altitudeGainMetres == null ? 0f : this.altitudeGainMetres;
        this.altitudeGainMetres += altitudeGainMetres;
    }

    @VisibleForTesting
    public void addAltitudeLoss_m(Float altitudeLossMetres) {
        this.altitudeLossMetres = this.altitudeLossMetres == null ? 0f : this.altitudeLossMetres;
        this.altitudeLossMetres += altitudeLossMetres;
    }

    @Nullable
    public Float getAltitudeLoss_m() {
        return isConnected ? altitudeLossMetres : null;
    }

    @VisibleForTesting
    public void setAltitudeLoss_m(float altitudeLossMetres) {
        this.altitudeLossMetres = altitudeLossMetres;
    }

    public void reset() {
        Log.d(TAG, "Reset");
        altitudeGainMetres = null;
        altitudeLossMetres = null;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.w(TAG, "Sensor accuracy changes are (currently) ignored.");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isConnected) {
            Log.w(TAG, "Not connected to sensor, cannot process data.");
            return;
        }
        onSensorValueChanged(event.values[0]);
    }

    @VisibleForTesting
    void onSensorValueChanged(float valueHpa) {
        if (Float.isNaN(lastAcceptedPressureValueHpa)) {
            lastAcceptedPressureValueHpa = valueHpa;
            lastSeenSensorValueHpa = valueHpa;
            return;
        }

        altitudeGainMetres = altitudeGainMetres != null ? altitudeGainMetres : 0;
        altitudeLossMetres = altitudeLossMetres != null ? altitudeLossMetres : 0;

        PressureSensorUtils.AltitudeChange altitudeChange = PressureSensorUtils
                .computeChangesWithSmoothing_m(lastAcceptedPressureValueHpa, lastSeenSensorValueHpa, valueHpa);
        if (altitudeChange != null) {
            altitudeGainMetres += altitudeChange.getAltitudeGain_m();

            altitudeLossMetres += altitudeChange.getAltitudeLoss_m();

            lastAcceptedPressureValueHpa = altitudeChange.getCurrentSensorValue_hPa();
        }

        lastSeenSensorValueHpa = valueHpa;

        Log.v(TAG, "altitude gain: " + altitudeGainMetres + ", altitude loss: " + altitudeLossMetres);
    }
}
