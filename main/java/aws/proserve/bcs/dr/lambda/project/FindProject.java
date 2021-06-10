// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.project;

import aws.proserve.bcs.dr.lambda.MapHandler;
import aws.proserve.bcs.dr.project.ProjectFinder;
import com.amazonaws.services.lambda.runtime.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

public class FindProject implements MapHandler<String> {

    @Override
    public Map<String, Object> handleRequest(String projectId, Context context) {
        return ProjectComponent.build().findProject().find(projectId);
    }

    @Singleton
    static class Worker {
        private final Logger log = LoggerFactory.getLogger(getClass());
        private final ProjectFinder finder;

        @Inject
        Worker(ProjectFinder finder) {
            this.finder = finder;
        }

        Map<String, Object> find(String projectId) {
            log.info("Find project [{}]", projectId);
            final var project = finder.findOne(projectId);
            final int itemSize;

            if (project.getCloudEndureProject() != null) {
                itemSize = project.getCloudEndureProject().getItems().size();
            } else if (project.getDynamoProject() != null) {
                itemSize = project.getDynamoProject().getItems().size();
            } else if (project.getS3Project() != null) {
                itemSize = project.getS3Project().getItems().size();
            } else if (project.getVpcProject() != null) {
                itemSize = project.getVpcProject().getItems().size();
            } else {
                itemSize = 0;
            }

            return Map.of(
                    "project", project,
                    "itemSize", itemSize);
        }
    }
}
