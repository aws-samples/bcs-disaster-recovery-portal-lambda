// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.network;

import aws.proserve.bcs.dr.exception.PortalException;
import aws.proserve.bcs.dr.lambda.StringHandler;
import aws.proserve.bcs.dr.lambda.annotation.Target;
import aws.proserve.bcs.dr.vpc.Filters;
import aws.proserve.bcs.dr.vpc.VpcConstants;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.lambda.runtime.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

public class FindCommonSubnet implements StringHandler<FindCommonSubnet.Request> {

    @Override
    public String handleRequest(Request request, Context context) {
        return VpcComponent.build(null, request.getTargetRegion(), null)
                .findCommonSubnet().find(request);
    }

    /**
     * @apiNote Do not use lambda to invoke a sibling function. Inject its worker and invoke here directly.
     */
    @Singleton
    static class Worker {
        private final Logger log = LoggerFactory.getLogger(getClass());

        private final AmazonEC2 targetEc2;
        private final DeployCommonVpc.Worker deployCommonVpc;

        @Inject
        Worker(@Target AmazonEC2 targetEc2,
               DeployCommonVpc.Worker deployCommonVpc) {
            this.targetEc2 = targetEc2;
            this.deployCommonVpc = deployCommonVpc;
        }

        String find(Request request) {
            final var vpcs = targetEc2.describeVpcs(new DescribeVpcsRequest()
                    .withFilters(Filters.name(VpcConstants.COMMON_VPC))).getVpcs();
            if (vpcs.isEmpty()) {
                log.info("Unable to find common VPC in [{}]", request.getTargetRegion());

                final var deployRequest = new DeployCommonVpc.Request();
                deployRequest.setRegion(request.getTargetRegion());
                deployRequest.setSecretId(request.getSourceCredentialId());
                deployCommonVpc.deploy(deployRequest);
            }

            final var subnetName = request.isPublicNetwork() ? VpcConstants.COMMON_PUBLIC_SUBNET1 : VpcConstants.COMMON_PRIVATE_SUBNET1;
            final var subnets = targetEc2.describeSubnets(new DescribeSubnetsRequest()
                    .withFilters(Filters.name(subnetName))).getSubnets();
            final var subnet = subnets.isEmpty() ? null : subnets.get(0);
            if (subnet == null) {
                throw new PortalException("Corrupted common VPC, unable to find common subnet.");
            }
            return subnet.getSubnetId();
        }
    }

    /**
     * @apiNote This comes from {@code CreateCloudEndureProjectRequest} during project creation, thus we have to use
     * {@code sourceCredentialId}.
     */
    static class Request {
        private boolean publicNetwork;
        private String targetRegion;
        private String sourceCredentialId;

        public boolean isPublicNetwork() {
            return publicNetwork;
        }

        public void setPublicNetwork(boolean publicNetwork) {
            this.publicNetwork = publicNetwork;
        }

        public String getTargetRegion() {
            return targetRegion;
        }

        public void setTargetRegion(String targetRegion) {
            this.targetRegion = targetRegion;
        }

        public String getSourceCredentialId() {
            return sourceCredentialId;
        }

        public void setSourceCredentialId(String sourceCredentialId) {
            this.sourceCredentialId = sourceCredentialId;
        }
    }
}
