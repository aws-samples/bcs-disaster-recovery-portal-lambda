// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.project;

import aws.proserve.bcs.dr.lambda.VoidHandler;
import aws.proserve.bcs.dr.project.Component;
import com.amazonaws.services.lambda.runtime.Context;

public class UpdateItemState implements VoidHandler<UpdateItemState.Request> {

    @Override
    public void handleRequest(Request request, Context context) {
        UpdateStateWorker
                .getWorker(Component.of(request.getComponent()), ProjectComponent.build().projectFinder())
                .update(request.getId(), request.getItemId(), request.getState());
    }

    static final class Request {
        private String component;
        private String id;
        private String itemId;
        private String state;

        public String getComponent() {
            return component;
        }

        public void setComponent(String component) {
            this.component = component;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }
}
