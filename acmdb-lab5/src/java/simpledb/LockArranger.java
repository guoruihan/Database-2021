package simpledb;

import java.util.*;
import java.util.concurrent.*;

public class LockArranger {
    private final ConcurrentHashMap<PageId, Object> locks;
    private final ConcurrentHashMap<PageId, TransactionId> exLocks;
    private final ConcurrentHashMap<PageId, ConcurrentLinkedDeque<TransactionId>> shLocks;
    private final ConcurrentHashMap<TransactionId, ConcurrentLinkedDeque<PageId>> trWithLocks;
    private final ConcurrentHashMap<TransactionId, ConcurrentLinkedDeque<PageId>> trWithExLocks;
    private final ConcurrentHashMap<TransactionId, ConcurrentLinkedDeque<TransactionId>> dependencyGraph;

    private LockArranger() {
        locks = new ConcurrentHashMap<>();
        shLocks = new ConcurrentHashMap<>();
        exLocks = new ConcurrentHashMap<>();
        trWithLocks = new ConcurrentHashMap<>();
        trWithExLocks = new ConcurrentHashMap<>();
        dependencyGraph = new ConcurrentHashMap<>();
    }

    public static LockArranger GetlockArranger() {
        return new LockArranger();
    }

    private boolean checkEx(TransactionId tid, PageId pid){
        return exLocks.containsKey(pid) && tid.equals(exLocks.get(pid));
    }

    private boolean checkSh(TransactionId tid, PageId pid){
        return shLocks.containsKey(pid) && shLocks.get(pid).contains(tid);
    }

    private boolean hasLock(TransactionId tid, PageId pid, boolean isReadOnly) {
        if (checkEx(tid, pid)) {
            return true;
        }
        if (isReadOnly){
            return checkSh(tid, pid);
        }
        return false;
    }

    private Object getLock(PageId pid) {
        locks.putIfAbsent(pid, new Object());
        return locks.get(pid);
    }

    public boolean acquireLock(TransactionId tid, PageId pid, Permissions perm) throws TransactionAbortedException {
        if (perm == Permissions.READ_ONLY) {
            if (hasLock(tid, pid, true)) return true;
            acquireShLock(tid, pid);
        } else if (perm == Permissions.READ_WRITE) {
            if (hasLock(tid, pid, false)) return true;
            acquireExLock(tid, pid);
        }
        updateTransactionLocks(tid, pid);
        return true;
    }

    private void acquireShLock(TransactionId tid, PageId pid) throws TransactionAbortedException {
        Object lock = getLock(pid);

        while (true) {
            synchronized (lock) {
                TransactionId holder = exLocks.get(pid);
                boolean notBlocked = (holder == null || holder.equals(tid));

                if (notBlocked) {
                    removeDependency(tid);
                    addShTransaction(pid, tid);
                    return;
                }
                ArrayList<TransactionId> holders = new ArrayList<>();
                holders.add(holder);
                updateDependency(tid, holders);
            }
        }
    }

    private void acquireExLock(TransactionId tid, PageId pid) throws TransactionAbortedException {
        Object lock = getLock(pid);

        while (true) {
            synchronized (lock) {
                ArrayList<TransactionId> holders = new ArrayList<>();
                if (exLocks.containsKey(pid)) {
                    holders.add(exLocks.get(pid));
                }
                if (shLocks.containsKey(pid)) {
                    holders.addAll(shLocks.get(pid));
                }

                boolean notBlocked = holders.size() == 0 || (holders.size() == 1 && holders.get(0).equals(tid));

                if (notBlocked) {
                    removeDependency(tid);
                    addExTransaction(pid, tid);
                    return;
                }
                updateDependency(tid, holders);
            }
        }
    }

    private void addShTransaction(PageId pid, TransactionId tid) {
        shLocks.putIfAbsent(pid, new ConcurrentLinkedDeque<>());
        shLocks.get(pid).add(tid);
    }

    private void addExTransaction(PageId pid, TransactionId tid) {
        exLocks.put(pid, tid);
        trWithExLocks.putIfAbsent(tid, new ConcurrentLinkedDeque<>());
        trWithExLocks.get(tid).add(pid);
    }


    private void removeDependency(TransactionId tid) {
        // each time only one thread will execute the function
        // the restriction is on dependencyGraph
        synchronized (dependencyGraph) {
            dependencyGraph.remove(tid);
            for (TransactionId curtid : dependencyGraph.keySet()) {
                dependencyGraph.get(curtid).remove(tid);
            }
        }
    }

    private void updateDependency(TransactionId acquirer, ArrayList<TransactionId> holders)
            throws TransactionAbortedException {
        dependencyGraph.putIfAbsent(acquirer, new ConcurrentLinkedDeque<>());
        boolean hasChange = false;
        ConcurrentLinkedDeque<TransactionId> childs = dependencyGraph.get(acquirer);
        for (TransactionId holder : holders) {
            if (!childs.contains(holder) && !holder.equals(acquirer)) {
                hasChange = true;
                dependencyGraph.get(acquirer).add(holder);
            }
        }
        if (hasChange) {
            checkDeadLock(acquirer, new HashSet<>());
        }
    }


    private void checkDeadLock(TransactionId root, HashSet<TransactionId> visit) throws TransactionAbortedException {
        // DFS Checking self-loop
        if (!dependencyGraph.containsKey(root))
            return;
        for (TransactionId child : dependencyGraph.get(root)) {
            if (visit.contains(child)) {
                throw new TransactionAbortedException();
            }
            visit.add(child);
            checkDeadLock(child, visit);
            visit.remove(child);
        }
    }

    private void updateTransactionLocks(TransactionId tid, PageId pid) {
        trWithLocks.putIfAbsent(tid, new ConcurrentLinkedDeque<>());
        trWithLocks.get(tid).add(pid);
    }


    public void releasePage(TransactionId tid, PageId pid) {
        if (holdsLock(tid, pid)) {
            Object lock = getLock(pid);
            synchronized (lock) {
                if (shLocks.containsKey(pid)) {
                    shLocks.get(pid).remove(tid);
                }
                if (exLocks.containsKey(pid) && exLocks.get(pid).equals(tid)) {
                    exLocks.remove(pid);
                }
                if (trWithLocks.containsKey(tid)) {
                    trWithLocks.get(tid).remove(pid);
                }
                if (trWithExLocks.containsKey(tid)) {
                    trWithExLocks.get(tid).remove(pid);
                }
            }
        }
    }

    public void releasePages(TransactionId tid) {
        if (trWithLocks.containsKey(tid)) {
            for (PageId pid : trWithLocks.get(tid)) {
                releasePage(tid, pid);
            }
        }
        trWithExLocks.remove(tid);
    }

    public boolean holdsLock(TransactionId tid, PageId pid) {
        if (hasLock(tid, pid, true)){
            return true;
        }
        if (hasLock(tid, pid, false)){
            return true;
        }
        return false;
    }

    public ConcurrentHashMap<TransactionId, ConcurrentLinkedDeque<PageId>> getTransactionDirtiedPages() {
        return trWithExLocks;
    }
}