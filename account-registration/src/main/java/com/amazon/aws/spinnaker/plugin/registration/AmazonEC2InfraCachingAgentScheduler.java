package com.amazon.aws.spinnaker.plugin.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@DependsOn("netflixAmazonCredentials")
@Slf4j
public class AmazonEC2InfraCachingAgentScheduler {
    private final LazyLoadCredentialsRepository lazyLoadCredentialsRepository;
    private final AwsInfrastructureProvider awsInfrastructureProvider;
    private final AmazonClientProvider amazonClientProvider;
    @Qualifier("amazonObjectMapper")
    private final ObjectMapper amazonObjectMapper;
    private final Registry registry;
    private final EddaTimeoutConfig eddaTimeoutConfig;

    @Autowired
    AmazonEC2InfraCachingAgentScheduler(
            LazyLoadCredentialsRepository lazyLoadCredentialsRepository,
            AwsInfrastructureProvider awsInfrastructureProvider,
            AmazonClientProvider amazonClientProvider, ObjectMapper amazonObjectMapper,
            Registry registry, EddaTimeoutConfig eddaTimeoutConfig
    ) {
        this.lazyLoadCredentialsRepository = lazyLoadCredentialsRepository;
        this.awsInfrastructureProvider = awsInfrastructureProvider;
        this.amazonClientProvider = amazonClientProvider;
        this.amazonObjectMapper = amazonObjectMapper;
        this.registry = registry;
        this.eddaTimeoutConfig = eddaTimeoutConfig;
    }

    @Scheduled(fixedDelayString= "${accountProvision.syncAgentFrequencyInMilliSeconds:10000}")
    public void synchronizeAwsInfrastructureProvider(){
        AmazonProviderUtils.synchronizeAwsInfrastructureProvider(awsInfrastructureProvider, amazonClientProvider,
                lazyLoadCredentialsRepository, amazonObjectMapper, registry, eddaTimeoutConfig);
    }
}


