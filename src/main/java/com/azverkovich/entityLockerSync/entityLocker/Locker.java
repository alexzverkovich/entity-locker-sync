package com.azverkovich.entityLockerSync.entityLocker;

import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.String.format;

public class Locker implements EntityLocker {

    private static final Logger log = Logger.getLogger(EntityLocker.class);
    private static Locker instance;

    /*this hashMap will keep lock information inside:
    * Object key - ID of the entity(could be any type due to task requirements)
    * IMPORTANT: we need to override equals and hashcode for key class(if it is not String or wrapper on primitives)
    *
    * LockEntry value - entry which keeps info about lock on this entity + waitSet size for this entity(need for clearing this map to avoid memory leaks)
    */
    private ConcurrentHashMap<Object, LockEntry> locksByEntityId;

    /* constructors are thread safe(we cannot call the same constructor from several threads), so doesn't make sense
       to add synchronized here(and Java doesn't allow) */
    private Locker(){

        locksByEntityId = new ConcurrentHashMap<>();
    }

    /* Locker should be single for all app for entities' locks consistency*/
    public static synchronized Locker getInstance(){

        if (instance == null){
            instance = new Locker();
        }
        return instance;
    }

    /* this function acquiers lock and returns lock for mentioned entity id
    *  and we will unlock in the main part of code
    * we must not forget to unlock entities via EntityLocker.unlock() in the main code!
    *
    * if lock is already locked, then thread blocks on this entity
    * reentrancy is reached because we are using ReentrantLock class inside
    *
    * if entity with provided id hasn't been locked before, then the new lock object is created
    *
    */
    public void lock(Object entityId) {

        //we do not need to block on all lock map, because we are working on separate entity
        //so can just block on part of map, which works with current entity


        LockEntry entry = locksByEntityId.computeIfAbsent(entityId, val -> new LockEntry(new ReentrantLock(), 0));

        /* since all methods of LockEntry are synchronized, then we are synchronized on current LockEntry object
        * no other threads can modify current entry's instance waitSet and counter, since we obtained lock on entry.lock object
        *  and do not need synchronized block below
        *
        * also we are not blocking all locksByEntityId map
        */
        //currently we are blocked on lockEntry object until Locker.lock() method finishes
        if (!entry.getLock().tryLock()){
           //if this Thread don't own the lock, increment waitSet counter
           //this will be needed for deleting the lock object from  locksByEntityId to avoid memory leaks

           // increment counter only if thread was not in waitSet before
           //so we need separate waitSet for each lock(entity!)

           if(!entry.waitSetContains(Thread.currentThread())){
               entry.incrementWaitCounter(Thread.currentThread());
           }

        }
    }

    /*
    * if lock on entity won't be accessed in timeout time(lock was owned by another thread, or current thread was interrupted),
    * then the caller thread will continue execution
    * */
    public void lock(Object entityId, long timeout) {

        //we do not need to block on all lock map, because we are working on separate entity
        //so can just block on part of map, which works with current entity
        LockEntry entry = locksByEntityId.computeIfAbsent(entityId, val -> new LockEntry(new ReentrantLock(), 0));

        try {
            if (!entry.getLock().tryLock(timeout, TimeUnit.MILLISECONDS)){
                //if this Thread don't own the lock, increment waitSet counter
                //this will be needed for deleting the lock object from  locksByEntityId to avoid memory leaks

                // increment counter only if thread was not in waitSet before
                //so we need separate waitSet for each lock(entity!)

                if(!entry.waitSetContains(Thread.currentThread())){
                    entry.incrementWaitCounter(Thread.currentThread());
                }

            }
        } catch (InterruptedException e) {
            log.info(format("Thread % was interrupted while waiting lock for entity %s with timeout %s",
                    Thread.currentThread().getName(), entityId, timeout));
        }
    }

    /*if we call EntityLocker.lock() on entity, then we always required to call unlock, in other case other waiting threads never will
    * get access to this entity*/
    public void unlock(Object entityId){
        if (!locksByEntityId.containsKey(entityId)){
            throw new IllegalStateException(format("Thread %s didn't have a lock of entity %s, nothing to unlock!", Thread.currentThread().getName(), entityId));
        }

        LockEntry entry = locksByEntityId.get(entityId);
        ReentrantLock currentLock = entry.getLock();

        if (Thread.holdsLock(currentLock)){
            currentLock.unlock();

            //if we really had lock, tnd then unlocked,also decrement waiting threads counter
            entry.decrementWaitCounter(Thread.currentThread());

            if(entry.isZeroCounter()){
            /*no threads are waiting for this entity lock*/

                if(currentLock.getHoldCount() == 1){
                /*because we are having ReentrantLock then we need to check that we haven't entered this lock several times
                * and holdCount == 1 means that we only once entered this lock by owner thread
                * */
                    /*because no waiting and no re-enterancy, we can remove from map
                    * but entry object by which we are locked still exists, so all is ok with synchronization on entry object(via LockEntry synchronized methods)
                    * */
                    locksByEntityId.remove(entityId);
                }
            }
        }
    }


    private class LockEntry{
        private ReentrantLock lock;
        private int counter; //how many threads waiting for this lock
        private Set<Thread> waitSet; //threads which are waiting to get access to this entity

        public LockEntry(ReentrantLock lock, int counter) {
            this.lock = lock;
            this.counter = counter;
            waitSet = new HashSet<>();
        }

        public synchronized boolean waitSetContains(Thread thread){
            return this.waitSet.contains(thread);
        }

        public synchronized ReentrantLock getLock() {
            return lock;
        }

        public synchronized void incrementWaitCounter(Thread thread) {
            this.counter++;
            this.waitSet.add(thread);
        }

        public synchronized void decrementWaitCounter(Thread thread) {
            this.counter--;
            this.waitSet.remove(thread);
        }

        public synchronized boolean isZeroCounter() {
            return counter == 0;
        }
    }
}
