package sebgillman.strava_activity_processing;

import java.util.List;

public class ProcessActivityReqBody {

    private Double userId;
    private Double activityId;
    private List<List<Double>> coords;

    public Double getUserId() {
        return userId;
    }

    public void setUserId(Double userId) {
        this.userId = userId;
    }

    public Double getActivityId() {
        return activityId;
    }

    public void setActivityId(Double activityId) {
        this.activityId = activityId;
    }

    public List<List<Double>> getCoords() {
        return coords;
    }

    public void setCoords(List<List<Double>> coords) {
        this.coords = coords;
    }
}
