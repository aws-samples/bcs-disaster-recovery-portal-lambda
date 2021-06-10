// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.network;

import aws.proserve.bcs.dr.exception.PortalException;
import aws.proserve.bcs.dr.lambda.VoidHandler;
import aws.proserve.bcs.dr.lambda.annotation.Source;
import aws.proserve.bcs.dr.lambda.annotation.Target;
import aws.proserve.bcs.dr.lambda.util.Assure;
import aws.proserve.bcs.dr.util.Preconditions;
import aws.proserve.bcs.dr.vpc.Cidr;
import aws.proserve.bcs.dr.vpc.Filters;
import aws.proserve.bcs.dr.vpc.VpcConstants;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.AcceptVpcPeeringConnectionRequest;
import com.amazonaws.services.ec2.model.CreateVpcPeeringConnectionRequest;
import com.amazonaws.services.ec2.model.DescribeVpcPeeringConnectionsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcPeeringConnectionsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.ModifyVpcPeeringConnectionOptionsRequest;
import com.amazonaws.services.ec2.model.PeeringConnectionOptionsRequest;
import com.amazonaws.services.ec2.model.VpcPeeringConnection;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Tasks:
 * <ul>
 * <li>Request to peer source VPC to target common VPC</li>
 * <li>Accept the request on the common VPC side</li>
 * </ul>
 */
public class PeerVpc implements VoidHandler<PeerVpc.Request> {

    @Override
    public void handleRequest(Request request, Context context) {
        final var credential = VpcComponent.getCredentialByProject(request.getSourceCredentialId());
        VpcComponent.build(request.getSourceRegion(), request.getTargetRegion(), credential)
                .peerVpc()
                .handle(request);
    }

    @Singleton
    static class Worker {
        private final Logger log = LoggerFactory.getLogger(getClass());

        private final ObjectMapper mapper;
        private final AWSLambda lambda;
        private final AmazonEC2 sourceEc2;
        private final AmazonEC2 targetEc2;

        @Inject
        Worker(ObjectMapper mapper,
               AWSLambda lambda,
               @Source AmazonEC2 sourceEc2,
               @Target AmazonEC2 targetEc2) {
            this.mapper = mapper;
            this.lambda = lambda;
            this.sourceEc2 = sourceEc2;
            this.targetEc2 = targetEc2;
        }

