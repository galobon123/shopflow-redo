package com.Proyect.Ecommerce.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.repository.WishListRepository;

/** HU-14 · A restart while Neo4j is slow doesn't lose the syncs waiting in memory, against real Postgres and Redis. */
@IntegrationTest
class WishlistGraphSyncShutdownTest {

    @Autowired private WishListRepository wishListRepository;
    @Autowired private StringRedisTemplate redis;

    @BeforeEach
    void emptyQueues() {
        redis.delete(WishlistGraphSync.PENDING);
    }

    @Test
    void onShutdownTheWaitingAndTheRunningSyncsGoToTheRetryList() throws Exception {
        // Neo4j that hangs until the thread is interrupted. Its own instance: the application's keeps running.
        RecommendationService hungNeo4j = mock(RecommendationService.class);
        CountDownLatch started = new CountDownLatch(1);
        doAnswer(call -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                throw new IllegalStateException("Neo4j no responde", e);
            }
            return null;
        }).when(hungNeo4j).syncWishlistRemove(anyLong(), anyLong());
        WishlistGraphSync sync = new WishlistGraphSync(wishListRepository, hungNeo4j, redis);

        sync.onWishlistChanged(new WishlistChanged(7L, 1L));
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        sync.onWishlistChanged(new WishlistChanged(7L, 2L));
        sync.onWishlistChanged(new WishlistChanged(7L, 3L));

        sync.stop();

        assertThat(redis.opsForList().range(WishlistGraphSync.PENDING, 0, -1))
                .containsExactlyInAnyOrder("7:1:0", "7:2:0", "7:3:0");
    }
}
