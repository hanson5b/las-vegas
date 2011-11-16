package edu.brown.lasvegas;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.Relationship;
import com.sleepycat.persist.model.SecondaryKey;

/**
 * The recovery unit of a table.
 * A Fracture is a conceptual partitioning which defines
 * the subset of tuples from the table.
 * 
 * Fractures are totally separated; all recovery and querying happen per fracture.
 * Technically, two fractures are two different tables which happen
 * to have the same scheme.
 */
@Entity
public class LVTableFracture {
    /**
     * ID of the table this fracture belongs to.
     */
    @SecondaryKey(name="IX_TABLE_ID", relate=Relationship.MANY_TO_ONE, relatedEntity=LVTable.class)
    private int tableId;
    
    /**
     * A unique (system-wide) ID of this fracture.
     */
    @PrimaryKey
    private int fractureId;

    /**
     * Defines the beginning key of this fracture (inclusive).
     * The key of the base group's partitioning column.
     * Could be NULL only when the base group uses automatic-epoch
     * partitioning.
     */
    private Object startKey;

    /**
     * Defines the ending key of this fracture (exclusive).
     * The key of the base group's partitioning column.
     * Could be NULL only when the base group uses automatic-epoch
     * partitioning.
     */
    private Object endKey;

    /**
     * The number of tuples in this fracture.
     * This is just a statistics. Might not be accurate.
     */
    private long tupleCount;
    
    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Fracture-" + fractureId + " in Table-" + tableId
        + ": start=" + startKey + ", end=" + endKey
        + ". tupleCount=" + tupleCount;
    }

// auto-generated getters/setters (comments by JAutodoc)    
    /**
     * Gets the iD of the table this fracture belongs to.
     *
     * @return the iD of the table this fracture belongs to
     */
    public int getTableId() {
        return tableId;
    }

    /**
     * Sets the iD of the table this fracture belongs to.
     *
     * @param tableId the new iD of the table this fracture belongs to
     */
    public void setTableId(int tableId) {
        this.tableId = tableId;
    }

    /**
     * Gets the a unique (system-wide) ID of this fracture.
     *
     * @return the a unique (system-wide) ID of this fracture
     */
    public int getFractureId() {
        return fractureId;
    }

    /**
     * Sets the a unique (system-wide) ID of this fracture.
     *
     * @param fractureId the new a unique (system-wide) ID of this fracture
     */
    public void setFractureId(int fractureId) {
        this.fractureId = fractureId;
    }

    /**
     * Gets the defines the beginning key of this fracture (inclusive).
     *
     * @return the defines the beginning key of this fracture (inclusive)
     */
    public Object getStartKey() {
        return startKey;
    }

    /**
     * Sets the defines the beginning key of this fracture (inclusive).
     *
     * @param startKey the new defines the beginning key of this fracture (inclusive)
     */
    public void setStartKey(Object startKey) {
        this.startKey = startKey;
    }

    /**
     * Gets the defines the ending key of this fracture (exclusive).
     *
     * @return the defines the ending key of this fracture (exclusive)
     */
    public Object getEndKey() {
        return endKey;
    }

    /**
     * Sets the defines the ending key of this fracture (exclusive).
     *
     * @param endKey the new defines the ending key of this fracture (exclusive)
     */
    public void setEndKey(Object endKey) {
        this.endKey = endKey;
    }

    /**
     * Gets the number of tuples in this fracture.
     *
     * @return the number of tuples in this fracture
     */
    public long getTupleCount() {
        return tupleCount;
    }

    /**
     * Sets the number of tuples in this fracture.
     *
     * @param tupleCount the new number of tuples in this fracture
     */
    public void setTupleCount(long tupleCount) {
        this.tupleCount = tupleCount;
    }
    
}
