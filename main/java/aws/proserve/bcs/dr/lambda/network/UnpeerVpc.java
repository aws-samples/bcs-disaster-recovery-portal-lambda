// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.network;

import aws.proserve.bcs.dr.lambda.VoidHandler;
import aws.proserve.bcs.dr.lambda.annotation.Source;
import aws.proserve.bcs.dr.lambda.annotation.Target;
import aws.proserve.bcs.dr.vpc.Filters;
import aws.proserve.bcs.dr.vpc.VpcConstants;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DeleteVpcPeeringConnectionRequest;
import com.amazonaws.services.ec2.model.DescribeVpcPeeringConnectionsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcPeeringConnectionsResult;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.VpcPeeringConnection;
import com.amazonaws.services.lambda.runtime.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

public class UnpeerVpc implements VoidHandler<UnpeerVpc.Request> {

    @Override
    public void handleRequest(Request request, Context context) {
        final var credential = VpcComponent.getCredentialByProject(request.getProjectId());
        VpcComponent.build(request.getSourceRegion(), request.getTargetRegion(), credential)
                .unpeerVpc()
                .handle(request);
    }

    @Singleton
    static class Worker {
        private final Logger log = LoggerFactory.getLogger(getClass());

        private final AmazonEC2 sourceEc2;
        private final AmazonEC2 targetEc2;

        @Inject
        Worker(@Source AmazonEC2 sourceEc2,
               @Target AmazonEC2 targetEc2) {
            this.sourceEc2 = sourceEc2;
            this.targetEc2 = targetEc2;
        }

        void handle(Request request) {
            final var commonVpc = targetEc2.describeVpcs(new DescribeVpcsRequest()
                    .withFilters(Filters.name(VpcConstants.COMMON_VPC))).getVpcs().get(0);

            final var describePeerRequest = new DescribeVpcPeeringConnectionsRequest();
            DescribeVpcPeeringConnectionsResult result;
            Optional<VpcPeeringConnection> peer;
            do {
                result = sourceEc2.describeVpcPeeringConnections();
                describePeerRequest.setNextToken(result.getNextToken());

                peer = result.getVpcPeeringConnections().stream()
                        .filter(c -> PeerStatus.isActive(c.getStatus()))
                        .filter(c -> c.getAccepterVpcInfo().getVpcId().equals(commonVpc.getVpcId()))
                        .findFirst();
            } while (peer.isEmpty() && result.getNextToken() != null);

            if (peer.isPresent()) {
                log.info("Active VPC peering exists, delete it.");
                sourceEc2.deleteVpcPeeringConnection(new DeleteVpcPeeringConnectionRequest()
                        .withVpcPeeringConnectionId(peer.get().getVpcPeeringConnectionId()));
            }
        }
    }

    static class Request {
        private String sourceRegion;
        private String targetRegion;
        private String projectId;

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

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }
    }
}
