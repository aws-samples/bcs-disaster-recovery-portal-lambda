// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.project;

import aws.proserve.bcs.dr.lambda.CommonModule;
import aws.proserve.bcs.dr.project.ProjectFinder;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = CommonModule.class)
public interface ProjectComponent {

    static ProjectComponent build() {
        return DaggerProjectComponent.builder().build();
    }

    DeleteProject.Worker deleteProject();

    FindProject.Worker findProject();

    ApiFindProjectById.Worker findProjectById();

    ApiFindProjects.Worker findProjects();

    ProjectFinder projectFinder();
}
