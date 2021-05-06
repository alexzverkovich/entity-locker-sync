package com.azverkovich.entityLockerSync.entityLocker;

public interface EntityLocker {

    void lock(Object entityId);
    void lock(Object entityId, long timeout);
    void unlock(Object entityId);
}
