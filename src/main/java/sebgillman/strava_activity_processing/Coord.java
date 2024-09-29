package sebgillman.strava_activity_processing;

public class Coord {

    private double longitude;
    private double latitude;

    public Double getLat() {
        return latitude;
    }

    public void setLat(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLong() {
        return longitude;
    }

    public void setLong(Double longitude) {
        this.longitude = longitude;
    }
}
