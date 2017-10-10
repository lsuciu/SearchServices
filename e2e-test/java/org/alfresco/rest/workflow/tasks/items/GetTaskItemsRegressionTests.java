package org.alfresco.rest.workflow.tasks.items;

import org.alfresco.dataprep.CMISUtil.DocumentType;
import org.alfresco.dataprep.CMISUtil.Priority;
import org.alfresco.rest.RestTest;
import org.alfresco.rest.model.RestErrorModel;
import org.alfresco.rest.model.RestItemModel;
import org.alfresco.rest.model.RestItemModelsCollection;
import org.alfresco.rest.model.RestProcessModel;
import org.alfresco.rest.model.RestTaskModel;
import org.alfresco.utility.model.FileModel;
import org.alfresco.utility.model.SiteModel;
import org.alfresco.utility.model.TaskModel;
import org.alfresco.utility.model.TestGroup;
import org.alfresco.utility.model.UserModel;
import org.alfresco.utility.report.Bug;
import org.alfresco.utility.testrail.ExecutionType;
import org.alfresco.utility.testrail.annotation.TestRail;
import org.springframework.http.HttpStatus;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Created by Claudia Agache on 12/8/2016.
 */
public class GetTaskItemsRegressionTests extends RestTest
{
    private UserModel adminUser, userWhoStartsTask, assignee;
    private SiteModel siteModel;
    private FileModel fileModel;
    private TaskModel taskModel;
    private RestItemModelsCollection itemModels;
    private RestProcessModel addedProcess;
    private RestTaskModel addedTask;
    private RestItemModel taskItem;

