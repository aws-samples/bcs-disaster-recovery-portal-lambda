// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.network;

import aws.proserve.bcs.dr.exception.PortalException;
import aws.proserve.bcs.dr.lambda.VoidHandler;
import aws.proserve.bcs.dr.vpc.Filters;
import aws.proserve.bcs.dr.vpc.VpcConstants;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeRouteTablesRequest;
import com.amazonaws.services.ec2.model.DescribeRouteTablesResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcPeeringConnectionsRequest;
import com.amazonaws.services.ec2.model.DescribeVpcsRequest;
import com.amazonaws.services.ec2.model.RouteTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PeerRouteBase implements VoidHandler<PeerRouteBase.Request> {


    static abstract class Worker {
        static final Logger log = LoggerFactory.getLogger(PeerRouteBase.class);

        private final AmazonEC2 sourceEc2;
        private final AmazonEC2 targetEc2;
        private final ManageRoute manageRoute;

        protected Worker(
                AmazonEC2 sourceEc2,
                AmazonEC2 targetEc2,
                ManageRoute manageRoute) {
            this.sourceEc2 = sourceEc2;
            this.targetEc2 = targetEc2;
            this.manageRoute = manageRoute;
        }

        void handle(Request request) {
            final var targetVpc = targetEc2.describeVpcs(new DescribeVpcsRequest()
                    .withFilters(Filters.name(VpcConstants.COMMON_VPC))).getVpcs().get(0);

            final var peerId = sourceEc2.describeVpcPeeringConnections(new DescribeVpcPeeringConnectionsRequest()
                    .withFilters(
                            Filters.accepterVpcId(targetVpc.getVpcId()),
                            Filters.statusCode(PeerStatus.active.name())))
                    .getVpcPeeringConnections().get(0).getVpcPeeringConnectionId();

            final var peerSubnet = targetEc2.describeSubnets(new DescribeSubnetsRequest()
                    .withFilters(Filters.name(VpcConstants.COMMON_PRIVATE_SUBNET1))).getSubnets().get(0);

            final var targetRouteTable = targetEc2.describeRouteTables(new DescribeRouteTablesRequest()
                    .withFilters(Filters.associatedSubnetId(peerSubnet.getSubnetId()))).getRouteTables().get(0);
            final var sourceCidr = sourceEc2.describeVpcs(new DescribeVpcsRequest()
                    .withVpcIds(request.getSourceVpcId())).getVpcs().get(0).getCidrBlock();
            manageRoute.manage(targetEc2, targetRouteTable, sourceCidr, peerId);

            if (request.getInstanceIds() == null) { // for deleting
                final var describeRequest = new DescribeRouteTablesRequest();
                DescribeRouteTablesResult result;
                do {
                    result = sourceEc2.describeRouteTables(describeRequest);
                    describeRequest.setNextToken(result.getNextToken());

                    result.getRouteTables().forEach(table ->
                            manageRoute.manage(sourceEc2, table, targetVpc.getCidrBlock(), peerId));
                } while (result.getNextToken() != null);
            } else { // for adding
                for (String instanceId : request.getInstanceIds()) {
                    final var sourceVpc = findSubnetId(sourceEc2, instanceId);
                    final var tables = sourceEc2.describeRouteTables(new DescribeRouteTablesRequest()
                            .withFilters(Filters.associatedSubnetId(sourceVpc.getSubnetId()))).getRouteTables();

                    if (tables.isEmpty()) {
                        log.info("Unable to find subnet association, add to main route table.");
                        final var mainTable = sourceEc2.describeRouteTables(new DescribeRouteTablesRequest()
                                .withFilters(Filters.associatedMain())).getRouteTables().get(0);

                        manageRoute.manage(sourceEc2, mainTable, targetVpc.getCidrBlock(), peerId);
                    } else {
                        manageRoute.manage(sourceEc2, tables.get(0), targetVpc.getCidrBlock(), peerId);
                    }
                }
            }
        }

        private SourceVpc findSubnetId(AmazonEC2 ec2, String instanceId) {
            final var describeRequest = new DescribeInstancesRequest();
            DescribeInstancesResult result;
            do {
                result = ec2.describeInstances(describeRequest);
                describeRequest.setNextToken(result.getNextToken());

                for (var reservation : result.getReservations()) {
                    final var instance = reservation.getInstances().stream()
                            .filter(i -> i.getInstanceId().equals(instanceId))
                            .findFirst();

                    if (instance.isPresent()) {
                        return new SourceVpc(
                                instance.get().getVpcId(),
                                instance.get().getSubnetId(),
                                instance.get().getSecurityGroups().get(0).getGroupId());
                    }
                }
            } while (result.getNextToken() != null);
            throw new PortalException("Unable to find subnet of instance: " + instanceId);
        }
    }

    interface ManageRoute {
        void manage(AmazonEC2 ec2, RouteTable table, String cidr, String peerId);
    }

    static class SourceVpc {
        private final String vpcId;
        private final String subnetId;
        private final String securityGroupId;

        SourceVpc(String vpcId, String subnetId, String securityGroupId) {
            this.vpcId = vpcId;
            this.subnetId = subnetId;
            this.securityGroupId = securityGroupId;
        }

        public String getVpcId() {
            return vpcId;
        }

        public String getSubnetId() {
            return subnetId;
        }

        public String getSecurityGroupId() {
            return securityGroupId;
        }
    }

    static class Request {
        private String sourceVpcId;
        private String sourceRegion;
        private String targetRegion;
        private String[] instanceIds;
        private String projectId;

        public String getSourceVpcId() {
            return sourceVpcId;
        }

        public void setSourceVpcId(String sourceVpcId) {
            this.sourceVpcId = sourceVpcId;
        }

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

        public String[] getInstanceIds() {
            return instanceIds;
        }

        public void setInstanceIds(String[] instanceIds) {
            this.instanceIds = instanceIds;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }
    }
}
