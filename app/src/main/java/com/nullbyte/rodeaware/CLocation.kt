import android.location.Location

class CLocation : Location {
    constructor(location: Location?) : super(location!!)

    constructor(provider: String?) : super(provider)

    override fun getAccuracy(): Float {
        return super.getAccuracy()
    }

    override fun distanceTo(dest: Location): Float {
        return super.distanceTo(dest)
    }

    override fun getAltitude(): Double {
        return super.getAltitude()
    }

    override fun getSpeed(): Float {
        // Convert speed from meters/second to kilometers/hour
        return super.getSpeed() * 3.6f
    }
}