    @BeforeClass(alwaysRun=true)
    public void dataPreparation() throws Exception
    {
        userWhoStartsTask = dataUser.createRandomTestUser();
        assignee = dataUser.createRandomTestUser();
        siteModel = dataSite.usingUser(userWhoStartsTask).createPublicRandomSite();
        fileModel = dataContent.usingSite(siteModel).createContent(DocumentType.TEXT_PLAIN);
        adminUser = dataUser.getAdminUser();
    }

    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Verify if get task items call return status code 404 when invalid taskId is provided")
    @Test(groups = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    public void getTaskItemsUsingInvalidTaskId() throws Exception
    {
        taskModel = dataWorkflow.usingUser(userWhoStartsTask).usingSite(siteModel).usingResource(fileModel).createNewTaskAndAssignTo(assignee);
        taskModel.setId("invalidId");

        restClient.authenticateUser(assignee).withWorkflowAPI()
                .usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.NOT_FOUND)
                .assertLastError().containsSummary(String.format(RestErrorModel.ENTITY_NOT_FOUND, "invalidId"));

        taskModel.setId("");

        restClient.authenticateUser(userWhoStartsTask).withWorkflowAPI()
                .usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.NOT_FOUND)
                .assertLastError().containsSummary(String.format(RestErrorModel.ENTITY_NOT_FOUND, ""));
    }

    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS },
            executionType = ExecutionType.REGRESSION,
            description = "Verify if get task items request returns status code 200 after the task is finished.")
    @Test(groups = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    public void getTaskItemsAfterFinishingTask() throws Exception
    {
        taskModel = dataWorkflow.usingUser(userWhoStartsTask).usingSite(siteModel).usingResource(fileModel).createNewTaskAndAssignTo(assignee);
        dataWorkflow.usingUser(assignee).taskDone(taskModel);
        restClient.authenticateUser(userWhoStartsTask);
        itemModels = restClient.withWorkflowAPI().usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        itemModels.assertThat().entriesListIsNotEmpty().and().entriesListContains("name", fileModel.getName());
    }

    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS },
            executionType = ExecutionType.REGRESSION,
            description = "Verify if get task items request returns status code 200 after the process is deleted (Task state is now completed.)")
    @Test(groups = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    public void getTaskItemsAfterDeletingProcess() throws Exception
    {
        addedProcess = restClient.authenticateUser(userWhoStartsTask).withWorkflowAPI().addProcess("activitiAdhoc", assignee, false, Priority.Normal);
        addedTask = restClient.withWorkflowAPI().getTasks().getTaskModelByProcess(addedProcess);
        restClient.withWorkflowAPI().usingTask(addedTask).addTaskItem(fileModel);
        restClient.assertStatusCodeIs(HttpStatus.CREATED);
        restClient.withWorkflowAPI().usingProcess(addedProcess).deleteProcess();
        restClient.assertStatusCodeIs(HttpStatus.NO_CONTENT);

        itemModels = restClient.withWorkflowAPI().usingTask(addedTask).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        itemModels.assertThat().entriesListIsNotEmpty().and().entriesListContains("name", fileModel.getName());
    }

    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Verify gets task items call with admin from different network")
    @Test(groups = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION, TestGroup.NETWORKS  })
    public void getTaskItemsByAdminFromAnotherNetwork() throws Exception
    {
        UserModel adminTenantUser1 = UserModel.getAdminTenantUser();
        UserModel adminTenantUser2 = UserModel.getAdminTenantUser();

        restClient.authenticateUser(adminUser).usingTenant().createTenant(adminTenantUser1);
        restClient.usingTenant().createTenant(adminTenantUser2);

        UserModel tenantUser1 = dataUser.usingUser(adminTenantUser1).createUserWithTenant("uTenant");
        UserModel tenantUserAssignee1 = dataUser.usingUser(adminTenantUser1).createUserWithTenant("uTenantAssignee");

        siteModel = dataSite.usingUser(adminTenantUser1).createPublicRandomSite();
        fileModel = dataContent.usingUser(adminTenantUser1).usingSite(siteModel).createContent(DocumentType.XML);
        addedProcess = restClient.authenticateUser(tenantUser1).withWorkflowAPI().addProcess("activitiAdhoc", tenantUserAssignee1, false, Priority.Normal);
        addedTask = restClient.withWorkflowAPI().getTasks().getTaskModelByProcess(addedProcess);
        restClient.withWorkflowAPI().usingTask(addedTask).addTaskItem(fileModel);

        restClient.authenticateUser(adminTenantUser2).withWorkflowAPI().usingTask(addedTask).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.FORBIDDEN)
                .assertLastError().containsSummary(RestErrorModel.PERMISSION_WAS_DENIED);
    }

    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Verify gets task items call returns only task items inside network")
    @Test(groups = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION, TestGroup.NETWORKS  })
    public void getTaskItemsReturnsOnlyItemsFromThatNetwork() throws Exception
    {
        UserModel adminTenantUser1 = UserModel.getAdminTenantUser();
        UserModel adminTenantUser2 = UserModel.getAdminTenantUser();

        restClient.authenticateUser(adminUser).usingTenant().createTenant(adminTenantUser1);
        restClient.usingTenant().createTenant(adminTenantUser2);

        UserModel tenantUser1 = dataUser.usingUser(adminTenantUser1).createUserWithTenant("uTenant1");
        UserModel tenantUser2 = dataUser.usingUser(adminTenantUser2).createUserWithTenant("uTenant2");

        SiteModel siteModel1 = dataSite.usingUser(adminTenantUser1).createPublicRandomSite();
        FileModel fileModel1 = dataContent.usingUser(adminTenantUser1).usingSite(siteModel1).createContent(DocumentType.XML);

        RestProcessModel addedProcess1 = restClient.authenticateUser(adminTenantUser1).withWorkflowAPI().addProcess("activitiAdhoc", tenantUser1, false, Priority.Normal);
        RestTaskModel addedTask1 = restClient.withWorkflowAPI().getTasks().getTaskModelByProcess(addedProcess1);
        RestItemModel taskItem1 = restClient.withWorkflowAPI().usingTask(addedTask1).addTaskItem(fileModel1);

        SiteModel siteModel2 = dataSite.usingUser(adminTenantUser2).createPublicRandomSite();
        FileModel fileModel2 = dataContent.usingUser(adminTenantUser2).usingSite(siteModel2).createContent(DocumentType.XML);

        RestProcessModel addedProcess2 = restClient.authenticateUser(adminTenantUser2).withWorkflowAPI().addProcess("activitiAdhoc", tenantUser2, false, Priority.Normal);
        RestTaskModel addedTask2 = restClient.withWorkflowAPI().getTasks().getTaskModelByProcess(addedProcess2);
        RestItemModel taskItem2 = restClient.withWorkflowAPI().usingTask(addedTask2).addTaskItem(fileModel2);

        itemModels = restClient.withWorkflowAPI().usingTask(addedTask2).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        itemModels.assertThat()
                .entriesListIsNotEmpty().and()
                .entriesListContains("id", taskItem2.getId()).and()
                .entriesListContains("name", fileModel2.getName()).and()
                .entriesListDoesNotContain("id", taskItem1.getId()).and()
                .entriesListDoesNotContain("name", fileModel1.getName());
    }

    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Check default error model schema for get task items api call")
    @Test(groups = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    public void getTaskItemsUsingCheckErrorModel() throws Exception
    {
        taskModel = dataWorkflow.usingUser(userWhoStartsTask).usingSite(siteModel).usingResource(fileModel).createNewTaskAndAssignTo(assignee);
        taskModel.setId("invalidId");
        restClient.authenticateUser(assignee).withWorkflowAPI()
                .usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.NOT_FOUND)
                .assertLastError().containsSummary(String.format(RestErrorModel.ENTITY_NOT_FOUND, "invalidId"))
                .containsErrorKey(RestErrorModel.ENTITY_NOT_FOUND_ERRORKEY)
                .stackTraceIs(RestErrorModel.STACKTRACE)
                .descriptionURLIs(RestErrorModel.RESTAPIEXPLORER);
    }

    @Test(groups = {TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Verify that admin user can get task items")
    public void getTaskItemsByAdminUser() throws Exception
    {
        restClient.authenticateUser(userWhoStartsTask);
        taskModel = dataWorkflow.usingUser(userWhoStartsTask).usingSite(siteModel).usingResource(fileModel).createNewTaskAndAssignTo(assignee);
        taskItem = restClient.withWorkflowAPI().usingTask(taskModel).getTaskItems().getOneRandomEntry();

        itemModels = restClient.authenticateUser(dataUser.getAdminUser()).withWorkflowAPI().usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        itemModels.assertThat()
                .entriesListIsNotEmpty().and()
                .entriesListContains("id", taskItem.onModel().getId()).and()
                .entriesListContains("name", taskItem.onModel().getName())
                .getEntries().get(0).onModel()
                .assertThat().field("createdAt").isNotNull()
                .assertThat().field("size").isNotNull()
                .assertThat().field("createdBy").contains(dataUser.getAdminUser().getUsername())
                .assertThat().field("modifiedAt").isNotNull()
                .assertThat().field("name").contains(fileModel.getName())
                .assertThat().field("modifiedBy").contains(userWhoStartsTask.getUsername())
                .assertThat().field("id").contains(fileModel.getNodeRef().split(";")[0])
                .assertThat().field("mimeType").contains(fileModel.getFileType().mimeType);
    }

    @Bug(id="MNT-17438")
    @Test(groups = {TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Verify that user who started the process can get task items with skip count parameter")
    public void getTaskItemsWithSkipCount() throws Exception
    {
        restClient.authenticateUser(userWhoStartsTask);
        taskModel = dataWorkflow.usingUser(userWhoStartsTask).usingSite(siteModel).usingResource(fileModel).createNewTaskAndAssignTo(assignee);
        FileModel document1 = dataContent.usingSite(siteModel).createContent(DocumentType.XML);
        restClient.withWorkflowAPI().usingTask(taskModel).addTaskItem(document1);
        itemModels = restClient.authenticateUser(userWhoStartsTask).withWorkflowAPI()
                .usingParams("skipCount=1").usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        itemModels.assertThat()
                .entriesListIsNotEmpty()
                .and().paginationField("count").is("1");
    }

    @Bug(id="MNT-17438")
    @Test(groups = {TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Verify that user who started the process can get task items with max items parameter")
    public void getTaskItemsWithMaxItems() throws Exception
    {
        restClient.authenticateUser(userWhoStartsTask);
        taskModel = dataWorkflow.usingUser(userWhoStartsTask).usingSite(siteModel).usingResource(fileModel).createNewTaskAndAssignTo(assignee);
        FileModel document1 = dataContent.usingSite(siteModel).createContent(DocumentType.XML);
        restClient.withWorkflowAPI().usingTask(taskModel).addTaskItem(document1);
        itemModels = restClient.authenticateUser(userWhoStartsTask).withWorkflowAPI()
                .usingParams("maxItems=1").usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        itemModels.assertThat()
                .entriesListIsNotEmpty()
                .and().paginationField("count").is("1");
    }

    @Test(groups = {TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Verify that user who started the process can get task items with valid properties")
    public void getTaskItemsWithValidProperties() throws Exception
    {
        restClient.authenticateUser(userWhoStartsTask);
        taskModel = dataWorkflow.usingUser(userWhoStartsTask).usingSite(siteModel).usingResource(fileModel).createNewTaskAndAssignTo(assignee);
        taskItem = restClient.withWorkflowAPI().usingTask(taskModel).getTaskItems().getOneRandomEntry();
        itemModels = restClient.authenticateUser(userWhoStartsTask).withWorkflowAPI()
                .usingParams("properties=createdAt,createdBy,id,size").usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        itemModels.getOneRandomEntry().onModel().assertThat()
                .fieldsCount().is(4).and()
                .field("createdAt").isNotNull().and()
                .field("size").isNotNull().and()
                .field("createdBy").isNotNull().and()
                .field("modifiedAt").isNull().and()
                .field("name").isNull().and()
                .field("modifiedBy").isNull().and()
                .field("id").isNotNull().and()
                .field("mimeType").isNull();
    }

    @Test(groups = {TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Verify that user who started the process can get task items with an invalid property")
    public void getTaskItemsWithInvalidProperties() throws Exception
    {
        restClient.authenticateUser(userWhoStartsTask);
        taskModel = dataWorkflow.usingUser(userWhoStartsTask).usingSite(siteModel).usingResource(fileModel).createNewTaskAndAssignTo(assignee);
        taskItem = restClient.withWorkflowAPI().usingTask(taskModel).getTaskItems().getOneRandomEntry();
        itemModels = restClient.authenticateUser(userWhoStartsTask).withWorkflowAPI()
                .usingParams("properties=size,fake-prop").usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.OK);
        itemModels.getOneRandomEntry().onModel().assertThat()
                .fieldsCount().is(1).and()
                .field("createdAt").isNull().and()
                .field("size").isNotNull().and()
                .field("createdBy").isNull().and()
                .field("modifiedAt").isNull().and()
                .field("name").isNull().and()
                .field("modifiedBy").isNull().and()
                .field("id").isNull().and()
                .field("mimeType").isNull();
    }

    @Test(groups = {TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Verify that involved user in process that was deleted cannot get task items")
    public void getTaskItemsByDeletedUserInvolvedInProcess() throws Exception
    {
        UserModel assigneeDeleted = dataUser.createRandomTestUser();
        restClient.authenticateUser(userWhoStartsTask);
        taskModel = dataWorkflow.usingUser(userWhoStartsTask).usingSite(siteModel).usingResource(fileModel).createNewTaskAndAssignTo(assigneeDeleted);

        dataUser.usingAdmin().deleteUser(assigneeDeleted);

        itemModels = restClient.authenticateUser(assigneeDeleted).withWorkflowAPI().usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.UNAUTHORIZED)
                .assertLastError().containsErrorKey(RestErrorModel.API_DEFAULT_ERRORKEY)
                .containsSummary(RestErrorModel.AUTHENTICATION_FAILED)
                .stackTraceIs(RestErrorModel.STACKTRACE)
                .descriptionURLIs(RestErrorModel.RESTAPIEXPLORER);
    }

    @Test(groups = {TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS, TestGroup.REGRESSION })
    @TestRail(section = { TestGroup.REST_API, TestGroup.WORKFLOW, TestGroup.TASKS }, executionType = ExecutionType.REGRESSION,
            description = "Verify that involved user in process that was disabled cannot get task items")
    public void getTaskItemsByDisabledUserInvolvedInProcess() throws Exception
    {
        UserModel assigneeDisabled = dataUser.createRandomTestUser();
        restClient.authenticateUser(userWhoStartsTask);
        taskModel = dataWorkflow.usingUser(userWhoStartsTask).usingSite(siteModel).usingResource(fileModel).createNewTaskAndAssignTo(assigneeDisabled);

        dataUser.usingAdmin().disableUser(assigneeDisabled);

        itemModels = restClient.authenticateUser(assigneeDisabled).withWorkflowAPI().usingTask(taskModel).getTaskItems();
        restClient.assertStatusCodeIs(HttpStatus.UNAUTHORIZED)
                .assertLastError().containsErrorKey(RestErrorModel.API_DEFAULT_ERRORKEY)
                .containsSummary(RestErrorModel.AUTHENTICATION_FAILED)
                .stackTraceIs(RestErrorModel.STACKTRACE)
                .descriptionURLIs(RestErrorModel.RESTAPIEXPLORER);
    }
}
