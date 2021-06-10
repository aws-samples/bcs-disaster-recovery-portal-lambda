// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package aws.proserve.bcs.dr.lambda.project;

import aws.proserve.bcs.dr.dynamo.DynamoItem;
import aws.proserve.bcs.dr.lambda.dto.ImmutableResponse;
import aws.proserve.bcs.dr.lambda.dto.Response;
import aws.proserve.bcs.dr.project.Component;
import aws.proserve.bcs.dr.project.Item;
import aws.proserve.bcs.dr.project.Project;
import aws.proserve.bcs.dr.project.ProjectFinder;
import aws.proserve.bcs.dr.project.States;
import aws.proserve.bcs.dr.project.SubProject;
import aws.proserve.bcs.dr.project.TimedItem;
import aws.proserve.bcs.dr.s3.S3Item;
import aws.proserve.bcs.dr.vpc.VpcItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.function.Function;

class UpdateStateWorker {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ProjectFinder finder;
    private final Function<String, Enum<?>> stateFunction;
    private final Function<Project, SubProject<? extends Item>> projectFunction;

    static UpdateStateWorker getWorker(Component component, ProjectFinder finder) {
        switch (component) {
            case DynamoDB:
                return new UpdateStateWorker(finder, DynamoItem.State::valueOf, Project::getDynamoProject);

            case S3:
                return new UpdateStateWorker(finder, S3Item.State::valueOf, Project::getS3Project);

            case VPC:
                return new UpdateStateWorker(finder, VpcItem.State::valueOf, Project::getVpcProject);

            default:
                throw new IllegalArgumentException("Unsupported component " + component);
        }
    }

    UpdateStateWorker(
            ProjectFinder finder,
            Function<String, Enum<?>> stateFunction,
            Function<Project, SubProject<? extends Item>> projectFunction) {
        this.finder = finder;
        this.stateFunction = stateFunction;
        this.projectFunction = projectFunction;
    }

    Response update(String projectId, String itemId, String state) {
        log.info("Update project [{}] item [{}] state [{}]", projectId, itemId, state);
        final var project = finder.findOne(projectId);
        if (project == null) {
            return ImmutableResponse.builder()
                    .isSuccessful(false)
                    .cause("Unable to find project " + projectId)
                    .build();
        }

        final var item = projectFunction.apply(project)
                .getItems()
                .stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst();

        if (item.isEmpty()) {
            return ImmutableResponse.builder()
                    .isSuccessful(false)
                    .cause("Unable to find item " + itemId)
                    .build();
        }

        final var i = item.get();
        try {
            final var checkedState = stateFunction.apply(state).name();
            i.setState(checkedState);

            if (i instanceof TimedItem) {
                switch (checkedState) {
                    case States.STARTED:
                    case States.REPLICATING:
                        ((TimedItem) i).setStartTime(new Date());
                        break;

                    case States.REPLICATED:
                    case States.STOPPED:
                    case States.FAILED:
                        ((TimedItem) i).setEndTime(new Date());
                        break;
                }
            }
        } catch (IllegalArgumentException e) {
            return ImmutableResponse.builder()
                    .isSuccessful(false)
                    .cause("Illegal state " + state)
                    .build();
        }

        finder.save(project);
        return ImmutableResponse.builder().isSuccessful(true).build();
    }
}
