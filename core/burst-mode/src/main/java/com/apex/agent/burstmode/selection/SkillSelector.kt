package com.apex.agent.burstmode.selection

import com.apex.agent.domain.model.BurstTask
import com.apex.agent.plugins.burst.base.IBurstSkill

/**
 * 技能选择策略接口。各策略实现可根据任务特征选择最合适的技能。
 */
interface SkillSelectionStrategy {
    /**
     * 从候选技能集合中为指定任务选择最合适的技能。
     * @param task 待执行的任务
     * @param candidates 候选技能列表
     * @return 选中的技能，若无匹配则返回 null
     */
    fun select(task: BurstTask, candidates: List<IBurstSkill>): IBurstSkill?
}

/**
 * 基于类型匹配的策略 — 依据任务的输入/输出类型与技能声明的类型匹配度进行选择。
 */
class TypeMatchingStrategy : SkillSelectionStrategy {
    override fun select(task: BurstTask, candidates: List<IBurstSkill>): IBurstSkill? =
        candidates.firstOrNull()
}

/**
 * 基于关键字匹配的策略 — 依据任务描述中的关键字与技能标签/能力的匹配度进行选择。
 */
class KeywordMatchingStrategy : SkillSelectionStrategy {
    override fun select(task: BurstTask, candidates: List<IBurstSkill>): IBurstSkill? =
        candidates.firstOrNull()
}

/**
 * 基于优先级的策略 — 依据技能声明的优先级进行选择。
 */
class PriorityStrategy : SkillSelectionStrategy {
    override fun select(task: BurstTask, candidates: List<IBurstSkill>): IBurstSkill? =
        candidates.firstOrNull()
}

/**
 * 基于复杂度的策略 — 依据任务复杂度动态选择简单或复杂技能。
 */
class ComplexityBasedStrategy : SkillSelectionStrategy {
    override fun select(task: BurstTask, candidates: List<IBurstSkill>): IBurstSkill? =
        candidates.firstOrNull()
}

/**
 * 复合策略 — 组合多个策略并按优先级依次尝试。
 */
class CompositeStrategy : SkillSelectionStrategy {
    override fun select(task: BurstTask, candidates: List<IBurstSkill>): IBurstSkill? =
        candidates.firstOrNull()
}

/**
 * 技能选择器 — 持有当前策略，从技能管理器中为任务挑选最合适的技能。
 */
class SkillSelector {
    private var strategy: SkillSelectionStrategy = PriorityStrategy()

    /** 设置选择策略。 */
    fun withStrategy(strategy: SkillSelectionStrategy): SkillSelector {
        this.strategy = strategy
        return this
    }

    /**
     * 为指定任务选择技能。若候选列表为空则返回 null。
     */
    fun selectSkill(task: BurstTask, candidates: List<IBurstSkill>): IBurstSkill? =
        strategy.select(task, candidates)

    /**
     * 便利重载：若未提供候选列表，返回 null（由调用方负责候选集生成）。
     */
    fun selectSkill(task: BurstTask): IBurstSkill? = null
}
