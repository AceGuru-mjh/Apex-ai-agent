// IWorkflowBridge.aidl
package com.apex.apk.workflow;


/**
 * Workflow APK 对外接口。
 */
interface IWorkflowBridge {

    /// 注册一个工作流定义
    boolean registerWorkflow(in String workflowDef);

    /// 异步执行工作流
    void executeAsync(String workflowId, in String inputs, String callback);

    /// 列出所有已注册的工作流
    List<String> listWorkflows();

    /// 心跳
    long heartbeat();
}
