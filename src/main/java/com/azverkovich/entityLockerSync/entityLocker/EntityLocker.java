package com.azverkovich.entityLockerSync.entityLocker;

public interface EntityLocker {

    Object lock(Object entityId);
}
