/*
 * Copyright (c) 2023-present KuFlow S.L.
 *
 * All rights reserved.
 */

package com.kuflow.engine.samples.worker.workflow;

import com.kuflow.rest.model.Task;
import com.kuflow.rest.model.TaskDefinitionSummary;
import com.kuflow.temporal.activity.kuflow.KuFlowSyncActivities;
import com.kuflow.temporal.activity.kuflow.model.ClaimTaskRequest;
import com.kuflow.temporal.activity.kuflow.model.CompleteProcessRequest;
import com.kuflow.temporal.activity.kuflow.model.CompleteProcessResponse;
import com.kuflow.temporal.activity.kuflow.model.CompleteTaskRequest;
import com.kuflow.temporal.activity.kuflow.model.CreateTaskRequest;
import com.kuflow.temporal.activity.uivision.UIVisionActivities;
import com.kuflow.temporal.activity.uivision.model.ExecuteUIVisionMacroRequest;
import com.kuflow.temporal.common.KuFlowGenerator;
import com.kuflow.temporal.common.model.WorkflowRequest;
import com.kuflow.temporal.common.model.WorkflowResponse;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;

//este es el que hay ejecutar.


public class UIVisionSampleWorkflowImpl implements UIVisionSampleWorkflow {
    private static final Logger LOGGER = Workflow.getLogger(UIVisionSampleWorkflow.class);

    private static final String TASK_ROBOT_RESULTS = "ROBOT_RESULTS";

    private final KuFlowSyncActivities kuFlowSyncActivities;

    private final UIVisionActivities uiVisionActivities;

    private KuFlowGenerator kuflowGenerator;

    public UIVisionSampleWorkflowImpl() {
        RetryOptions defaultRetryOptions = RetryOptions.newBuilder().validateBuildWithDefaults();

        ActivityOptions defaultActivityOptions = ActivityOptions
            .newBuilder()
            .setRetryOptions(defaultRetryOptions)
            .setStartToCloseTimeout(Duration.ofMinutes(15))
            .setScheduleToCloseTimeout(Duration.ofDays(365))
            .validateAndBuildWithDefaults();

        this.kuFlowSyncActivities = Workflow.newActivityStub(KuFlowSyncActivities.class, defaultActivityOptions);

        this.uiVisionActivities = Workflow.newActivityStub(UIVisionActivities.class, defaultActivityOptions);
    }

    @Override
    public WorkflowResponse runWorkflow(WorkflowRequest workflowRequest) {
        this.kuflowGenerator = new KuFlowGenerator(workflowRequest.getProcessId());

        this.createTaskRobotResults(workflowRequest);

        CompleteProcessResponse completeProcess = this.completeProcess(workflowRequest);

        LOGGER.info("UiVision process finished. {}", workflowRequest.getProcessId());

        return this.completeWorkflow(completeProcess);
    }

    private WorkflowResponse completeWorkflow(CompleteProcessResponse completeProcess) {
        WorkflowResponse workflowResponse = new WorkflowResponse();
        workflowResponse.setMessage("Complete process " + completeProcess.getProcess().getId());

        return workflowResponse;
    }

    private CompleteProcessResponse completeProcess(WorkflowRequest workflowRequest) {
        CompleteProcessRequest request = new CompleteProcessRequest();
        request.setProcessId(workflowRequest.getProcessId());

        return this.kuFlowSyncActivities.completeProcess(request);
    }

    private void createTaskRobotResults(WorkflowRequest workflowRequest) {
        UUID taskId = this.kuflowGenerator.randomUUID();

        // Create task in KuFlow
        TaskDefinitionSummary tasksDefinition = new TaskDefinitionSummary();
        tasksDefinition.setCode(TASK_ROBOT_RESULTS);

        Task task = new Task();
        task.setId(taskId);
        task.setProcessId(workflowRequest.getProcessId());
        task.setTaskDefinition(tasksDefinition);

        CreateTaskRequest createTaskRequest = new CreateTaskRequest();
        createTaskRequest.setTask(task);
        this.kuFlowSyncActivities.createTask(createTaskRequest);

        // Claim task by the worker because is a valid candidate.
        // We could also claim it by specifying the "owner" in the above creation call.
        // We use the same application for the worker and for the robot.
        ClaimTaskRequest claimTaskRequest = new ClaimTaskRequest();
        claimTaskRequest.setTaskId(taskId);
        this.kuFlowSyncActivities.claimTask(claimTaskRequest);

        // Executes the Temporal activity to run the robot.
        ExecuteUIVisionMacroRequest executeUIVisionMacroRequest = new ExecuteUIVisionMacroRequest();
        executeUIVisionMacroRequest.setTaskId(taskId);
        this.uiVisionActivities.executeUIVisionMacro(executeUIVisionMacroRequest);

        // Complete the task.
        CompleteTaskRequest completeTaskRequest = new CompleteTaskRequest();
        completeTaskRequest.setTaskId(taskId);
        this.kuFlowSyncActivities.completeTask(completeTaskRequest);
    }
}
