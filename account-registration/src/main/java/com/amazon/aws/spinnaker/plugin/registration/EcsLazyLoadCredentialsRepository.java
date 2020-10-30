//package com.amazon.aws.spinnaker.plugin.registration;
//
//import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
//import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
//import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
//import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
//import lombok.extern.slf4j.Slf4j;
//import org.jetbrains.annotations.Nullable;
//import org.springframework.context.annotation.Lazy;
//
//@Slf4j
//public class EcsLazyLoadCredentialsRepository extends MapBackedCredentialsRepository<NetflixECSCredentials> {
//
//    public EcsLazyLoadCredentialsRepository(@Lazy CredentialsLifecycleHandler<NetflixECSCredentials> eventHandler) {
//        super("ecs", eventHandler);
//    }
//
//    @Override
//    public NetflixECSCredentials getOne(String key) {
//        log.info("Getting One!!!!!!!!!!!!!");
//        return super.getOne(key);
//    }
//}
