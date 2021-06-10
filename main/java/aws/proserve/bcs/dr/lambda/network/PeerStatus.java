// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.network;

import com.amazonaws.services.ec2.model.VpcPeeringConnectionStateReason;

enum PeerStatus {
    active;

    static boolean isActive(VpcPeeringConnectionStateReason status) {
        return active.name().equals(status.getCode());
    }
}
