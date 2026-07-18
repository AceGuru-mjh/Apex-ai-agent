package com.apex.di

import android.content.Context
import com.apex.agent.database.AppDatabase
import com.apex.agent.database.DatabaseRepository
import com.apex.agent.database.dao.PermissionDao
import com.apex.agent.database.dao.RoleDao
import com.apex.agent.database.dao.RolePermissionDao
import com.apex.agent.database.dao.TaskDao
import com.apex.agent.database.dao.UserDao
import com.apex.agent.database.dao.UserRoleDao
import com.apex.agent.database.dao.UserWithTasksDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module — 提供 Room [AppDatabase] / 各 DAO / [DatabaseRepository] 单例。
 *
 * AppDatabase 通过单例持有，DAO 由 AppDatabase 派生（每个 DAO 调用都是 O(1) 查表），
 * DatabaseRepository 是无状态包装器，标记为 @Singleton 以避免重复构造。
 *
 * 注意：AppDatabase.getDatabase() 内部已用 DCL 单例化 INSTANCE，
 * 这里 @Singleton 仅是 Hilt 容器层的不重复 provide，二者协同安全。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        AppDatabase.getDatabase(ctx)

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideUserWithTasksDao(db: AppDatabase): UserWithTasksDao = db.userWithTasksDao()

    @Provides
    fun providePermissionDao(db: AppDatabase): PermissionDao = db.permissionDao()

    @Provides
    fun provideRoleDao(db: AppDatabase): RoleDao = db.roleDao()

    @Provides
    fun provideUserRoleDao(db: AppDatabase): UserRoleDao = db.userRoleDao()

    @Provides
    fun provideRolePermissionDao(db: AppDatabase): RolePermissionDao = db.rolePermissionDao()

    @Provides
    @Singleton
    fun provideDatabaseRepository(
        userDao: UserDao,
        taskDao: TaskDao,
        userWithTasksDao: UserWithTasksDao,
        permissionDao: PermissionDao,
        roleDao: RoleDao,
        userRoleDao: UserRoleDao,
        rolePermissionDao: RolePermissionDao
    ): DatabaseRepository = DatabaseRepository(
        userDao = userDao,
        taskDao = taskDao,
        userWithTasksDao = userWithTasksDao,
        permissionDao = permissionDao,
        roleDao = roleDao,
        userRoleDao = userRoleDao,
        rolePermissionDao = rolePermissionDao
    )
}