        void handle(Request request) {
            final var vpcs = targetEc2.describeVpcs(new DescribeVpcsRequest()
                    .withFilters(Filters.name(VpcConstants.COMMON_VPC))).getVpcs();
            if (vpcs.isEmpty()) {
                log.info("Unable to find common VPC in [{}]", request.getTargetRegion());

                try {
                    lambda.invoke(new InvokeRequest()
                            .withFunctionName("DRPCommonDeployCommonVpc")
                            .withPayload(mapper.writeValueAsString(Map.of(
                                    "region", request.getTargetRegion(),
                                    "secretId", request.getSourceCredentialId()))));
                } catch (IOException e) {
                    throw new UncheckedIOException("Unable to deploy common VPC", e);
                }
            }

            final var commonVpc = targetEc2.describeVpcs(new DescribeVpcsRequest()
                    .withFilters(Filters.name(VpcConstants.COMMON_VPC))).getVpcs().get(0);
            final var commonCidr = new Cidr(commonVpc.getCidrBlock());

            final var sourceCidr = new Cidr(sourceEc2.describeVpcs(new DescribeVpcsRequest()
                    .withVpcIds(request.getSourceVpcId())).getVpcs().get(0).getCidrBlock());
            if (sourceCidr.canMask(commonCidr) || commonCidr.canMask(sourceCidr)) {
                throw new PortalException(String.format("VPC Peering does not allow overlapping CIDR: %s %s",
                        sourceCidr.getBlock(), commonCidr.getBlock()));
            }

            final var peers = sourceEc2.describeVpcPeeringConnections(new DescribeVpcPeeringConnectionsRequest()
                    .withFilters(
                            Filters.statusCode(PeerStatus.active.name()),
                            Filters.accepterVpcId(commonVpc.getVpcId()))).getVpcPeeringConnections();

            if (!peers.isEmpty()) {
                log.info("VPC peering is already established.");
                return;
            }

            log.info("Create VPC peering from {} to {}", request.getSourceVpcId(), commonVpc.getVpcId());
            final var peer = sourceEc2.createVpcPeeringConnection(new CreateVpcPeeringConnectionRequest()
                    .withVpcId(request.getSourceVpcId())
                    .withPeerVpcId(commonVpc.getVpcId())
                    .withPeerRegion(request.getTargetRegion())).getVpcPeeringConnection();

            Assure.assure(() -> {
                boolean found;
                final var describeRequest = new DescribeVpcPeeringConnectionsRequest();
                DescribeVpcPeeringConnectionsResult result;
                do {
                    result = targetEc2.describeVpcPeeringConnections(describeRequest);
                    describeRequest.setNextToken(result.getNextToken());

                    found = result.getVpcPeeringConnections().stream()
                            .map(VpcPeeringConnection::getVpcPeeringConnectionId)
                            .anyMatch(peer.getVpcPeeringConnectionId()::equals);
                } while (!found && result.getNextToken() != null);
                Preconditions.checkArgument(found, "VPC Peering is not established");
            });

            log.info("Accept VPC peering of {}", peer.getVpcPeeringConnectionId());
            targetEc2.acceptVpcPeeringConnection(new AcceptVpcPeeringConnectionRequest()
                    .withVpcPeeringConnectionId(peer.getVpcPeeringConnectionId()));

            Assure.assure(() -> {
                final var status = sourceEc2.describeVpcPeeringConnections(new DescribeVpcPeeringConnectionsRequest()
                        .withVpcPeeringConnectionIds(peer.getVpcPeeringConnectionId()))
                        .getVpcPeeringConnections().get(0).getStatus();
                Preconditions.checkArgument(PeerStatus.isActive(status),
                        "Source VPC peering status is not active yet: " + status);
            });

            Assure.assure(() -> {
                final var status = targetEc2.describeVpcPeeringConnections(new DescribeVpcPeeringConnectionsRequest()
                        .withVpcPeeringConnectionIds(peer.getVpcPeeringConnectionId()))
                        .getVpcPeeringConnections().get(0).getStatus();
                Preconditions.checkArgument(PeerStatus.isActive(status),
                        "Target VPC peering status is not active yet: " + status);
            });

            sourceEc2.modifyVpcPeeringConnectionOptions(new ModifyVpcPeeringConnectionOptionsRequest()
                    .withVpcPeeringConnectionId(peer.getVpcPeeringConnectionId())
                    .withRequesterPeeringConnectionOptions(new PeeringConnectionOptionsRequest()
                            .withAllowDnsResolutionFromRemoteVpc(true)));

            targetEc2.modifyVpcPeeringConnectionOptions(new ModifyVpcPeeringConnectionOptionsRequest()
                    .withVpcPeeringConnectionId(peer.getVpcPeeringConnectionId())
                    .withAccepterPeeringConnectionOptions(new PeeringConnectionOptionsRequest()
                            .withAllowDnsResolutionFromRemoteVpc(true)));
        }
    }

    /**
     * This comes from {@code CreateCloudEndureProjectRequest} during project creation, thus we have to use {@code sourceCredentialId}.
     */
    static class Request {
        private String sourceRegion;
        private String targetRegion;
        private String sourceVpcId;
        private String sourceCredentialId;

        public String getSourceRegion() {
            return sourceRegion;
        }

        public void setSourceRegion(String sourceRegion) {
            this.sourceRegion = sourceRegion;
        }

        public String getTargetRegion() {
            return targetRegion;
        }

        public void setTargetRegion(String targetRegion) {
            this.targetRegion = targetRegion;
        }

        public String getSourceVpcId() {
            return sourceVpcId;
        }

        public void setSourceVpcId(String sourceVpcId) {
            this.sourceVpcId = sourceVpcId;
        }

        public String getSourceCredentialId() {
            return sourceCredentialId;
        }

        public void setSourceCredentialId(String sourceCredentialId) {
            this.sourceCredentialId = sourceCredentialId;
        }
    }
}
