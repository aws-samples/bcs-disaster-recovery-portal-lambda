// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.project;

import aws.proserve.bcs.dr.lambda.VoidHandler;
import aws.proserve.bcs.dr.project.ProjectFinder;
import com.amazonaws.services.lambda.runtime.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

public class DeleteProject implements VoidHandler<String> {

    @Override
    public void handleRequest(String projectId, Context context) {
        ProjectComponent.build().deleteProject().delete(projectId);
    }

    @Singleton
    static class Worker {
        private final Logger log = LoggerFactory.getLogger(getClass());
        private final ProjectFinder finder;

        @Inject
        Worker(ProjectFinder finder) {
            this.finder = finder;
        }

        void delete(String projectId) {
            log.info("Delete project [{}]", projectId);
            final var project = finder.findOne(projectId);
            if (project != null) {
                finder.delete(project);
            }
        }
    }
}
