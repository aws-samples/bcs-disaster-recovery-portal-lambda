// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.network;

import aws.proserve.bcs.dr.lambda.VoidHandler;
import aws.proserve.bcs.dr.lambda.annotation.Target;
import aws.proserve.bcs.dr.lambda.util.StackUpdater;
import aws.proserve.bcs.dr.s3.S3Constants;
import aws.proserve.bcs.dr.vpc.VpcConstants;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class DeployCommonVpc implements VoidHandler<DeployCommonVpc.Request> {

    @Override
    public void handleRequest(Request request, Context context) {
        final var credential = VpcComponent.getCredential(request.getSecretId());
        VpcComponent.build(null, request.getRegion(), credential).deployCommonVpc().deploy(request);
    }

    @Singleton
    static class Worker {
        private final Logger log = LoggerFactory.getLogger(getClass());
        private final AmazonS3 s3;
        private final AmazonCloudFormation cfn;
        private final AWSSimpleSystemsManagement ssm;


        /**
         * @param cfn must use with {@code Target} here because {@link FindCommonSubnet} refers to this class with
         *            target region.
         */
        @Inject
        Worker(AmazonS3 s3,
               AWSSimpleSystemsManagement ssm,
               @Target AmazonCloudFormation cfn) {
            this.s3 = s3;
            this.ssm = ssm;
            this.cfn = cfn;
        }

        void deploy(Request request) {
            final var updater = new StackUpdater(cfn, VpcConstants.COMMON_VPC_STACK_NAME);
            if (updater.isValid()) {
                log.info("Stack [{}] already exists at [{}]", VpcConstants.COMMON_VPC_STACK_NAME, request.getRegion());
                return;
            }

            final var stream = s3.getObject(getBucket(ssm), S3Constants.COMMON_VPC_JSON).getObjectContent();
            final var body = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining(System.lineSeparator()));

            updater.update(body);
        }

        private String getBucket(AWSSimpleSystemsManagement ssm) {
            final var parameters = ssm.getParameters(new GetParametersRequest()
                    .withNames(S3Constants.PARAM_BUCKET)).getParameters();
            if (parameters.isEmpty()) {
                throw new IllegalStateException("Unable to find bucket at " + S3Constants.PARAM_BUCKET);
            }
            return parameters.get(0).getValue();
        }
    }

    /**
     * This is called by {@link PeerVpc} and {@link FindCommonSubnet}, thus we have to use {@code secretId}.
     */
    static class Request {
        private String region;
        private String secretId;

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getSecretId() {
            return secretId;
        }

        public void setSecretId(String secretId) {
            this.secretId = secretId;
        }
    }
}
