package OpenRate.cache;

import java.io.Serializable;

/**
 * A TimeInterval is a part of a time model. A model is made up of a list of
 * intervals that cover the whole of the possible 24 hour x 7 days
 */
public class TimeIntervalNode implements Serializable {

    private static final long serialVersionUID = 1124921403136789577L;
    int TimeFrom;
    int TimeTo;
    String Result = "NEW";
    TimeIntervalNode child = null;
}
