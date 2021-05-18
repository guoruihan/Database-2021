package simpledb;

import java.io.IOException;

/**
 * The delete operator. Delete reads tuples from its child operator and removes
 * them from the table they belong to.
 */
public class Delete extends Operator {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor specifying the transaction that this delete belongs to as
     * well as the child to read from.
     * 
     * @param t
     *            The transaction this delete runs in
     * @param child
     *            The child operator from which to read tuples for deletion
     */
    private final TransactionId tid;
    private DbIterator child;
    private boolean deleted = false;
    public Delete(TransactionId t, DbIterator child) {
        this.tid = t;
        this.child = child;
    }

    public TupleDesc getTupleDesc() {
        return new TupleDesc(new Type[]{Type.INT_TYPE});
    }

    public void open() throws DbException, TransactionAbortedException {
        super.open();
        child.open();
    }

    public void close() {
        child.close();
        super.close();
    }

    public void rewind() throws DbException, TransactionAbortedException {
        close();
        open();
    }

    /**
     * Deletes tuples as they are read from the child operator. Deletes are
     * processed via the buffer pool (which can be accessed via the
     * Database.getBufferPool() method.
     * 
     * @return A 1-field tuple containing the number of deleted records.
     * @see Database#getBufferPool
     * @see BufferPool#deleteTuple
     */
    protected Tuple fetchNext() throws TransactionAbortedException, DbException {
        if(deleted)
            return null;

        int count = 0;
        while (child.hasNext()) {
            Tuple toDelete = child.next();
            try {
                Database.getBufferPool().deleteTuple(tid, toDelete);
            } catch (IOException e) {
                throw new DbException("Delete: fetchNext: IO error in dbFile delete tuple");
            }
            count += 1;
        }
        deleted = true;
        return Utility.getTuple(new int[]{count}, 1);
    }

    @Override
    public DbIterator[] getChildren() {
        return new DbIterator[]{child};

    }

    @Override
    public void setChildren(DbIterator[] children) {
        this.child = children[0];
    }

}
