package com.amazon.aws.spinnaker.plugin.registration;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.*;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class AmazonECSCachingAgentScheduler {
    private final LazyLoadCredentialsRepository lazyLoadCredentialsRepository;
    private final AmazonClientProvider amazonClientProvider;
    private final ObjectMapper objectMapper;
    private final Registry registry;
    private final EcsProvider ecsProvider;
    private final AWSCredentialsProvider awsCredentialsProvider;
    private final IamPolicyReader iamPolicyReader;


    @Autowired
    public AmazonECSCachingAgentScheduler( LazyLoadCredentialsRepository lazyLoadCredentialsRepository,
                                           AmazonClientProvider amazonClientProvider, ObjectMapper objectMapper,
                                           EcsProvider ecsProvider, AWSCredentialsProvider awsCredentialsProvider,
                                           IamPolicyReader iamPolicyReader, Registry registry

    ) {
        this.lazyLoadCredentialsRepository = lazyLoadCredentialsRepository;
        this.amazonClientProvider = amazonClientProvider;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.ecsProvider = ecsProvider;
        this.awsCredentialsProvider = awsCredentialsProvider;
        this.iamPolicyReader = iamPolicyReader;
    }

    // Sync ECS accounts. Code is mostly from com/netflix/spinnaker/clouddriver/ecs/security/EcsCredentialsInitializer.java
    @Scheduled(fixedDelayString= "${accountProvision.syncAgentFrequencyInMilliSeconds:10000}")
    public void synchronizeEcsProvider() {
        // Cannot use ProviderUtils.getScheduledAccounts. EcsProvider does not implement AccountAware.
        // ProviderUtils.getScheduledAccounts will always return an empty set for EcsProvider.
        Set<String> scheduledAccounts = getScheduledAccounts(ecsProvider);
        Set<NetflixAmazonCredentials> allAccounts =
                ProviderUtils.buildThreadSafeSetOfAccounts(
                        lazyLoadCredentialsRepository, NetflixAmazonCredentials.class, EcsCloudProvider.ID);
        List<Agent> newAgents = new LinkedList<>();

        for (NetflixAmazonCredentials credentials : allAccounts) {
            if (!scheduledAccounts.contains(credentials.getName())) {
                newAgents.add(
                        new IamRoleCachingAgent(
                                credentials,
                                amazonClientProvider,
                                awsCredentialsProvider,
                                iamPolicyReader)); // IAM is region-agnostic, so one caching agent per account is
                // enough
            }
            for (AmazonCredentials.AWSRegion region : credentials.getRegions()) {
                if (!scheduledAccounts.contains(credentials.getName())) {
                    newAgents.add(
                            new EcsClusterCachingAgent(
                                    credentials, region.getName(), amazonClientProvider, awsCredentialsProvider));
                    newAgents.add(
                            new ServiceCachingAgent(
                                    credentials,
                                    region.getName(),
                                    amazonClientProvider,
                                    awsCredentialsProvider,
                                    registry));
                    newAgents.add(
                            new TaskCachingAgent(
                                    credentials,
                                    region.getName(),
                                    amazonClientProvider,
                                    awsCredentialsProvider,
                                    registry));
                    newAgents.add(
                            new ContainerInstanceCachingAgent(
                                    credentials,
                                    region.getName(),
                                    amazonClientProvider,
                                    awsCredentialsProvider,
                                    registry));
                    newAgents.add(
                            new TaskDefinitionCachingAgent(
                                    credentials,
                                    region.getName(),
                                    amazonClientProvider,
                                    awsCredentialsProvider,
                                    registry,
                                    objectMapper));
                    newAgents.add(
                            new TaskHealthCachingAgent(
                                    credentials,
                                    region.getName(),
                                    amazonClientProvider,
                                    awsCredentialsProvider,
                                    objectMapper));
                    newAgents.add(
                            new EcsCloudMetricAlarmCachingAgent(
                                    credentials, region.getName(), amazonClientProvider, awsCredentialsProvider));
                    newAgents.add(
                            new ScalableTargetsCachingAgent(
                                    credentials,
                                    region.getName(),
                                    amazonClientProvider,
                                    awsCredentialsProvider,
                                    objectMapper));
                    newAgents.add(
                            new SecretCachingAgent(
                                    credentials,
                                    region.getName(),
                                    amazonClientProvider,
                                    awsCredentialsProvider,
                                    objectMapper));
                    newAgents.add(
                            new ServiceDiscoveryCachingAgent(
                                    credentials,
                                    region.getName(),
                                    amazonClientProvider,
                                    awsCredentialsProvider,
                                    objectMapper));
                    newAgents.add(
                            new TargetHealthCachingAgent(
                                    credentials,
                                    region.getName(),
                                    amazonClientProvider,
                                    awsCredentialsProvider,
                                    objectMapper));
                }
            }
        }
        ProviderUtils.rescheduleAgents(ecsProvider, newAgents);
        ecsProvider.getAgents().addAll(newAgents);
        ecsProvider.synchronizeHealthAgents();
    }

    private static Set<String> getScheduledAccounts(EcsProvider ecsProvider) {
        Set<String> scheduledAccounts = new HashSet<>();
        for (Agent agent : ecsProvider.getAgents()) {
//            accountName field is private. Use reflection or derive from AgentType.
            try {
                Field accountNameField = agent.getClass().getDeclaredField("accountName");
                accountNameField.setAccessible(true);
                String accountName = (String)accountNameField.get(agent);
                scheduledAccounts.add(accountName);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                String[] agentStringList = agent.getAgentType().split("/");
                String accountName = Arrays.stream(agentStringList).limit(agentStringList.length - 2 ).collect(Collectors.joining());
                if (!accountName.isEmpty()) {
                    scheduledAccounts.add(accountName);
                }
            }
        }
        return scheduledAccounts;
    }
}
