package dev.herald.worker.queue;

import dev.herald.worker.config.WorkerProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;

/**
 * SQS-backed queue. When fair queues are enabled every message carries {@code MessageGroupId =
 * tenantId}; on a standard queue SQS then keeps one tenant's backlog from inflating the dwell time
 * of everyone else's messages.
 */
@Component
class SqsDeliveryQueue implements DeliveryQueue {

    private static final Logger log = LoggerFactory.getLogger(SqsDeliveryQueue.class);
    private static final int SQS_BATCH_LIMIT = 10;

    private final SqsClient sqs;
    private final boolean fairQueues;
    private final String queueUrl;

    SqsDeliveryQueue(SqsClient sqs, WorkerProperties properties) {
        this.sqs = sqs;
        this.fairQueues = properties.queue().fairQueues();
        this.queueUrl = resolveQueueUrl(sqs, properties.queue());
    }

    @Override
    public void send(List<Message> messages) {
        for (int from = 0; from < messages.size(); from += SQS_BATCH_LIMIT) {
            List<Message> chunk = messages.subList(from, Math.min(from + SQS_BATCH_LIMIT, messages.size()));
            List<SendMessageBatchRequestEntry> entries = new ArrayList<>(chunk.size());
            for (int i = 0; i < chunk.size(); i++) {
                Message message = chunk.get(i);
                var entry = SendMessageBatchRequestEntry.builder().id(Integer.toString(i)).messageBody(message.deliveryId().toString());
                if (fairQueues) {
                    entry.messageGroupId(message.tenantId().toString());
                }
                entries.add(entry.build());
            }
            SendMessageBatchResponse response = sqs.sendMessageBatch(b -> b.queueUrl(queueUrl).entries(entries));
            if (!response.failed().isEmpty()) {
                // Throwing rolls back the dispatch transaction, so the rows stay PENDING and are retried.
                throw new IllegalStateException("SQS rejected " + response.failed().size() + " message(s): " + response.failed().getFirst().message());
            }
        }
    }

    @Override
    public List<Received> receive(int max, Duration wait) {
        return sqs.receiveMessage(b -> b.queueUrl(queueUrl).maxNumberOfMessages(Math.min(max, SQS_BATCH_LIMIT)).waitTimeSeconds((int) wait.toSeconds()))
                .messages()
                .stream()
                .map(m -> new Received(UUID.fromString(m.body()), m.receiptHandle()))
                .toList();
    }

    @Override
    public void acknowledge(Received message) {
        sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receipt()));
    }

    private static String resolveQueueUrl(SqsClient sqs, WorkerProperties.Queue queue) {
        if (queue.createIfMissing()) {
            log.info("Ensuring SQS queue '{}' exists", queue.name());
            return sqs.createQueue(CreateQueueRequest.builder()
                            .queueName(queue.name())
                            .attributes(Map.of(QueueAttributeName.VISIBILITY_TIMEOUT, Long.toString(queue.visibilityTimeout().toSeconds())))
                            .build())
                    .queueUrl();
        }
        return sqs.getQueueUrl(b -> b.queueName(queue.name())).queueUrl();
    }
}
