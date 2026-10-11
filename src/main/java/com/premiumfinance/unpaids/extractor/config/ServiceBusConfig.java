package com.premiumfinance.unpaids.extractor.config;

import com.azure.core.amqp.AmqpRetryMode;
import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.AmqpTransportType;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.premiumfinance.unpaids.extractor.infrastructure.servicebus.ServiceBusUnpaidEventSender;
import com.premiumfinance.unpaids.extractor.infrastructure.servicebus.UnpaidEventMessages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One thread-safe sender for the unpaids topic.
 * <ul>
 *   <li>Azure: managed identity (DefaultAzureCredential) and the namespace, no secrets.</li>
 *   <li>Local: a connection string to the Service Bus emulator.</li>
 *   <li>Local against real Azure (profile local-azure): your own Entra login via {@code az login};
 *       needs VPN and private DNS when the namespace is private.</li>
 * </ul>
 * The client connects lazily on first send, so an execution with nothing to publish never opens AMQP.
 */
@Configuration(proxyBeanMethods = false)
public class ServiceBusConfig {

    @Bean(destroyMethod = "close")
    public ServiceBusSenderClient unpaidsSenderClient(ServiceBusProperties properties) {
        var builder = new ServiceBusClientBuilder()
                .retryOptions(retryOptions(properties.retry()))
                .transportType(switch (properties.transport()) {
                    case AMQP -> AmqpTransportType.AMQP;
                    case AMQP_WEB_SOCKETS -> AmqpTransportType.AMQP_WEB_SOCKETS;
                });

        if (properties.usesConnectionString()) {
            builder.connectionString(properties.connectionString());
        } else {
            var credential = new DefaultAzureCredentialBuilder();
            if (properties.hasManagedIdentityClientId()) {
                credential.managedIdentityClientId(properties.managedIdentityClientId());
            }
            builder.fullyQualifiedNamespace(properties.fullyQualifiedNamespace()).credential(credential.build());
        }
        return builder.sender().topicName(properties.topic()).buildClient();
    }

    @Bean
    public ServiceBusUnpaidEventSender unpaidEventSender(ServiceBusSenderClient unpaidsSenderClient,
                                                         UnpaidEventMessages messages) {
        return new ServiceBusUnpaidEventSender(unpaidsSenderClient, messages);
    }

    private static AmqpRetryOptions retryOptions(ServiceBusProperties.Retry retry) {
        return new AmqpRetryOptions()
                .setMode(AmqpRetryMode.EXPONENTIAL)
                .setMaxRetries(retry.maxRetries())
                .setDelay(retry.delay())
                .setMaxDelay(retry.maxDelay())
                .setTryTimeout(retry.tryTimeout());
    }
}
