// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.network;

import aws.proserve.bcs.dr.lambda.CommonModule;
import aws.proserve.bcs.dr.lambda.annotation.Source;
import aws.proserve.bcs.dr.lambda.annotation.Target;
import aws.proserve.bcs.dr.secret.Credential;
import aws.proserve.bcs.dr.secret.SecretManager;
import dagger.BindsInstance;
import dagger.Component;

import javax.annotation.Nullable;
import javax.inject.Singleton;

@Singleton
@Component(modules = CommonModule.class)
public interface VpcComponent {

    static VpcComponent build(String sourceRegion, String targetRegion, Credential credential) {
        return DaggerVpcComponent.builder()
                .sourceRegion(sourceRegion)
                .targetRegion(targetRegion)
                .credential(credential)
                .build();
    }

    static Credential getCredential(String secretId) {
        return DaggerVpcComponent.builder()
                .build()
                .secretManager()
                .getCredential(secretId);
    }

    static Credential getCredentialByProject(String projectId) {
        return DaggerVpcComponent.builder()
                .build()
                .secretManager()
                .getCredentialByProject(projectId);
    }

    SecretManager secretManager();

    DeployCommonVpc.Worker deployCommonVpc();

    FindCommonSubnet.Worker findCommonSubnet();

    AddPeerRoute.Worker addPeerRoute();

    DeletePeerRoute.Worker deletePeerRoute();

    PeerVpc.Worker peerVpc();

    UnpeerVpc.Worker unpeerVpc();

    @Component.Builder
    interface Builder {

        @BindsInstance
        Builder sourceRegion(@Nullable @Source String region);

        @BindsInstance
        Builder targetRegion(@Nullable @Target String region);

        @BindsInstance
        Builder credential(@Nullable Credential credential);

        VpcComponent build();
    }
}
