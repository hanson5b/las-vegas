package edu.brown.lasvegas;

/**
 * Defines possible status of a job ({@link LVJob}).
 */
public enum JobStatus {
    /** Just created, not yet started nor requested to start. */
    CREATED,
    /** Has been requested to start the job. */
    REQUESTED,
    /** The start-request has been received and the job is being processed. */
    RUNNING,
    /** Successfully finished. */
    DONE,
    /** Exited with an error. */
    ERROR,
    /** Canceled per user request. */
    CANCELED,
    /** kind of null. */
    INVALID,
    ;

    /** tells if this status is one of "finished" status.*/
    public static boolean isFinished (JobStatus status) {
        return status == DONE || status == CANCELED || status == ERROR; 
    }
}
