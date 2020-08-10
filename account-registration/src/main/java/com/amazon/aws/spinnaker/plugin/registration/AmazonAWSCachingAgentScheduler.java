package com.amazon.aws.spinnaker.plugin.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentProvider;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class AmazonAWSCachingAgentScheduler {
    private final LazyLoadCredentialsRepository lazyLoadCredentialsRepository;
    private final Registry registry;
    private final EddaTimeoutConfig eddaTimeoutConfig;
    private final AmazonClientProvider amazonClientProvider;
    private final AwsProvider awsProvider;
    private final AmazonCloudProvider amazonCloudProvider;
    private final AmazonS3DataProvider amazonS3DataProvider;
    private final ObjectMapper objectMapper;
    private final EddaApiFactory eddaApiFactory;
    private final ApplicationContext ctx;
    private final Optional<ExecutorService> reservationReportPool;
    private final Collection<AgentProvider> agentProviders;
    private final DynamicConfigService dynamicConfigService;

    @Autowired
    public AmazonAWSCachingAgentScheduler(LazyLoadCredentialsRepository lazyLoadCredentialsRepository,
                                          AmazonClientProvider amazonClientProvider,
                                          Registry registry, EddaTimeoutConfig eddaTimeoutConfig, AwsProvider awsProvider,
                                          AmazonCloudProvider amazonCloudProvider, AmazonS3DataProvider amazonS3DataProvider,
                                          ObjectMapper objectMapper, EddaApiFactory eddaApiFactory, ApplicationContext ctx,
                                          Optional<ExecutorService> reservationReportPool, Collection<AgentProvider> agentProviders,
                                          DynamicConfigService dynamicConfigService
    ) {
        this.lazyLoadCredentialsRepository = lazyLoadCredentialsRepository;
        this.amazonClientProvider = amazonClientProvider;
        this.registry = registry;
        this.eddaTimeoutConfig = eddaTimeoutConfig;
        this.awsProvider = awsProvider;
        this.amazonCloudProvider = amazonCloudProvider;
        this.amazonS3DataProvider = amazonS3DataProvider;
        this.objectMapper = objectMapper;
        this.eddaApiFactory = eddaApiFactory;
        this.ctx = ctx;
        this.reservationReportPool = reservationReportPool;
        this.agentProviders = agentProviders;
        this.dynamicConfigService = dynamicConfigService;
    }

    @Scheduled(fixedDelayString = "${accountProvision.syncAgentFrequencyInMilliSeconds:10000}")
    public void synchronizeAwsProvider() {
        AmazonProviderUtils.synchronizeAwsProvider(awsProvider, amazonCloudProvider, amazonClientProvider,
                amazonS3DataProvider, lazyLoadCredentialsRepository, objectMapper, eddaApiFactory, ctx,
                registry, reservationReportPool, agentProviders, eddaTimeoutConfig, dynamicConfigService);
    }
}
