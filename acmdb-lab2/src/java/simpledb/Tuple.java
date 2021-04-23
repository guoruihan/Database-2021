package simpledb;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Tuple maintains information about the contents of a tuple. Tuples have a
 * specified schema specified by a TupleDesc object and contain Field objects
 * with the data for each field.
 */
public class Tuple implements Serializable {

    private static final long serialVersionUID = 1L;

    private TupleDesc td;
    private RecordId rid;
    private Field[] fieldValues;


    /**
     * Create a new tuple with the specified schema (type).
     *
     * @param td
     *            the schema of this tuple. It must be a valid TupleDesc
     *            instance with at least one field.
     */
    public Tuple(TupleDesc td) {
        this.td = td;
        fieldValues = new Field[td.numFields()];
        // some code goes here
    }

    /**
     * @return The TupleDesc representing the schema of this tuple.
     */
    public TupleDesc getTupleDesc() {

        // some code goes here
        return td;
    }

    /**
     * @return The RecordId representing the location of this tuple on disk. May
     *         be null.
     */
    public RecordId getRecordId() {
        // some code goes here
        return rid;
    }

    /**
     * Set the RecordId information for this tuple.
     *
     * @param rid
     *            the new RecordId for this tuple.
     */
    public void setRecordId(RecordId rid) {
        this.rid = rid;
        // some code goes here
    }

    /**
     * Change the value of the ith field of this tuple.
     *
     * @param i
     *            index of the field to change. It must be a valid index.
     * @param f
     *            new value for the field.
     */
    public void setField(int i, Field f) {
        this.fieldValues[i] = f;
        // some code goes here
    }

    /**
     * @return the value of the ith field, or null if it has not been set.
     *
     * @param i
     *            field index to return. Must be a valid index.
     */
    public Field getField(int i) {
        return this.fieldValues[i];
        // some code goes here
//        return null;
    }

    /**
     * Returns the contents of this Tuple as a string. Note that to pass the
     * system tests, the format needs to be as follows:
     *
     * column1\tcolumn2\tcolumn3\t...\tcolumnN
     *
     * where \t is any whitespace (except a newline)
     */
    public String toString() {
        StringBuilder finalstr = new StringBuilder();
        for (Field fieldValue : fieldValues) {
            if (finalstr.toString().equals("")) {
                finalstr = new StringBuilder(fieldValue.toString());
            } else {
                finalstr.append("\t").append(fieldValue.toString());
            }
        }

        return finalstr.toString();

        // some code goes here
//        throw new UnsupportedOperationException("Implement this");
    }

    /**
     * @return
     *        An iterator which iterates over all the fields of this tuple
     * */
    public Iterator<Field> fields()
    {
        return new FieldIterator();
//        // some code goes here
//        return null;
    }

    private class FieldIterator implements Iterator<Field> {
        int cur = 0;

        @Override
        public boolean hasNext() {
            return cur < fieldValues.length;
        }

        @Override
        public Field next() {
            return fieldValues[cur++];
        }
    }

    /**
     * reset the TupleDesc of thi tuple
     * */
    public void resetTupleDesc(TupleDesc td)
    {
        this.td = td;
        fieldValues = new Field[td.numFields()];
        // some code goes here
    }
}
