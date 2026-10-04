package lk.ac.sliit.tgms.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class NotificationDispatchServiceTests {

    @Test
    void executorFailureIsContainedSoBusinessCallerIsNotFailed() {
        NotificationWriterService writer = mock(NotificationWriterService.class);
        Executor rejectingExecutor = command -> { throw new RejectedExecutionException("full"); };
        NotificationDispatchService dispatcher =
                new NotificationDispatchService(writer, rejectingExecutor, true);

        assertThatCode(() -> dispatcher.orderStatus(10, 20, "ORD-20", lk.ac.sliit.tgms.order.OrderStatus.CONFIRMED))
                .doesNotThrowAnyException();
    }
}
