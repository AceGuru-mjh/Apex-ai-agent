// IMultiAgentBridge.aidl
package com.apex.apk.multiagent;


/**
 * Multi-Agent APK 对外接口。
 */
interface IMultiAgentBridge {

    /// 注册一个 Agent
    boolean registerAgent(in String agentSpec);

    /// 启动一次协作会话
    void runCollaborationAsync(in String config, String callback);

    /// 列出所有注册的 Agent
    List<String> listAgents();

    /// 读取黑板数据
    String readBlackboard(String key);

    /// 心跳
    long heartbeat();
}
