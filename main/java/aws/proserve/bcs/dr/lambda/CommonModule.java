// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda;


import aws.proserve.bcs.dr.lambda.annotation.Source;
import aws.proserve.bcs.dr.lambda.annotation.Target;
import aws.proserve.bcs.dr.secret.Credential;
import com.amazonaws.jmespath.ObjectMapperSingleton;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;

import javax.annotation.Nullable;
import javax.inject.Singleton;

@Module
@Singleton
public class CommonModule {

    @Provides
    @Singleton
    AmazonS3 s3() {
        return AmazonS3ClientBuilder.standard().enableForceGlobalBucketAccess().build();
    }

    @Provides
    @Singleton
    AmazonDynamoDB amazonDynamoDB() {
        return AmazonDynamoDBClientBuilder.defaultClient();
    }

    @Provides
    @Singleton
    AWSLambda lambda() {
        return AWSLambdaClientBuilder.defaultClient();
    }

    @Provides
    @Singleton
    DynamoDBMapper dynamoDBMapper(AmazonDynamoDB amazonDynamoDB) {
        return new DynamoDBMapper(amazonDynamoDB, DynamoDBMapperConfig.builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
                .build());
    }

    @Provides
    @Source
    AmazonEC2 sourceEc2(@Nullable @Source String region, @Nullable Credential credential) {
        return AmazonEC2ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(Credential.toProvider(credential))
                .build();
    }

    @Provides
    @Target
    AmazonEC2 targetEc2(@Nullable @Target String region) {
        return AmazonEC2ClientBuilder.standard()
                .withRegion(region)
                .build();
    }

    @Provides
    @Target
    AmazonCloudFormation targetCfn(@Nullable @Target String region, @Nullable Credential credential) {
        return AmazonCloudFormationClientBuilder.standard()
                .withRegion(region)
                .withCredentials(Credential.toProvider(credential))
                .build();
    }

    @Provides
    @Singleton
    ObjectMapper objectMapper() {
        return ObjectMapperSingleton.getObjectMapper();
    }

    @Provides
    @Singleton
    AWSSecretsManager secretsManager() {
        return AWSSecretsManagerClientBuilder.defaultClient();
    }

    /**
     * @apiNote SSM at the default region stores the values in the parameter store.
     */
    @Provides
    @Singleton
    AWSSimpleSystemsManagement ssm() {
        return AWSSimpleSystemsManagementClientBuilder.defaultClient();
    }
}
