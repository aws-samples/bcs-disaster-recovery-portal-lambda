// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.project;

import aws.proserve.bcs.dr.lambda.ApiHandler;
import aws.proserve.bcs.dr.lambda.dto.ImmutableResponse;
import aws.proserve.bcs.dr.project.Component;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

import java.util.Map;

public class ApiUpdateItemState extends ApiHandler {

    @Override
    public Map<String, Object> handleRequest(
            APIGatewayProxyRequestEvent event, Context context) {
        final Component component;
        final String state;
        try {
            final var map = mapper.readValue(event.getBody(), Map.class);
            component = Component.of((String) map.get("component"));
            state = (String) map.get("state");
        } catch (Exception e) {
            log.warn("Unable to parse request", e);
            return output(ImmutableResponse.builder()
                    .isSuccessful(false)
                    .cause("Unable to parse request, need to provide state and component ")
                    .build());
        }

        final var map = event.getPathParameters();
        try {
            return output(UpdateStateWorker
                    .getWorker(component, ProjectComponent.build().projectFinder())
                    .update(map.get("id"), map.get("itemId"), state));
        } catch (IllegalArgumentException e) {
            return output(ImmutableResponse.builder()
                    .isSuccessful(false)
                    .cause(e.getLocalizedMessage())
                    .build());
        }
    }
}
