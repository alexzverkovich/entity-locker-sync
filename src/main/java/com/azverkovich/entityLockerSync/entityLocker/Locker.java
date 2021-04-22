package com.azverkovich.entityLockerSync.entityLocker;

import java.util.concurrent.ConcurrentHashMap;

public class Locker implements EntityLocker {

    private static Locker instance;

    /*this concurrent hashMap will keep lock information inside:
    * Object key - ID of the entity(could be any type due to task requirements
    * Object value - abstract object which is used as java monitor for this entity
    */
    private ConcurrentHashMap<Object, Object> locksByEntityId;

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

    /* this function acquers monitor and returns monitor for mentioned entity id
    * if monitor is not available, then thread blocks on this monitor
    *
    * if entity with provided id hasn't been locked before, then the new monitor object is created
    *
    * we do not need to use additional synchronization here, because we are using concurrentHashMap as a lock storage*/
    public Object lock(Object entityId) {
        return locksByEntityId.computeIfAbsent(entityId, val -> new Object());
    }
}
