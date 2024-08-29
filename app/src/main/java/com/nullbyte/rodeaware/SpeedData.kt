package com.nullbyte.rodeaware

class SpeedData {

    var acceleration
            : Double? = null
    var speed // Change to Double
            : Double? = null
    var latitude // Change to Double
            : Double? = null
    var longitude // Change to Double
            : Double? = null


    constructor() {
        // Required empty constructor for Firebase
    }

    constructor(speed: Double?, latitude: Double?, longitude: Double?, timestamp: Long?) {
        this.speed = speed
        this.latitude = latitude
        this.longitude = longitude
    }

    fun setSpeedValue(speedValue: Double) {
        speed = speedValue
    }
}