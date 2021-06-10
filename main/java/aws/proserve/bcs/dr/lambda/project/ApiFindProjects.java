// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.project;

import aws.proserve.bcs.dr.lambda.ApiHandler;
import aws.proserve.bcs.dr.lambda.dto.ImmutableResponse;
import aws.proserve.bcs.dr.project.Project;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

/**
 * @deprecated Do not provide this api as it exposes too much information.
 */
@Deprecated
public class ApiFindProjects extends ApiHandler {

    @Override
    public Map<String, Object> handleRequest(
            APIGatewayProxyRequestEvent event, Context context) {
        return output(ImmutableResponse.builder()
                .isSuccessful(true)
                .result(ProjectComponent.build().findProjects().findAll())
                .build());
    }

    @Singleton
    static class Worker {
        private final DynamoDBMapper dbMapper;

        @Inject
        Worker(DynamoDBMapper dbMapper) {
            this.dbMapper = dbMapper;
        }

        List<Project> findAll() {
            return dbMapper.scan(Project.class, new DynamoDBScanExpression());
        }
    }
}
