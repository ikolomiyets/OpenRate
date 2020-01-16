package OpenRate.cache;

import java.io.Serializable;

/**
 * A TimeMap is the list of intervals that make up the whole map.
 */
public class TimeMap implements Serializable {
    private static final long serialVersionUID = -4558692576850694018L;
    // The vectors for the individual days
    TimeIntervalNode[] Intervals;
}
