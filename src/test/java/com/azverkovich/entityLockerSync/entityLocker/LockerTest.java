package com.azverkovich.entityLockerSync.entityLocker;


import com.azverkovich.entityLockerSync.model.UserEntity;
import org.apache.commons.lang3.RandomUtils;
import org.apache.log4j.Logger;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newLinkedList;
import static java.lang.String.format;
import static java.lang.Thread.sleep;

public class LockerTest {
    public static final Logger log = Logger.getLogger(LockerTest.class); 

    public static final int NUMBER_OF_THREADS = 10;
    public static final int USER_ENTITIES_NUMBER = 20;
    public static final int OPERATIONS_NUMBER = 100;

    @Test
    public void testGlobal() throws InterruptedException {
        List<UserEntity> users = newArrayList();
        IntStream.range(0, USER_ENTITIES_NUMBER).forEach(
                i -> users.add(new UserEntity(format("user%s", i), format("userName%s", i)))
        );

        EntityLocker locker = Locker.getInstance();

        ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

        List<Callable<Object>> tasks = newLinkedList();
        IntStream.range(0, OPERATIONS_NUMBER).forEach( i ->
                tasks.add(new Callable<Object>() {
                    public Future<Object> call() {
                        try {
                            UserEntity user = users.get(RandomUtils.nextInt(0, USER_ENTITIES_NUMBER));
                            synchronized (locker.lock(user.getId())) {

                                user.setName("userName_" + Thread.currentThread().getName());

                                sleep(2 * 500l);

                                log.info(format("Thread %s set username %s for user %s", Thread.currentThread().getName(), user.getName(), user.getId()));

                            }
                        } catch (InterruptedException e) {
                            log.info(format("Thread %s was interrupted", Thread.currentThread().getName()));
                            Thread.currentThread().interrupt();
                        }

                        return null;
                    }
                })
        );
        executorService.invokeAll(tasks);
        executorService.shutdown();
    }

    @Test
    public void testTwoThreadsAcquireOneEntity() throws InterruptedException {

        UserEntity user = new UserEntity("user1", "initialName");
        EntityLocker locker = Locker.getInstance();

        Thread thread1 =  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (locker.lock(user.getId())) {

                        user.setName("userName_" + Thread.currentThread().getName());

                        sleep(1 * 5000l);

                        log.info(format("Thread %s set username %s for user %s", Thread.currentThread().getName(), user.getName(), user.getId()));

                    }
                } catch (InterruptedException e) {
                    log.info(format("Thread %s was interrupted", Thread.currentThread().getName()));
                    Thread.currentThread().interrupt();
                }
            }
        });

        Thread thread2 =  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (locker.lock(user.getId())) {

                        user.setName("userName_" + Thread.currentThread().getName());

                        sleep(2 * 5000l);

                        log.info(format("Thread %s set username %s for user %s", Thread.currentThread().getName(), user.getName(), user.getId()));

                    }
                } catch (InterruptedException e) {
                    log.info(format("Thread %s was interrupted", Thread.currentThread().getName()));
                    Thread.currentThread().interrupt();
                }
            }
        });

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        log.info(user);
    }
}
