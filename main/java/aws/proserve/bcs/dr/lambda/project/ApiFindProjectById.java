// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.project;

import aws.proserve.bcs.dr.exception.ProjectNotFoundException;
import aws.proserve.bcs.dr.lambda.ApiHandler;
import aws.proserve.bcs.dr.lambda.dto.ImmutableResponse;
import aws.proserve.bcs.dr.project.Project;
import aws.proserve.bcs.dr.project.ProjectFinder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

public class ApiFindProjectById extends ApiHandler {

    @Override
    public Map<String, Object> handleRequest(
            APIGatewayProxyRequestEvent event, Context context) {
        final var id = event.getPathParameters().get("id");
        try {
            return output(ImmutableResponse.builder()
                    .isSuccessful(true)
                    .result(ProjectComponent.build().findProjectById().find(id))
                    .build());
        } catch (ProjectNotFoundException e) {
            return output(ImmutableResponse.builder()
                    .isSuccessful(false)
                    .cause("Unable to find project [" + id + "]")
                    .build());
        }
    }

    @Singleton
    static class Worker {
        private final Logger log = LoggerFactory.getLogger(getClass());
        private final ProjectFinder finder;

        @Inject
        Worker(ProjectFinder finder) {
            this.finder = finder;
        }

        Project find(String id) {
            log.info("Find project [{}]", id);
            return finder.findOne(id);
        }
    }
}
