package com.jason.test;

import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormData;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.activiti.engine.impl.form.DateFormType;
import org.activiti.engine.impl.form.LongFormType;
import org.activiti.engine.impl.form.StringFormType;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * @author jason.xu
 * @date 2018/3/1 下午12:59
 */
public class OnBoardingRequest {
//    static Logger LOGGER = LoggerFactory.getLogger(OnBoardingRequest.class);

    public static void main(String[] args) throws ParseException {
        ProcessEngine processEngine = initProcessEngine();
        ProcessDefinition processDefinition = initRepositoryService(processEngine);

        RuntimeService runtimeService = processEngine.getRuntimeService();
        ProcessInstance processInstance = initProcessInstance(runtimeService, "onboarding");

        TaskService taskService = processEngine.getTaskService();
        FormService formService = processEngine.getFormService();
        HistoryService historyService = processEngine.getHistoryService();

        Scanner scanner = new Scanner(System.in);

        while (processInstance != null && !processInstance.isEnded()) {
            List<Task> tasks = taskService.createTaskQuery().taskCandidateGroup("managers").list();
            System.out.println(String.format("Active outstanding task:[%s]", tasks.size()));
            for (Task task : tasks) {
                System.out.println(String.format("Processing Task [%s]", task.getName()));
                Map<String, Object> variables = new HashMap<String, Object>();
                FormData formData = formService.getTaskFormData(task.getId());
                for (FormProperty formProperty : formData.getFormProperties()) {
                    if (StringFormType.class.isInstance(formProperty.getType())) {
                        System.out.println(formProperty.getName() + "?");
                        String value = scanner.nextLine();
                        variables.put(formProperty.getId(), value);
                    } else if (LongFormType.class.isInstance(formProperty.getType())) {
                        System.out.println(String.format("%s Must be a whole number", formProperty.getName()));
                        Long value = Long.valueOf(scanner.nextLine());
                        variables.put(formProperty.getId(), value);
                    } else if (DateFormType.class.isInstance(formProperty.getType())) {
                        System.out.println(String.format("%s Must be a data m/d/yy", formProperty.getName()));
                        DateFormat dateFormat = new SimpleDateFormat("m/d/yy");
                        Date value = dateFormat.parse(scanner.nextLine());
                        variables.put(formProperty.getId(), value);
                    } else {
                        System.out.println(String.format("<form type:%s not supported>", formProperty.getType()));
                    }
                }
                taskService.complete(task.getId(), variables);

                HistoricActivityInstance endActivity = null;
                List<HistoricActivityInstance> activityInstances =
                        historyService.createHistoricActivityInstanceQuery()
                                .processInstanceId(processInstance.getId()).finished()
                                .orderByHistoricActivityInstanceEndTime().asc().list();
                for (HistoricActivityInstance activityInstance : activityInstances) {
                    if (activityInstance.getActivityType().equals("startEvent ")) {
                        System.out.println(String.format("BEGIN [%s] %s ms", processDefinition.getName(), activityInstance.getDurationInMillis()));
                    }
                    if (activityInstance.getActivityType().equals("endEvent")) {
                        endActivity = activityInstance;
                    } else {
                        System.out.println(String.format("-- %s [%s] %s ms", activityInstance.getActivityName(), activityInstance.getActivityId(), activityInstance.getDurationInMillis()));
                    }
                }
                if (endActivity != null) {
                    System.out.println(String.format("-- %s [%s] %s ms", endActivity.getActivityName(), endActivity.getActivityId(), endActivity.getDurationInMillis()));
                    System.out.println(String.format("COMPLETE %s [%s] %s", processDefinition.getName(), processInstance.getProcessDefinitionKey(), endActivity.getEndTime()));
                }
                processInstance = runtimeService.createProcessInstanceQuery()
                        .processInstanceId(processInstance.getId()).singleResult();
            }
        }
        scanner.close();
    }

    private static ProcessInstance initProcessInstance(RuntimeService runtimeService, String keyName) {
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(keyName);
        System.out.println(String.format(
                "OnBoarding process started with process instance id [%s] key [%s]",
                processInstance.getProcessInstanceId(),
                processInstance.getProcessDefinitionKey())
        );
        return processInstance;
    }

    private static ProcessEngine initProcessEngine() {
        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:activiti;DB_CLOSE_DELAY=1000")
                .setJdbcUsername("sa")
                .setJdbcPassword("")
                .setJdbcDriver("org.h2.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        ProcessEngine processEngine = cfg.buildProcessEngine();
        String pName = processEngine.getName();
        String ver = ProcessEngine.VERSION;
        System.out.println(String.format("ProcessEngine [%s] Version: [%s]", pName, ver));
        return processEngine;
    }

    private static ProcessDefinition initRepositoryService(ProcessEngine processEngine) {
        RepositoryService repositoryService = processEngine.getRepositoryService();
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("onboarding.bpmn20.xml").deploy();
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId()).singleResult();
        System.out.println(String.format("Found process definition [%s] with id [%s] .", processDefinition.getName(), processDefinition.getId()));

        return processDefinition;
    }
}
