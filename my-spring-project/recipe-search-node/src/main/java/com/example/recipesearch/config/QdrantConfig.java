package com.example.recipesearch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;

@Configuration
public class QdrantConfig {

    @Bean(destroyMethod = "close")
    QdrantClient qdrantClient(RecipeSearchProperties properties) {
        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(properties.qdrant().host(), properties.qdrant().port());

        if (properties.qdrant().tls()) channelBuilder.useTransportSecurity();
        else channelBuilder.usePlaintext();

        ManagedChannel channel = channelBuilder.build();
        QdrantGrpcClient.Builder grpcBuilder = QdrantGrpcClient.newBuilder(channel, true);
        if (properties.qdrant().apiKey() != null && !properties.qdrant().apiKey().isBlank()) {
            grpcBuilder.withApiKey(properties.qdrant().apiKey());
        }
        return new QdrantClient(grpcBuilder.build());
    }
}
