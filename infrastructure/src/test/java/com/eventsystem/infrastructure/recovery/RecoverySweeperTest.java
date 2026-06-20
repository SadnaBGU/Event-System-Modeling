package com.eventsystem.infrastructure.recovery;

import com.eventsystem.application.order.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecoverySweeperTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private RecoverySweeper recoverySweeper;

    @Test
    void sweep_delegatesToOrderService() {
        recoverySweeper.sweep();

        verify(orderService, times(1)).sweepExpiredOrders();
    }

    @Test
    void sweep_swallowsFailures_soSchedulerThreadSurvives() {
        doThrow(new RuntimeException("db down")).when(orderService).sweepExpiredOrders();

        // A failing sweep must not propagate, otherwise the scheduled thread would stop firing.
        assertThatCode(() -> recoverySweeper.sweep()).doesNotThrowAnyException();
        verify(orderService).sweepExpiredOrders();
    }
}
