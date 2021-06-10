// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.network;

import aws.proserve.bcs.dr.lambda.annotation.Source;
import aws.proserve.bcs.dr.lambda.annotation.Target;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateRouteRequest;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.lambda.runtime.Context;

import javax.inject.Inject;
import javax.inject.Singleton;

public class AddPeerRoute extends PeerRouteBase {

    @Override
    public void handleRequest(PeerRouteBase.Request request, Context context) {
        final var credential = VpcComponent.getCredentialByProject(request.getProjectId());
        VpcComponent.build(request.getSourceRegion(), request.getTargetRegion(), credential)
                .addPeerRoute()
                .handle(request);
    }

    @Singleton
    static class Worker extends PeerRouteBase.Worker {

        @Inject
        Worker(@Source AmazonEC2 sourceEc2,
               @Target AmazonEC2 targetEc2) {
            super(sourceEc2, targetEc2, Worker::addRoute);
        }

        private static void addRoute(AmazonEC2 ec2, RouteTable table, String cidr, String peerId) {
            final var tableId = table.getRouteTableId();

            if (table.getRoutes().stream()
                    .anyMatch(r -> peerId.equals(r.getVpcPeeringConnectionId()))) {
                log.info("Route to peer VPC on route table {} exists, skip.", tableId);
                return;
            }

            log.info("Add a new route to peer VPC in route table {}, cidr {}, peerId {}", tableId, cidr, peerId);
            ec2.createRoute(new CreateRouteRequest()
                    .withRouteTableId(tableId)
                    .withDestinationCidrBlock(cidr)
                    .withVpcPeeringConnectionId(peerId));
        }
    }
}
