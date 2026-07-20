package com.apex.di

import android.content.Context
import com.apex.core.kernel.ApexKernel
import com.apex.engine.chat.ChatEngine
import com.apex.engine.chat.LLMProvider
import com.apex.engine.chat.OpenAICompatProvider
import com.apex.engine.tools.ToolExecutor
import com.apex.engine.tools.ToolRegistry
import com.apex.engine.tools.builtin.BuiltInTools
import com.apex.selfmodify.SelfModifyService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module — 提供 Chat / Tool 栈单例。
 *
 * 与 ApexApplication 中 ServiceLocator 注册的实例是相互独立的——
 * 当前阶段为"双 DI 共存"迁移期：
 *   - 旧代码（serviceLocator.resolve<ChatEngine>()）拿到的是 ApexApplication.onCreate
 *     中 new 出来的实例
 *   - 新代码（@Inject ChatEngine）拿到的是 Hilt 容器 provide 的实例
 * 后续 Task E+ 会把所有调用方迁到 Hilt，再删除 ServiceLocator 注册。
 *
 * 注意：ChatEngine 构造时引用 ApexKernel.eventBus（lateinit）。
 * Hilt 单例是懒创建的——首次注入发生在 MainActivity 的 ViewModel 构造时，
 * 远晚于 Application.onCreate 完成的 ApexKernel.boot()，因此 lateinit 已就绪。
 *
 * ChatEngine 同时被注入 ToolExecutor，使其能把注册表中的工具元信息透传给 LLM
 * Provider（OpenAI function-calling 协议）。ToolExecutor 与 ChatEngine 共享同一
 * 个 ToolRegistry 单例，确保 Hilt 端注入的工具执行器拿到的注册表与 Hilt 端
 * 透传给 LLM 的工具列表一致。
 *
 * ToolRegistry 在 Hilt 端也填充 BuiltInTools，与 ServiceLocator 端语义一致，
 * 避免 Hilt 注入的 ToolExecutor 拿到空注册表。
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideToolRegistry(
        @ApplicationContext ctx: Context,
        selfModify: SelfModifyService
    ): ToolRegistry {
        val registry = ToolRegistry()
        BuiltInTools.createAll(ctx, selfModify).forEach { registry.register(it) }
        return registry
    }

    @Provides
    @Singleton
    fun provideToolExecutor(registry: ToolRegistry): ToolExecutor = ToolExecutor(registry)

    @Provides
    @Singleton
    fun provideLlmProvider(): LLMProvider = OpenAICompatProvider()

    @Provides
    @Singleton
    fun provideChatEngine(
        provider: LLMProvider,
        toolExecutor: ToolExecutor
    ): ChatEngine = ChatEngine(provider, ApexKernel.eventBus, toolExecutor)
}
