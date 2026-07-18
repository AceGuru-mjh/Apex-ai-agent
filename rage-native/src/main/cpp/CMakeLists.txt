cmake_minimum_required(VERSION 3.22.1)
project("rage_native")

add_library(rage_native SHARED
    jni/rage_jni.cpp
    core/TaskStateMachine.cpp
    core/ParallelScheduler.cpp
    core/Blackboard.cpp
    core/MetricsCollector.cpp
    agent/AgentOrchestrator.cpp
    agent/PlannerAgent.cpp
    agent/SearcherAgent.cpp
    agent/ExecutorAgent.cpp
    agent/CriticAgent.cpp
    skill/SkillGraph.cpp
    skill/SkillMatcher.cpp
    cache/AggressiveCache.cpp
    cache/Prefetcher.cpp
    util/JsonSerializer.cpp
)

target_include_directories(rage_native PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
target_link_libraries(rage_native ${log-lib} android)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
target_compile_options(rage_native PRIVATE -Wall -Wextra -Wno-unused-parameter -O2)
